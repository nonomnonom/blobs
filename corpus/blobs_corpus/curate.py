"""Remove Kiwix support pages from a verified extraction, preserving its lineage."""
from collections import Counter
import copy
import hashlib
import os
from pathlib import Path
import tempfile

from .acquire import digest
from .extract import verified_shards, write_json, write_lines
from .records import documents

SUPPORTED_SOURCES = {'wikipedia-en-top', 'wikipedia-en-top1m-mini'}


def exclusion(document):
    path = document.get('provenance', {}).get('entry_path', '')
    if path.startswith('_assets_/'):
        return 'Bundled support asset, not a Wikipedia article'
    if path == 'index':
        return 'Archive navigation page, not a Wikipedia article'
    return None


def curate(source: Path, output: Path):
    original = verified_shards(source)
    if not original['complete']:
        raise ValueError('Finish extraction before review')
    if original['source']['id'] not in SUPPORTED_SOURCES or original.get('curation'):
        raise ValueError('This review policy applies once to a supported raw Kiwix English top extraction')
    if output.exists():
        raise FileExistsError(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest = copy.deepcopy(original)
    excluded, totals = [], Counter()
    with tempfile.TemporaryDirectory(prefix='.curate-', dir=output.parent) as temporary:
        staged = Path(temporary) / 'reviewed'
        staged.mkdir()
        for shard in manifest['shards']:
            info = shard['files']['documents']
            path = source.parent / info['name']
            counts = Counter(shard['counts'])
            changed = False
            for doc in documents(path):
                reason = exclusion(doc)
                if reason is None:
                    continue
                changed = True
                excluded.append(dict(id=doc['id'], title=doc['title'],
                                     entry_path=doc['provenance']['entry_path'], reason=reason))
                body = doc['body']
                removed = dict(documents=1, attributed=1, text_characters=len(body),
                               text_utf8_bytes=len(body.encode()),
                               with_headings=int('\n\n==' in body or body.startswith('==')),
                               short_under_1000_chars=int(len(body) < 1000),
                               long_over_100000_chars=int(len(body) > 100000))
                counts.subtract({key: value for key, value in removed.items() if key in counts})
                counts['excluded_non_articles'] += 1
            for kind, item in shard['files'].items():
                target = staged / item['name']
                if kind == 'documents' and changed:
                    write_lines(target, (doc for doc in documents(path) if exclusion(doc) is None))
                    item.update(bytes=target.stat().st_size, sha256=digest(target))
                else:
                    os.link(source.parent / item['name'], target)
            shard['counts'] = dict(counts)
            totals.update(counts)
        manifest['totals'] = dict(totals)
        manifest['curation'] = dict(policy='Kiwix article-only review v1',
                                    source_id=original['source']['id'],
                                    policy_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                                    input_manifest_sha256=digest(source),
                                    input_totals=original['totals'], excluded_documents=excluded)
        write_json(staged / 'manifest.json', manifest)
        verified_shards(staged / 'manifest.json')
        os.rename(staged, output)
    print(f'Reviewed {totals["documents"]:,} articles; excluded {len(excluded)} support pages; {output}', flush=True)
    return manifest


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    curate(args.source, args.output)
