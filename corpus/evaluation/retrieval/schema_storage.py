"""Compare schema 2 and span-backed schema 3 without changing retrieval inputs."""

import argparse
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import tempfile

from blobs_corpus.extract import write_json
from blobs_corpus.package import package, verify_package


STRUCTURAL_FILTER = (
    " AND (p.title!='' OR lower(trim(p.section)) NOT IN "
    "('references','external links','see also','further reading','bibliography'))"
)
SCHEMA2_SQL = """SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,
p.section,p.body,p.title AS opening_title,p.id AS passage_id,rank AS score
FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
JOIN documents d ON d.id=p.doc_id WHERE passages_fts MATCH ?{filter}
ORDER BY rank,p.id LIMIT 48"""
SCHEMA3_SQL = """SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,
p.section,substr(d.body,s.start+1,s.end-s.start) AS body,p.title AS opening_title,
p.id AS passage_id,rank AS score
FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
JOIN passage_spans s ON s.passage_id=p.id JOIN documents d ON d.id=p.doc_id
WHERE passages_fts MATCH ?{filter} ORDER BY rank,p.id LIMIT 48"""
POLICIES = {
    "all_sections": "",
    "structural_sections_excluded": STRUCTURAL_FILTER,
}


def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def table_sizes(path: Path) -> dict[str, int]:
    with closing(sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)) as db:
        return dict(db.execute("SELECT name,sum(pgsize) FROM dbstat GROUP BY name ORDER BY name"))


def query(path: Path, sql: str, match: str) -> list[dict]:
    with closing(sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)) as db:
        db.execute("PRAGMA trusted_schema=OFF")
        db.execute("PRAGMA query_only=ON")
        db.row_factory = sqlite3.Row
        return [dict(row) for row in db.execute(sql, (match,))]


def matches_frozen(rows: list[dict], frozen: list[dict]) -> bool:
    if not frozen:
        return rows == frozen
    keys = tuple(frozen[0])
    return [{key: row[key] for key in keys} for row in rows] == frozen


