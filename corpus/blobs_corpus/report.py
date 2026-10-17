"""Audit the extracted corpus without confusing coverage with answer quality."""
from collections import Counter
import hashlib
import heapq
import json
from pathlib import Path

from .extract import verified_shards, write_json
from .library import passage_spans
from .records import documents


def report(source: Path, output: Path):
    manifest = verified_shards(source)
    counts, categories, lengths, hashes, samples = Counter(), Counter(), [], set(), []
    ids, urls = set(), set()
    for item in documents(source):
        if item['id'] in ids:
            raise ValueError(f'Duplicate document ID: {item["id"]}')
        ids.add(item['id'])
        counts['duplicate_urls'] += int(item['url'] in urls)
        urls.add(item['url'])
        body, provenance = item['body'], item.get('provenance', {})
        body_hash = hashlib.sha256(body.encode()).hexdigest()
        counts['documents'] += 1
        counts['duplicate_bodies'] += int(body_hash in hashes)
        hashes.add(body_hash)
        counts['attributed'] += int(bool(item['publisher'] and item['license'] and item['url']))
        counts['with_revision_id'] += int(provenance.get('revision_id') is not None)
        counts['with_categories'] += int(bool(provenance.get('categories')))
        counts['text_characters'] += len(body)
        counts['text_utf8_bytes'] += len(body.encode())
        counts['replacement_characters'] += body.count('\ufffd')
        counts['table_rows'] += body.count(' | ')
        normalization = provenance.get('normalization', {})
        counts['aria_navigation_nodes_removed'] += normalization.get('aria_navigation_nodes_removed', 0)
        counts['leading_infoboxes_moved'] += normalization.get('leading_infoboxes_moved', 0)
        for category in provenance.get('categories', []):
            if not category.startswith(('Articles ', 'All ', 'CS1 ', 'CS1:', 'Wikipedia ', 'Pages ', 'Webarchive ', 'Use ', 'Short description', 'Commons category')):
                categories[category] += 1
        for span in passage_spans(body, item['title']):
            if body[span['start']:span['end']] != span['body']:
                raise ValueError('Passage offset is not an exact source slice')
            counts['passages'] += 1
        lengths.append(len(body))
        # Content-independent stable sample: choose the 12 lowest ID hashes.
        # This does not use benchmark questions, text quality or document length.
        key = int(hashlib.sha256(item['id'].encode()).hexdigest(), 16)
        sample = dict(id=item['id'], title=item['title'], url=item['url'],
                      characters=len(body), revision_id=provenance.get('revision_id'),
                      excerpt=body[:2500])
        heapq.heappush(samples, (-key, item['id'], sample))
        if len(samples) > 12:
            heapq.heappop(samples)
    if not lengths:
        raise ValueError('No documents to review')
    lengths.sort()
    result = dict(format=1, source=manifest['source'], extractor=manifest['identity'],
                  curation=manifest.get('curation'),
                  acquisition=manifest['totals'], quality=dict(counts),
                  length_characters={name: lengths[int((len(lengths) - 1) * q)]
                                     for name, q in [('min',0),('median',.5),('p95',.95),('max',1)]},
                  top_categories=categories.most_common(60),
                  review_sample=[value for _, _, value in sorted(samples, reverse=True)],
                  limitations=['Wikipedia selection, not the complete encyclopedia.',
                               'Categories overlap and are absent when source metadata cannot be parsed.',
                               'Tables retain text and rows; visual layout and images are not reproduced.',
                               'Snapshot dates are not per-article modification dates.',
                               'These checks do not establish answer accuracy or balanced subject coverage.'])
    output.parent.mkdir(parents=True, exist_ok=True)
    write_json(output, result)
    print(json.dumps({**dict(counts), 'report': str(output)}, indent=2))
    return result


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    report(args.source, args.output)
