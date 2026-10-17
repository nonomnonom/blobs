"""Replay one frozen Android retrieval policy for full-corpus diagnosis, not phone benchmarks."""
import argparse
from collections import Counter
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import shutil
import sqlite3
import tempfile
import time
import zipfile


# Any retrieval-policy change requires a new native run and a reviewed replay.
LIBRARY_STORE_SHA256 = 'f86dc5f712a10654c47e6978fd10a25dc90776b540025d49adc8972cbdac65aa'
QUERY_SHA256 = '37555a1e3ca5bbe613f1f4c6de82540ea1c227a4f385b6b1488b454c14a24186'
SQL = '''SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,p.section,p.body,p.title AS opening_title,
p.id AS passage_id,rank AS score
FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
JOIN documents d ON d.id=p.doc_id WHERE passages_fts MATCH ?
ORDER BY rank,p.id LIMIT 48'''


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def unpack(package, target):
    with zipfile.ZipFile(package) as archive:
        if archive.namelist() != ['manifest.json', 'library.sqlite']:
            raise ValueError('Expected one library payload after its manifest')
        if archive.getinfo('manifest.json').file_size > 65536:
            raise ValueError('Manifest exceeds the native bound')
        manifest = json.loads(archive.read('manifest.json'))
        if manifest['kind'] != 'library' or not 0 < manifest['bytes'] <= 40_000_000_000:
            raise ValueError('Invalid library manifest')
        if archive.getinfo('library.sqlite').file_size != manifest['bytes']:
            raise ValueError('Payload length differs from manifest')
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if target.stat().st_size != manifest['bytes'] or sha(target) != manifest['sha256']:
                raise ValueError('Existing review database differs from this package')
            return manifest
        if shutil.disk_usage(target.parent).free < manifest['bytes'] + 1024**3:
            raise OSError('Allow the complete database plus 1 GiB free space')
        with tempfile.TemporaryDirectory(prefix='.unpack-', dir=target.parent) as temporary:
            staged = Path(temporary) / 'library.sqlite'
            digest, count = hashlib.sha256(), 0
            with archive.open('library.sqlite') as source, staged.open('xb') as output:
                while block := source.read(1024 * 1024):
                    count += len(block)
                    if count > manifest['bytes']:
                        raise ValueError('Payload exceeds its declared length')
                    digest.update(block)
                    output.write(block)
                output.flush()
                os.fsync(output.fileno())
            if count != manifest['bytes'] or digest.hexdigest() != manifest['sha256']:
                raise ValueError('Payload verification failed')
            os.link(staged, target)
    return manifest


def retrieve(database, manifest, query, *, sql=SQL):
    with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as db:
        db.execute('PRAGMA trusted_schema=OFF')
        db.execute('PRAGMA query_only=ON')
        if db.execute('PRAGMA user_version').fetchone()[0] != 2:
            raise ValueError('This replay supports schema 2 only')
        db.row_factory = sqlite3.Row
        candidates = [dict(row) for row in db.execute(sql, (query,))] if query else []
    return select_evidence(candidates, manifest), candidates


def select_evidence(candidates, manifest):
    """Apply the frozen native whole-passage, document and UTF-16 budgets."""
    selected, counts, characters = [], Counter(), 0
    key = f'{manifest["id"]}-{manifest["version"]}-{manifest["sha256"][:16]}'
    for row in candidates:
        size = len(row['body'].encode('utf-16-le')) // 2
        if counts[row['id']] >= 2 or characters + size > 6000:
            continue
        counts[row['id']] += 1
        characters += size
        evidence = {name: row[name] for name in ('title', 'publisher', 'url', 'license', 'updated', 'saved', 'section')}
        evidence.update(id=f'{key}/{row["id"]}', text=row['body'],
                        edition=f'{manifest["title"]} · {manifest["version"]} · {manifest["sha256"]}')
        selected.append(evidence)
        if len(selected) == 6:
            break
    return selected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-report', type=Path, required=True)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--work-directory', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    sources = root.parent / 'android/app/src/main/kotlin/dev/blobs/app/data/library'
    if sha(sources / 'LibraryStore.kt') != LIBRARY_STORE_SHA256 or sha(sources / 'RetrievalQuery.kt') != QUERY_SHA256:
        raise ValueError('Native retrieval code changed; review this frozen replay before using it')
    if args.output.exists():
        raise FileExistsError(args.output)
    native = json.loads(args.native_report.read_text(encoding='utf-8'))
    if not native['complete']:
        raise ValueError('Native comparison must be complete')
    cases = [case for case in native['results'] if case['variant'] == 'candidateLibrary']
    if not 1 <= len(cases) <= 100:
        raise ValueError('Invalid comparison case count')
    work = args.work_directory.resolve()
    work.mkdir(parents=True, exist_ok=True)
    report = dict(format=1, complete=False, sqlite=sqlite3.sqlite_version,
                  script_sha256=sha(Path(__file__)), native_report_sha256=sha(args.native_report),
                  native_sources=dict(library_store=LIBRARY_STORE_SHA256, query=QUERY_SHA256),
                  scope='Host replay of a frozen unscoped single-library retrieval policy. Native starter parity is checked first. Host elapsed times are diagnostics, never phone latency or full-size Android import proof.',
                  cases=native['cases'], baseline_parity=[], results=[])

    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.output.with_suffix('.json.tmp')
        temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        os.replace(temporary, args.output)

    save()
    try:
        baseline = unpack(args.baseline, work / 'starter.sqlite')
        for case in cases:
            if case['library'] != baseline:
                raise ValueError('The baseline package differs from the native evidence')
            evidence, _ = retrieve(work / 'starter.sqlite', baseline, case['fts_query'])
            same = evidence == case['evidence']
            report['baseline_parity'].append(dict(case_id=case['case_id'], identical=same))
            if not same:
                raise ValueError(f'Host/native evidence differs: {case["case_id"]}')
        save()
        print(f'Native parity passed for {len(cases)} starter cases. Unpacking the full candidate.', flush=True)
        candidate = unpack(args.candidate, work / 'expanded.sqlite')
        report['candidate'] = candidate
        save()
        for case in cases:
            started = time.monotonic()
            evidence, candidates = retrieve(work / 'expanded.sqlite', candidate, case['fts_query'])
            report['results'].append(dict(case_id=case['case_id'], fts_query=case['fts_query'],
                                          elapsed_host_ms=(time.monotonic() - started) * 1000,
                                          evidence=evidence, candidates=candidates))
            save()
            print(f'{case["case_id"]}: ' + ', '.join(dict.fromkeys(row['title'] for row in evidence)), flush=True)
        report['complete'] = True
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        save()
    print(f'Full-corpus host diagnostics saved to {args.output}', flush=True)


if __name__ == '__main__':
    main()
