"""Exact article names and source redirects; no inferred synonym dictionary."""

from collections import Counter
import gzip
import json
from pathlib import Path
import unicodedata

from .records import valid_utf8_text


def name_key(text: str) -> str:
    text = unicodedata.normalize("NFKC", text).lower()
    return " ".join("".join(c if unicodedata.category(c)[0] in "LN" else " " for c in text).split())


def build_names(db, source: Path):
    from .extract import verified_shards

    db.executescript("""
        CREATE TABLE document_names(name TEXT NOT NULL,doc_id TEXT NOT NULL,
          PRIMARY KEY(name,doc_id)) WITHOUT ROWID;
        CREATE TEMP TABLE canonical_names(name TEXT NOT NULL,doc_id TEXT NOT NULL,
          PRIMARY KEY(name,doc_id)) WITHOUT ROWID;
        CREATE TEMP TABLE name_paths(path TEXT PRIMARY KEY,doc_id TEXT NOT NULL) WITHOUT ROWID;
        CREATE TEMP TABLE name_redirects(path TEXT PRIMARY KEY,target TEXT NOT NULL,name TEXT NOT NULL) WITHOUT ROWID;
    """)
    counts = Counter()
    for doc_id, title, details in db.execute(
        "SELECT d.id,d.title,p.details FROM documents d JOIN document_provenance p ON p.doc_id=d.id"
    ):
        key = name_key(title)
        if key and len(key) <= 300:
            db.execute("INSERT OR IGNORE INTO document_names VALUES (?,?)", (key, doc_id))
            db.execute("INSERT OR IGNORE INTO canonical_names VALUES (?,?)", (key, doc_id))
        path = json.loads(details).get("entry_path")
        if path:
            db.execute("INSERT INTO name_paths VALUES (?,?)", (path, doc_id))
    if source.name == "manifest.json":
        manifest = verified_shards(source)
        if not manifest["complete"]:
            raise ValueError("Cannot index incomplete redirects")
        for shard in manifest["shards"]:
            if "aliases" not in shard["files"]:
                continue
            path = source.parent / shard["files"]["aliases"]["name"]
            with gzip.open(path, "rt", encoding="utf-8") as rows:
                while line := rows.readline(65537):
                    if len(line) > 65536:
                        raise ValueError("Redirect record exceeds 65,536 characters")
                    row = json.loads(line)
                    if any(
                        not isinstance(row.get(field), str)
                        or not row[field]
                        or len(row[field]) > 4096
                        or "\x00" in row[field]
                        or "\ufffd" in row[field]
                        or not valid_utf8_text(row[field])
                        for field in ("path", "target", "title")
                    ):
                        raise ValueError("Invalid redirect record")
                    counts["redirects"] += 1
                    if counts["redirects"] > 5_000_000:
                        raise ValueError("Redirect count exceeds 5,000,000")
                    # HTML redirects are already percent-decoded by extraction.
                    # A section anchor cannot identify a different article.
                    target = row["target"].split("#", 1)[0].removeprefix("./")
                    key = name_key(row["title"])
                    db.execute(
                        "INSERT INTO name_redirects VALUES (?,?,?)",
                        (row["path"], target, key if len(key) <= 300 else ""),
                    )
        # Resolve on disk. Missing destinations and cycles never become names.
        # The bound also prevents pathological redirect chains delaying a build.
        for _ in range(32):
            added = db.execute("""INSERT OR IGNORE INTO name_paths
                SELECT a.path,r.doc_id FROM name_redirects a JOIN name_paths r ON r.path=a.target""").rowcount
            if added == 0:
                break
        counts["resolved_redirects"] = db.execute("""SELECT count(*) FROM name_redirects a
            JOIN name_paths p ON p.path=a.path""").fetchone()[0]
        counts["unresolved_redirects"] = counts["redirects"] - counts["resolved_redirects"]
        db.execute("""INSERT OR IGNORE INTO document_names
            SELECT a.name,p.doc_id FROM name_redirects a JOIN name_paths p ON p.path=a.path
            WHERE a.name!='' AND NOT EXISTS (
                SELECT 1 FROM canonical_names c WHERE c.name=a.name AND c.doc_id!=p.doc_id
            ) """)
    counts["name_document_pairs"] = db.execute("SELECT count(*) FROM document_names").fetchone()[0]
    counts["ambiguous_names"] = db.execute("""SELECT count(*) FROM
        (SELECT name FROM document_names GROUP BY name HAVING count(*)>1)""").fetchone()[0]
    db.execute("DROP TABLE name_redirects")
    db.execute("DROP TABLE name_paths")
    db.execute("DROP TABLE canonical_names")
    return dict(
        format=1,
        normalization="NFKC, lowercase, Unicode letters/numbers separated by spaces",
        maximum_resolution_passes=32,
        **counts,
    )
