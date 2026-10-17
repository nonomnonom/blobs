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
LIBRARY_STORE_SHA256 = '0f9d163efecf80f4d8485be70369663554656c28987f6407853e4aec9372cda4'
QUERY_SHA256 = '136427613783576c1188a157251241d9390d4f89913f58a4b56d0881ce56f7a5'
STOP_WORDS = {
    'the', 'and', 'or', 'not', 'for', 'with', 'that', 'this', 'what', 'how', 'why',
    'are', 'does', 'can', 'you', 'about', 'from', 'which', 'have', 'would', 'their',
    'they', 'its', 'these', 'those', 'compare', 'explain', 'between', 'difference',
    'versus', 'could', 'do', 'did', 'be', 'it', 'into', 'also', 'tell', 'please', 'me',
    'more', 'than', 'when', 'where', 'who', 'was', 'were', 'has', 'had', 'is', 'of',
    'to', 'by', 'on', 'in', 'an', 'as', 'at', 'if', 'we', 'us', 'our', 'describe'
}


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


def retrieve(database, manifest, query):
    with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as db:
        db.execute('PRAGMA trusted_schema=OFF')
        db.execute('PRAGMA query_only=ON')
        schema = db.execute('PRAGMA user_version').fetchone()[0]
        if schema not in (2, 3):
            raise ValueError(f'Unsupported library schema: {schema}')
        db.row_factory = sqlite3.Row
        if schema == 2:
            statement = '''SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,
                p.section,p.body,p.title AS opening_title,p.id AS passage_id,rank AS score
                FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
                JOIN documents d ON d.id=p.doc_id WHERE passages_fts MATCH ?
                AND (p.title!='' OR lower(trim(p.section)) NOT IN
                  ('references','external links','see also','further reading','bibliography'))
                ORDER BY rank,p.id LIMIT 48'''
        else:
            statement = '''SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,
                p.section,substr(d.body,s.start+1,s.end-s.start) AS body,p.title AS opening_title,
                p.id AS passage_id,rank AS score
                FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
                JOIN passage_spans s ON s.passage_id=p.id
                JOIN documents d ON d.id=p.doc_id WHERE passages_fts MATCH ?
                AND (p.title!='' OR lower(trim(p.section)) NOT IN
                  ('references','external links','see also','further reading','bibliography'))
                ORDER BY rank,p.id LIMIT 48'''
        candidates = [dict(row) for row in db.execute(statement, (query,))] if query else []
    return select_evidence(candidates, manifest), candidates


def retrieval_spec(database, question):
    """Mirror LibraryStore's unique source-name routing and FTS query construction."""
    import sqlite3
    import unicodedata
    import re

    normalized = unicodedata.normalize('NFKC', question).lower()
    words = re.findall(r'[\w]+', normalized, flags=re.UNICODE)[:40]
    candidates = []
    for size in range(min(6, len(words)), 0, -1):
        for start in range(len(words) - size + 1):
            phrase = words[start:start + size]
            key = ' '.join(phrase)
            if 2 <= len(key) <= 600 and any(word not in STOP_WORDS for word in phrase):
                candidates.append((key, set(range(start, start + size))))
    with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as db:
        names = list(dict.fromkeys(key for key, _ in candidates))
        if names:
            placeholders = ','.join('?' for _ in names)
            rows = db.execute(
                f'''SELECT n.name,min(d.title),min(d.id) FROM document_names n
                    JOIN documents d ON d.id=n.doc_id WHERE n.name IN ({placeholders})
                    GROUP BY n.name HAVING count(*)=1''', names).fetchall()
            matches = {row[0]: (row[1], row[2]) for row in rows}
        else:
            matches = {}
    occupied, titles = set(), []
    for key, positions in candidates:
        if key not in matches or occupied.intersection(positions):
            continue
        occupied.update(positions)
        title = matches[key][0]
        if title in titles:
            continue
        titles.append(title)
        if len(titles) == 4:
            break
    terms = list(dict.fromkeys(
        word for word in re.findall(r'[\w]{2,}', normalized, flags=re.UNICODE)
        if word not in STOP_WORDS
    ))[:20]
    query = [f'"{term}"' for term in terms]
    for title in titles:
        phrase = ' '.join(re.findall(r'[\w]+', unicodedata.normalize('NFKC', title).lower(), flags=re.UNICODE))
        if phrase:
            query.append(f'title:"{phrase}"')
    return ' OR '.join(dict.fromkeys(query)), titles


def retrieval_query(database, question):
    return retrieval_spec(database, question)[0]


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
    parser.add_argument('--baseline', type=Path)
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
    cases = [case for case in native['results']
             if case['variant'] in {'candidateLibrary', 'installedLibrary'}]
    if not 1 <= len(cases) <= 100:
        raise ValueError('Invalid comparison case count')
    work = args.work_directory.resolve()
    work.mkdir(parents=True, exist_ok=True)
    report = dict(format=1, complete=False, sqlite=sqlite3.sqlite_version,
                  script_sha256=sha(Path(__file__)), native_report_sha256=sha(args.native_report),
                  native_sources=dict(library_store=LIBRARY_STORE_SHA256, query=QUERY_SHA256),
                  scope='Host replay of the pinned Android retrieval policy, including source-name routing, on the exact native-evaluated package. Host elapsed times are diagnostics, never phone latency or full-size Android import proof.',
                  cases=native['cases'], baseline_parity=[], results=[])

    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.output.with_suffix('.json.tmp')
        temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        os.replace(temporary, args.output)

    save()
    try:
        if args.baseline:
            baseline = unpack(args.baseline, work / 'starter.sqlite')
            baseline_cases = [case for case in native['results'] if case['variant'] == 'baselineLibrary']
            if len(baseline_cases) != len(cases):
                raise ValueError('Native report must contain matching baseline and candidate cases')
            for case in baseline_cases:
                if case['library'] != baseline:
                    raise ValueError('The baseline package differs from the native evidence')
                query, _ = retrieval_spec(work / 'starter.sqlite', case['resolved_query'])
                evidence, _ = retrieve(work / 'starter.sqlite', baseline, query)
                same = evidence == case['evidence']
                report['baseline_parity'].append(dict(case_id=case['case_id'], identical=same))
                if not same:
                    raise ValueError(f'Host/native evidence differs: {case["case_id"]}')
            save()
            print(f'Native starter parity passed for {len(baseline_cases)} cases.', flush=True)
        candidate = unpack(args.candidate, work / 'expanded.sqlite')
        report['candidate'] = candidate
        save()
        for case in cases:
            started = time.monotonic()
            query, titles = retrieval_spec(work / 'expanded.sqlite', case['resolved_query'])
            evidence, candidates = retrieve(work / 'expanded.sqlite', candidate, query)
            same = evidence == case['evidence']
            report['results'].append(dict(case_id=case['case_id'], resolved_query=case['resolved_query'],
                                          fts_query=query,
                                          matched_titles=titles,
                                          elapsed_host_ms=(time.monotonic() - started) * 1000,
                                          native_evidence_identical=same,
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
