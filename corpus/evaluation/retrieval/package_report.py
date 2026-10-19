"""Build a pinned host replay of Android retrieval for a library package and case set."""

import argparse
import json
import os
from pathlib import Path
import tempfile

from blobs_corpus.package import verify_package
from .host_replay import (
    LIBRARY_STORE_SHA256,
    QUERY_SHA256,
    direct_title_ids,
    retrieve,
    retrieval_spec,
    sha,
    unpack,
)


def _case_definitions(path: Path):
    value = json.loads(path.read_text(encoding="utf-8"))
    cases = value.get("cases")
    if isinstance(cases, dict):
        cases = cases.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("Case file must contain a nonempty cases list")
    ids = set()
    for case in cases:
        if (
            not isinstance(case, dict)
            or not isinstance(case.get("id"), str)
            or not case["id"].strip()
            or case["id"] in ids
            or not isinstance(case.get("question"), str)
            or not case["question"].strip()
        ):
            raise ValueError("Cases must have unique IDs and nonempty questions")
        ids.add(case["id"])
        if case.get("previous_question"):
            raise ValueError("Host answer replay does not synthesize conversation history")
    return value, cases


def build_report(package_path: Path, cases_path: Path, work_directory: Path):
    """Create answer-run input using the frozen host mirror of Android retrieval."""
    root = Path(__file__).resolve().parents[2]
    android = root.parent / "android/app/src/main/kotlin/dev/blobs/app/data/library"
    if (
        sha(android / "LibraryStore.kt") != LIBRARY_STORE_SHA256
        or sha(android / "RetrievalQuery.kt") != QUERY_SHA256
    ):
        raise ValueError("Android retrieval changed; review the frozen host replay first")

    manifest = verify_package(package_path)
    if manifest["kind"] != "library":
        raise ValueError("Expected a verified library package")
    case_set, cases = _case_definitions(cases_path)
    work_directory.mkdir(parents=True, exist_ok=True)
    report = dict(
        format=1,
        complete=False,
        scope=(
            "Host replay of the frozen Android retrieval policy on this exact library package. "
            "It is not native-device latency or proof of relevance or answer correctness."
        ),
        script_sha256=sha(Path(__file__)),
        native_sources=dict(library_store=LIBRARY_STORE_SHA256, query=QUERY_SHA256),
        package=dict(file=package_path.name, package_sha256=sha(package_path), manifest=manifest),
        cases=case_set,
        results=[],
    )
    with tempfile.TemporaryDirectory(
        prefix="blobs-package-retrieval-", dir=work_directory
    ) as temporary:
        database = Path(temporary) / "library.sqlite"
        loaded = unpack(package_path, database)
        if loaded != manifest:
            raise ValueError("Package manifest changed during retrieval replay")
        for case in cases:
            query, titles = retrieval_spec(database, case["question"])
            route_ids = direct_title_ids(database, case["question"], titles)
            priority_ids = direct_title_ids(database, case["question"], titles, broad_only=True)
            evidence, _ = retrieve(
                database,
                manifest,
                query,
                question=case["question"],
                priority_ids=route_ids,
                broad_priority_ids=priority_ids,
            )
            report["results"].append(
                dict(
                    case_id=case["id"],
                    question=case["question"],
                    variant="candidateLibrary",
                    resolved_query=case["question"],
                    fts_query=query,
                    matched_titles=titles,
                    evidence=evidence,
                )
            )
    report["complete"] = True
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--library-package", type=Path, required=True)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--work-directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = build_report(args.library_package, args.cases, args.work_directory)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + ".partial")
    if temporary.exists():
        raise FileExistsError(temporary)
    try:
        temporary.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        os.replace(temporary, args.output)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise
    print(
        json.dumps(
            dict(
                complete=report["complete"],
                cases=len(report["results"]),
                package_sha256=report["package"]["package_sha256"],
                output=str(args.output),
            ),
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
