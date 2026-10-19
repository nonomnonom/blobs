"""Build the small fictional library used by native answer-contract tests."""

import argparse
import json
from pathlib import Path
import re

from blobs_corpus.extract import write_lines
from blobs_corpus.library import build_library
from blobs_corpus.package import package, verify_package


ROOT = Path(__file__).resolve().parents[2]
CASES = ROOT / "evaluation/cases/answer-contract-development-v2.json"


def build(output: Path) -> Path:
    if output.exists():
        raise FileExistsError(f"Evaluation fixture output already exists: {output}")
    definition = json.loads(CASES.read_text(encoding="utf-8"))
    cases = {case["id"] for case in definition["cases"]["cases"]}
    results = definition["results"]
    if len(cases) != len(definition["cases"]["cases"]):
        raise ValueError("Answer-contract case IDs must be unique")
    if {row["case_id"] for row in results} != cases:
        raise ValueError("Every answer-contract case needs exactly one evidence definition")

    rows = []
    for result in results:
        for source in result["evidence"]:
            rows.append(
                {
                    "id": re.sub(r"[^a-zA-Z0-9_-]", "_", source["id"]),
                    "title": source["title"],
                    "publisher": "Blobs evaluation fixture",
                    "url": source["url"],
                    "license": source["license"],
                    "updated": source["updated"],
                    "saved": source["saved"],
                    "body": source["text"],
                    "collection": f"Synthetic answer-contract case {result['case_id']}",
                }
            )
    if not rows:
        raise ValueError("At least one synthetic evidence record is required")

    output.mkdir(parents=True)
    records = output / "records.jsonl.gz"
    database = output / "library.sqlite"
    archive = output / "answer-contract-fixture.blobs"
    write_lines(records, rows)
    build_library(records, database)
    package(
        database,
        archive,
        kind="library",
        id="answer-contract-fixture",
        version="2",
        title="Synthetic answer-contract fixtures",
        license="Original fictional evaluation text; not for redistribution",
        origin="local:test/answer-contract-development-v2",
    )
    manifest = verify_package(archive)
    if manifest["kind"] != "library" or manifest["bytes"] < database.stat().st_size:
        raise ValueError("Generated evaluation library did not verify")
    return archive


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    archive = build(args.output_dir)
    print(json.dumps({"archive": str(archive), "bytes": archive.stat().st_size}, indent=2))


if __name__ == "__main__":
    main()
