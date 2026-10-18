"""Measure fixed host-side retrieval smoke cases without storing passage text."""
import argparse
from contextlib import closing
import json
import math
import os
from pathlib import Path
import platform
import sqlite3
import statistics
import tempfile
import time

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


def build_report(package_path: Path, cases_path: Path, work_directory: Path) -> dict:
    root = Path(__file__).resolve().parents[2]
    android = root.parent / 'android/app/src/main/kotlin/dev/blobs/app/data/library'
    if sha(android / 'LibraryStore.kt') != LIBRARY_STORE_SHA256 or sha(android / 'RetrievalQuery.kt') != QUERY_SHA256:
        raise ValueError('Android retrieval changed; review the host replay before using it')

    case_set = json.loads(cases_path.read_text(encoding='utf-8'))
    cases = case_set.get('cases')
    if case_set.get('format') != 1 or not isinstance(cases, list) or not 1 <= len(cases) <= 100:
        raise ValueError('Expected 1..100 versioned retrieval cases')
    ids = set()
    for case in cases:
        if (
            not isinstance(case, dict)
            or not isinstance(case.get('id'), str)
            or not case['id'].strip()
            or case['id'] in ids
            or not isinstance(case.get('question'), str)
            or not case['question'].strip()
            or not isinstance(case.get('expected_document_titles'), list)
            or not case['expected_document_titles']
            or any(not isinstance(title, str) or not title.strip()
                   for title in case['expected_document_titles'])
        ):
            raise ValueError('Each case needs a unique ID, question and expected document titles')
        ids.add(case['id'])

    manifest = verify_package(package_path)
    expected = case_set.get('library')
    if expected and any(expected.get(key) != manifest.get(manifest_key) for key, manifest_key in (
        ('id', 'id'), ('version', 'version'), ('payload_sha256', 'sha256')
    )):
        raise ValueError('Package identity differs from the pinned smoke case set')
    if manifest['kind'] != 'library':
        raise ValueError('Expected a library package')

    results = []
    work_directory.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='travel-retrieval-smoke-', dir=work_directory) as temporary:
        database = Path(temporary) / 'library.sqlite'
        unpack(package_path, database)
        with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as db:
            db.execute('PRAGMA query_only=ON')
            integrity = db.execute('PRAGMA integrity_check').fetchone()[0]
        if integrity != 'ok':
            raise ValueError('Library database failed SQLite integrity_check')

        for case in cases:
            started = time.perf_counter_ns()
            query, titles = retrieval_spec(database, case['question'])
            route_ids = direct_title_ids(database, case['question'], titles)
            priority_ids = direct_title_ids(
                database, case['question'], titles, broad_only=True
            )
            evidence, _ = retrieve(
                database, manifest, query, question=case['question'], priority_ids=route_ids,
                broad_priority_ids=priority_ids,
            )
            elapsed_ms = (time.perf_counter_ns() - started) / 1_000_000
            selected = list(dict.fromkeys(row['title'] for row in evidence))
            expected_titles = case['expected_document_titles']
            ranks = [selected.index(title) + 1 if title in selected else None
                     for title in expected_titles]
            results.append(dict(
                case_id=case['id'],
                elapsed_host_ms=elapsed_ms,
                exact_title_priority_count=len(priority_ids),
                selected_document_titles=selected,
                expected_document_titles=expected_titles,
                expected_titles_found=[title for title in expected_titles if title in selected],
                expected_title_ranks=ranks,
                first_expected_title_rank=min((rank for rank in ranks if rank is not None), default=None),
            ))

    elapsed = [row['elapsed_host_ms'] for row in results]
    expected_count = sum(len(row['expected_document_titles']) for row in results)
    found_count = sum(len(row['expected_titles_found']) for row in results)
    ranks = [rank for row in results for rank in row['expected_title_ranks']]
    return dict(
        format=1,
        complete=True,
        scope=('Fixed document-retrieval smoke cases on the exact packaged library and host runtime. '
               'Measures host retrieval only; it does not score generated answers or predict Android latency.'),
        case_set=dict(id=case_set['id'], sha256=sha(cases_path)),
        package=dict(file=package_path.name, sha256=sha(package_path), manifest=manifest),
        runtime=dict(platform=platform.platform(), processor=platform.processor(),
                     python=platform.python_version(), sqlite=sqlite3.sqlite_version),
        retrieval_sources=dict(library_store=LIBRARY_STORE_SHA256, query=QUERY_SHA256),
        host_runtime=dict(
            retrieval_smoke_sha256=sha(Path(__file__)),
            host_replay_sha256=sha(Path(__file__).with_name('host_replay.py')),
        ),
        summary=dict(
            case_count=len(results),
            expected_titles=expected_count,
            expected_titles_found=found_count,
            expected_title_recall=found_count / expected_count,
            expected_titles_at_rank_1=sum(rank == 1 for rank in ranks),
            expected_title_mean_reciprocal_rank=(
                sum(1 / rank for rank in ranks if rank is not None) / expected_count
            ),
            host_elapsed_ms_median=statistics.median(elapsed),
            host_elapsed_ms_p95=sorted(elapsed)[math.ceil(len(elapsed) * 0.95) - 1],
        ),
        results=results,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--library-package', type=Path, required=True)
    parser.add_argument('--cases', type=Path, required=True)
    parser.add_argument('--work-directory', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = build_report(args.library_package, args.cases, args.work_directory)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + '.partial')
    if temporary.exists():
        raise FileExistsError(temporary)
    try:
        temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        os.replace(temporary, args.output)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise
    print(json.dumps(dict(complete=report['complete'], summary=report['summary'], output=str(args.output)), indent=2))


if __name__ == '__main__':
    main()
