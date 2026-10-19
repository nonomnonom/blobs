"""Diagnose source-name retrieval after checking complete evidence parity with Android."""
import argparse
from contextlib import closing
import json
from pathlib import Path
import re
import sqlite3
import time

from blobs_corpus.extract import write_json
from blobs_corpus.names import name_key
from .host_replay import direct_title_ids, retrieve, sha, unpack


LIBRARY_STORE_SHA256 = '3ba109923c22281a29282e693777845972a3d2445b0c0e44b097476f200390c5'
QUERY_SHA256 = 'acee75cef2a8b7845ed1817d3e81143bb4daf730661b83d7f7c864f81c4f8ab5'


def expanded_query(database, question, base_query, stop_words):
    words = name_key(question).split()[:40]
    phrases = [(' '.join(words[start:start + length]), range(start, start + length))
               for length in range(min(6, len(words)), 0, -1)
               for start in range(len(words) - length + 1)
               if any(word not in stop_words for word in words[start:start + length])]
    phrases = [(key, positions) for key, positions in phrases if 2 <= len(key.encode('utf-16-le')) // 2 <= 600]
    keys = list(dict.fromkeys(key for key, _ in phrases))
    if not keys:
        return base_query, []
    with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True)) as db:
        db.execute('PRAGMA trusted_schema=OFF')
        if not db.execute("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='document_names'").fetchone()[0]:
            return base_query, []
        matches = dict(db.execute('''SELECT n.name,min(d.title)
            FROM document_names n JOIN documents d ON d.id=n.doc_id
            WHERE n.name IN (''' + ','.join('?' for _ in keys) + ''')
            GROUP BY n.name HAVING count(*)=1''', keys))
    occupied, titles = set(), []
    for key, positions in phrases:
        if key not in matches or occupied.intersection(positions):
            continue
        occupied.update(positions)
        if matches[key] not in titles:
            titles.append(matches[key])
        if len(titles) == 4:
            break
    clauses = ([base_query] if base_query else []) + [f'title:"{name_key(title)}"' for title in titles]
    return ' OR '.join(dict.fromkeys(clauses)), titles


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-report', type=Path, required=True)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--candidate-database', type=Path, required=True)
    parser.add_argument('--candidate-receipt', type=Path, required=True)
    parser.add_argument('--work-directory', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    sources = root.parent / 'android/app/src/main/kotlin/dev/blobs/app/data/library'
    if sha(sources / 'LibraryStore.kt') != LIBRARY_STORE_SHA256 or sha(sources / 'RetrievalQuery.kt') != QUERY_SHA256:
        raise ValueError('Native retrieval changed; review this diagnostic policy first')
    stop_words = set(re.findall(r'"([^"\\]+)"', (sources / 'RetrievalQuery.kt').read_text().split('private val stopWords = setOf(', 1)[1].split(')', 1)[0]))
    if args.output.exists():
        raise FileExistsError(args.output)
    native = json.loads(args.native_report.read_text(encoding='utf-8'))
    if not native['complete']:
        raise ValueError('Native run must be complete')
    cases = [row for row in native['results'] if row['variant'] in {'candidateLibrary', 'installedLibrary'}]
    if not 1 <= len(cases) <= 100:
        raise ValueError('Invalid case count')
    if len({row['case_id'] for row in cases}) != len(cases):
        raise ValueError('Use a report with one candidate library variant')
    report = dict(format=1, complete=False, script_sha256=sha(Path(__file__)),
                  retrieval_helper_sha256=sha(Path(__file__).with_name('host_replay.py')),
                  native_report_sha256=sha(args.native_report), sqlite=sqlite3.sqlite_version,
                  native_sources=dict(library_store=LIBRARY_STORE_SHA256, query=QUERY_SHA256),
                  scope='Host source-name and structural-section diagnostic, not device performance. Complete native evidence parity precedes the candidate run. The manifest identifies the actual candidate edition; do not infer full-corpus coverage. Development cases are not held out.',
                  baseline_parity=[], results=[])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.work_directory.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    try:
        baseline_db = args.work_directory.resolve() / 'names-parity.sqlite'
        baseline = unpack(args.baseline, baseline_db)
        for case in cases:
            if baseline != case['library']:
                raise ValueError('Native library identity differs')
            query, titles = expanded_query(baseline_db, case['resolved_query'], case['base_fts_query'], stop_words)
            route_ids = direct_title_ids(baseline_db, case['resolved_query'], titles)
            priority_ids = direct_title_ids(
                baseline_db, case['resolved_query'], titles, broad_only=True
            )
            evidence, _ = retrieve(
                baseline_db, baseline, query, question=case['resolved_query'], priority_ids=route_ids,
                broad_priority_ids=priority_ids,
            )
            same = evidence == case['evidence']
            report['baseline_parity'].append(dict(case_id=case['case_id'], identical=same, matched_titles=titles))
            if not same:
                raise ValueError('Host/native evidence differs: ' + case['case_id'])
        write_json(args.output, report)
        candidate = json.loads(args.candidate_receipt.read_text(encoding='utf-8'))['manifest']
        database = args.candidate_database.resolve()
        if database.stat().st_size != candidate['bytes'] or sha(database) != candidate['sha256']:
            raise ValueError('Candidate database differs from verified package identity')
        report['candidate'] = candidate
        report['cases'] = native['cases']
        for case in cases:
            base_start = time.monotonic()
            base_evidence, _ = retrieve(
                database, candidate, case['base_fts_query'], question=case['resolved_query']
            )
            base_elapsed = (time.monotonic() - base_start) * 1000
            start = time.monotonic()
            query, titles = expanded_query(database, case['resolved_query'], case['base_fts_query'], stop_words)
            route_ids = direct_title_ids(database, case['resolved_query'], titles)
            priority_ids = direct_title_ids(
                database, case['resolved_query'], titles, broad_only=True
            )
            evidence, candidates = retrieve(
                database, candidate, query, question=case['resolved_query'], priority_ids=route_ids,
                broad_priority_ids=priority_ids,
            )
            report['results'].append(dict(case_id=case['case_id'], resolved_query=case['resolved_query'],
                                          fts_query=query, matched_titles=titles, evidence=evidence,
                                          base_policy=dict(fts_query=case['base_fts_query'], evidence=base_evidence,
                                                           elapsed_host_ms=base_elapsed),
                                          candidates=candidates, elapsed_host_ms=(time.monotonic() - start) * 1000))
            write_json(args.output, report)
            print(case['case_id'] + ': ' + ', '.join(dict.fromkeys(row['title'] for row in evidence)), flush=True)
        report['complete'] = True
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        write_json(args.output, report)


if __name__ == '__main__':
    main()