def convert(source: Path, target: Path) -> dict:
    if target.exists():
        raise FileExistsError(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(target.parent).free < source.stat().st_size + 64 * 1024**2:
        raise OSError("Schema comparison needs the source size plus 64 MiB of free space")
    with closing(sqlite3.connect(source.resolve().as_uri() + "?mode=ro", uri=True)) as old:
        if old.execute("PRAGMA user_version").fetchone()[0] != 2:
            raise ValueError("Expected a schema 2 baseline")
        if old.execute("PRAGMA quick_check").fetchone()[0] != "ok":
            raise ValueError("Baseline SQLite quick check failed")
        source_mismatches = old.execute("""SELECT count(*) FROM passages p
            JOIN passage_spans s ON s.passage_id=p.id JOIN documents d ON d.id=p.doc_id
            WHERE p.body!=substr(d.body,s.start+1,s.end-s.start)""").fetchone()[0]
        if source_mismatches:
            raise ValueError("Baseline passage text differs from its source span")
        source_hash = sha(source)
        with tempfile.TemporaryDirectory(prefix=".schema3-", dir=target.parent) as folder:
            staged = Path(folder) / "library.sqlite"
            with closing(sqlite3.connect(staged)) as db:
                db.executescript("""
                PRAGMA user_version=3;
                CREATE TABLE documents(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,publisher TEXT NOT NULL,
                  url TEXT NOT NULL,license TEXT NOT NULL,updated TEXT NOT NULL,saved TEXT NOT NULL,
                  body TEXT NOT NULL,collection TEXT NOT NULL);
                CREATE INDEX documents_title ON documents(title,id);
                CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT NOT NULL,section TEXT NOT NULL,title TEXT NOT NULL);
                CREATE INDEX passages_document ON passages(doc_id);
                CREATE TABLE document_provenance(doc_id TEXT PRIMARY KEY NOT NULL,body_sha256 TEXT NOT NULL,details TEXT NOT NULL);
                CREATE INDEX provenance_body_hash ON document_provenance(body_sha256);
                CREATE TABLE passage_spans(passage_id INTEGER PRIMARY KEY,start INTEGER NOT NULL,end INTEGER NOT NULL,section_path TEXT NOT NULL);
                CREATE TABLE build_metadata(key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL);
                CREATE TABLE document_names(name TEXT NOT NULL,doc_id TEXT NOT NULL,
                  PRIMARY KEY(name,doc_id)) WITHOUT ROWID;
                CREATE VIRTUAL TABLE passages_fts USING fts5(body,title,section,content='',tokenize='unicode61');
                INSERT INTO passages_fts(passages_fts,rank) VALUES ('rank','bm25(1.0,6.0,2.0)');
                """)
                for table in (
                    "documents",
                    "document_provenance",
                    "passage_spans",
                    "document_names",
                ):
                    columns = [row[1] for row in old.execute(f"PRAGMA table_info({table})")]
                    placeholders = ",".join("?" for _ in columns)
                    db.executemany(
                        f"INSERT INTO {table} VALUES ({placeholders})",
                        old.execute(f"SELECT {','.join(columns)} FROM {table}"),
                    )
                for passage_id, doc_id, body, section, title in old.execute(
                    "SELECT id,doc_id,body,section,title FROM passages ORDER BY id"
                ):
                    db.execute(
                        "INSERT INTO passages VALUES (?,?,?,?)",
                        (passage_id, doc_id, section, title),
                    )
                    db.execute(
                        "INSERT INTO passages_fts(rowid,body,title,section) VALUES (?,?,?,?)",
                        (passage_id, body, title, section),
                    )
                build = {
                    "format": 1,
                    "schema": 3,
                    "scope": "Evaluation conversion preserving schema-2 rows and FTS inputs exactly",
                    "source_sha256": source_hash,
                    "passage_storage": "document spans + contentless FTS",
                }
                db.execute(
                    "INSERT INTO build_metadata VALUES (?,?)",
                    ("build", json.dumps(build, sort_keys=True)),
                )
                db.execute("INSERT INTO passages_fts(passages_fts) VALUES ('integrity-check')")
                db.commit()
                if db.execute("PRAGMA quick_check").fetchone()[0] != "ok":
                    raise ValueError("Candidate SQLite quick check failed")
                if (
                    db.execute("SELECT count(*) FROM passages").fetchone()[0]
                    != db.execute("SELECT count(*) FROM passage_spans").fetchone()[0]
                ):
                    raise ValueError("Passage/span count mismatch")
                if (
                    db.execute("SELECT count(*) FROM passages").fetchone()[0]
                    != db.execute("SELECT count(*) FROM passages_fts").fetchone()[0]
                ):
                    raise ValueError("Passage/FTS count mismatch")
                mismatch = db.execute("""SELECT count(*) FROM passages p
                    JOIN passage_spans s ON s.passage_id=p.id JOIN documents d ON d.id=p.doc_id
                    JOIN passages_fts ON passages_fts.rowid=p.id
                    WHERE length(substr(d.body,s.start+1,s.end-s.start)) NOT BETWEEN 1 AND 1400""").fetchone()[
                    0
                ]
                if mismatch:
                    raise ValueError("Invalid reconstructed passage")
                db.execute("PRAGMA journal_mode=DELETE")
            with staged.open("r+b") as completed:
                os.fsync(completed.fileno())
            os.link(staged, target)
    return build


def prepare_candidate(args, baseline_manifest: dict) -> tuple[dict, dict]:
    existing = args.candidate.exists(), args.candidate_package.exists()
    if any(existing) and not all(existing):
        raise ValueError("Candidate database and package must either both exist or both be absent")
    if all(existing):
        with closing(
            sqlite3.connect(args.candidate.resolve().as_uri() + "?mode=ro", uri=True)
        ) as db:
            if db.execute("PRAGMA user_version").fetchone()[0] != 3:
                raise ValueError("Existing candidate is not schema 3")
            row = db.execute("SELECT value FROM build_metadata WHERE key='build'").fetchone()
            if row is None:
                raise ValueError("Existing candidate has no build identity")
            build = json.loads(row[0])
        if build.get("source_sha256") != baseline_manifest["sha256"]:
            raise ValueError("Existing candidate came from another baseline")
        candidate_manifest = verify_package(args.candidate_package)
        if (
            candidate_manifest["sha256"] != sha(args.candidate)
            or candidate_manifest["bytes"] != args.candidate.stat().st_size
        ):
            raise ValueError("Existing candidate package and database differ")
        return build, candidate_manifest
    build = convert(args.baseline, args.candidate)
    candidate_manifest = package(
        args.candidate,
        args.candidate_package,
        kind="library",
        id=baseline_manifest["id"],
        version=baseline_manifest["version"] + ".schema3-eval",
        title=baseline_manifest["title"] + " (schema 3 evaluation)",
        license=baseline_manifest["license"],
        origin=baseline_manifest["origin"],
    )
    if verify_package(args.candidate_package) != candidate_manifest:
        raise ValueError("Candidate package verification failed")
    return build, candidate_manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--baseline-package", required=True, type=Path)
    parser.add_argument("--baseline-receipt", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--candidate-package", required=True, type=Path)
    parser.add_argument("--retrieval-report", required=True, action="append", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    receipt = json.loads(args.baseline_receipt.read_text(encoding="utf-8"))
    baseline_manifest = verify_package(args.baseline_package)
    if baseline_manifest != receipt["manifest"]:
        raise ValueError("Baseline package and receipt differ")
    if (
        args.baseline.stat().st_size != baseline_manifest["bytes"]
        or sha(args.baseline) != baseline_manifest["sha256"]
    ):
        raise ValueError("Baseline database and package identity differ")
    report = {
        "format": 1,
        "complete": False,
        "scope": "Exact schema-2/schema-3 storage and retrieval comparison on the retained 90-document fixture; not a full-corpus estimate or phone benchmark.",
        "tool_sha256": sha(Path(__file__)),
        "sqlite_version": sqlite3.sqlite_version,
        "baseline": {},
        "candidate": {},
        "retrieval_parity": [],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    try:
        build, candidate_manifest = prepare_candidate(args, baseline_manifest)
        baseline_tables = table_sizes(args.baseline)
        candidate_tables = table_sizes(args.candidate)
        report["baseline"] = {
            "database": {
                "file": str(args.baseline),
                "bytes": args.baseline.stat().st_size,
                "sha256": sha(args.baseline),
            },
            "package": {
                "file": str(args.baseline_package),
                "bytes": args.baseline_package.stat().st_size,
                "sha256": sha(args.baseline_package),
            },
            "table_bytes": baseline_tables,
        }
        report["candidate"] = {
            "database": {
                "file": str(args.candidate),
                "bytes": args.candidate.stat().st_size,
                "sha256": sha(args.candidate),
            },
            "package": {
                "file": str(args.candidate_package),
                "bytes": args.candidate_package.stat().st_size,
                "sha256": sha(args.candidate_package),
            },
            "table_bytes": candidate_tables,
            "build": build,
        }
        report["savings"] = {
            "database_bytes": args.baseline.stat().st_size - args.candidate.stat().st_size,
            "database_percent": (1 - args.candidate.stat().st_size / args.baseline.stat().st_size)
            * 100,
            "package_bytes": args.baseline_package.stat().st_size
            - args.candidate_package.stat().st_size,
            "package_percent": (
                1 - args.candidate_package.stat().st_size / args.baseline_package.stat().st_size
            )
            * 100,
        }
        seen = set()
        for path in args.retrieval_report:
            frozen = json.loads(path.read_text(encoding="utf-8"))
            if (
                not frozen.get("complete")
                or frozen.get("candidate", {}).get("sha256") != baseline_manifest["sha256"]
            ):
                raise ValueError(f"Retrieval report does not identify the baseline: {path}")
            matching_policies = []
            for policy, sql_filter in POLICIES.items():
                sql = SCHEMA2_SQL.format(filter=sql_filter)
                if all(
                    matches_frozen(
                        query(args.baseline, sql, result["fts_query"]), result["candidates"]
                    )
                    for result in frozen["results"]
                ):
                    matching_policies.append(policy)
            if len(matching_policies) != 1:
                raise ValueError(f"Frozen retrieval policy is ambiguous or unsupported: {path}")
            policy = matching_policies[0]
            for result in frozen["results"]:
                identity = (path.name, result["case_id"])
                if identity in seen:
                    raise ValueError(f"Duplicate retrieval case: {identity}")
                seen.add(identity)
                rows_by_policy = {}
                policy_parity = {}
                for policy_name, sql_filter in POLICIES.items():
                    baseline_policy_rows = query(
                        args.baseline, SCHEMA2_SQL.format(filter=sql_filter), result["fts_query"]
                    )
                    candidate_policy_rows = query(
                        args.candidate, SCHEMA3_SQL.format(filter=sql_filter), result["fts_query"]
                    )
                    rows_by_policy[policy_name] = (baseline_policy_rows, candidate_policy_rows)
                    policy_parity[policy_name] = baseline_policy_rows == candidate_policy_rows
                baseline_rows, candidate_rows = rows_by_policy[policy]
                exact = baseline_rows == candidate_rows and matches_frozen(
                    baseline_rows, result["candidates"]
                )
                report["retrieval_parity"].append(
                    {
                        "report": path.name,
                        "case_id": result["case_id"],
                        "fts_query": result["fts_query"],
                        "policy": policy,
                        "candidates": len(candidate_rows),
                        "exact": exact,
                        "schema_policy_parity": policy_parity,
                    }
                )
                if not exact or not all(policy_parity.values()):
                    raise ValueError(f"Retrieval candidates changed: {identity}")
        report["complete"] = True
    except Exception as error:
        report["error"] = str(error)
        raise
    finally:
        write_json(args.output, report)
    print(
        json.dumps(
            {
                "complete": report["complete"],
                "retrieval_cases": len(report["retrieval_parity"]),
                "savings": report["savings"],
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
