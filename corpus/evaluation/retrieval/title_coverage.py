"""Inspect source-title availability without running retrieval or judging answerability."""

import argparse
from collections import Counter
from contextlib import closing
import json
from pathlib import Path
import sqlite3

from blobs_corpus.extract import write_json
from blobs_corpus.names import name_key
from blobs_corpus.records import documents
from .host_replay import sha


def load_cases(questions):
    question_hash = sha(questions)
    cases = json.loads(questions.read_text(encoding="utf-8"))["cases"]
    if not isinstance(cases, list) or not cases:
        raise ValueError("Require a nonempty question set")
    seen = set()
    for case in cases:
        if not isinstance(case, dict):
            raise ValueError("Each case must be an object")
        if not isinstance(case.get("id"), str) or not case["id"].strip() or case["id"] in seen:
            raise ValueError("Require unique nonempty case IDs")
        seen.add(case["id"])
        if not isinstance(case.get("category"), str) or not case["category"].strip():
            raise ValueError("Require a category for each case")
        targets = case.get("expected_titles")
        if (
            not isinstance(targets, list)
            or not targets
            or any(not isinstance(title, str) or not title.strip() for title in targets)
            or len(set(targets)) != len(targets)
        ):
            raise ValueError("Require distinct nonempty title targets for each case")
    return cases, question_hash


def summarize(cases, question_hash, present_by_case, **source):
    rows = [
        dict(
            case_id=case["id"],
            category=case["category"],
            present=present_by_case[case["id"]],
            absent=[
                title
                for title in case["expected_titles"]
                if title not in present_by_case[case["id"]]
            ],
        )
        for case in cases
    ]
    targets = {title for case in cases for title in case["expected_titles"]}
    return dict(
        format=1,
        complete=True,
        scope="Exact normalized source-title and redirect-alias availability only, not passage relevance, answerability or retrieval recall. An absent title may be covered by another article. A present title may lack the required evidence. No model answers executed.",
        questions_sha256=question_hash,
        cases=len(cases),
        categories=dict(Counter(case["category"] for case in cases)),
        expected_unique_titles=len(targets),
        present_unique_titles=len({title for row in rows for title in row["present"]}),
        absent_unique_titles=sorted({title for row in rows for title in row["absent"]}),
        cases_with_all_target_titles=sum(not row["absent"] for row in rows),
        cases_with_some_target_titles=sum(
            bool(row["present"]) and bool(row["absent"]) for row in rows
        ),
        cases_with_no_target_titles=sum(not row["present"] for row in rows),
        results=rows,
        **source,
    )


def inspect(records, questions):
    record_hash = sha(records)
    cases, question_hash = load_cases(questions)
    titles, count = set(), 0
    for document in documents(records):
        titles.add(document["title"])
        count += 1
    if sha(records) != record_hash or sha(questions) != question_hash:
        raise ValueError("Inputs changed while inspecting title availability")
    keys = {
        case["id"]: [title for title in case["expected_titles"] if title in titles]
        for case in cases
    }
    return summarize(
        cases,
        question_hash,
        keys,
        source_kind="records",
        records_sha256=record_hash,
        script_sha256=sha(Path(__file__)),
        documents=count,
        unique_titles=len(titles),
    )


def inspect_database(database, questions):
    """Check the packaged library itself, including normalized title aliases."""
    database_hash = sha(database)
    cases, question_hash = load_cases(questions)
    wanted = {name_key(title) for case in cases for title in case["expected_titles"]}
    present_keys = set()
    with closing(sqlite3.connect(database.resolve().as_uri() + "?mode=ro", uri=True)) as db:
        db.execute("PRAGMA trusted_schema=OFF")
        db.execute("PRAGMA query_only=ON")
        version = db.execute("PRAGMA user_version").fetchone()[0]
        if version not in (1, 2, 3):
            raise ValueError(f"Unsupported library schema: {version}")
        table = db.execute(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='document_names'"
        ).fetchone()
        count = db.execute("SELECT count(*) FROM documents").fetchone()[0]
        if table:
            for key in wanted:
                if db.execute(
                    "SELECT 1 FROM document_names WHERE name=? LIMIT 1", (key,)
                ).fetchone():
                    present_keys.add(key)
        else:
            for (title,) in db.execute("SELECT title FROM documents"):
                key = name_key(title)
                if key in wanted:
                    present_keys.add(key)
        unique_titles = db.execute("SELECT count(DISTINCT title) FROM documents").fetchone()[0]
    if sha(database) != database_hash or sha(questions) != question_hash:
        raise ValueError("Inputs changed while inspecting package title availability")
    present_by_case = {
        case["id"]: [title for title in case["expected_titles"] if name_key(title) in present_keys]
        for case in cases
    }
    return summarize(
        cases,
        question_hash,
        present_by_case,
        source_kind="sqlite-package-payload",
        database_sha256=database_hash,
        script_sha256=sha(Path(__file__)),
        schema=version,
        documents=count,
        unique_titles=unique_titles,
        availability="document title or normalized redirect alias",
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--records", type=Path)
    source.add_argument(
        "--database",
        type=Path,
        help="unpacked SQLite library payload; checks document titles and redirect aliases",
    )
    parser.add_argument("--questions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = (
        inspect(args.records, args.questions)
        if args.records
        else inspect_database(args.database, args.questions)
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(
        f"{report['cases']} questions; {report['present_unique_titles']}/{report['expected_unique_titles']} target titles present. Answerability not evaluated."
    )


if __name__ == "__main__":
    main()
