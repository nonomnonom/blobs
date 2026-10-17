"""Revalidate retained raw model outputs without treating format as answer quality."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re

from blobs_corpus.extract import write_json
from .citation_output import parse_answer
from ..retrieval.host_replay import sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, nargs='+', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    if len({path.resolve() for path in args.input}) != len(args.input):
        raise ValueError('Repeated input report')
    results = []
    for path in args.input:
        report = json.loads(path.read_text(encoding='utf-8'))
        if not report['complete']:
            raise ValueError('Refuse unfinished generation reports')
        rows = []
        for row in report['results']:
            observation = dict(case_id=row['case_id'], generation_status=row['status'],
                               source_count=len(row['evidence']))
            if row['status'] == 'RECORDED':
                raw = row['response']['content']
                observation['stop_type'] = row['response'].get('stop_type')
                try:
                    parsed = parse_answer(raw, len(row['evidence']))
                    texts = ([claim['text'] for claim in parsed['claims']] if parsed['status'] == 'answered'
                             else [parsed['reason']])
                    observation.update(structure_valid=True, value=parsed,
                        article_like_markers_in_text=[re.findall(r'\[\d{1,6}\]', text) for text in texts],
                        source_id_mentions_in_text=[re.findall(r'\bS[1-9]\d{0,5}\b', text) for text in texts])
                    prior = row.get('structured_validation')
                    if prior is not None:
                        observation['agrees_with_recorded_validation'] = prior.get('valid') is True and prior.get('value') == parsed
                except ValueError as error:
                    observation.update(structure_valid=False, error=str(error))
            else:
                observation['scope'] = 'Not a completed model answer; excluded from structural success counts.'
            rows.append(observation)
        results.append(dict(file=path.name, sha256=sha(path), model=report['model'],
            grammar_requested=report.get('structured_citations', False),
            generation_status_counts=dict(Counter(row['generation_status'] for row in rows)),
            structurally_valid_recorded_outputs=sum(row.get('structure_valid') is True for row in rows),
            results=rows))
    audit = dict(format=1, complete=True,
        scope='Structural observations only. No citation entailment, answer accuracy, usefulness, safety or bounty score. Raw failures are preserved; text footnotes are recorded but never converted into source references.',
        script_sha256=sha(Path(__file__)), validator_sha256=sha(Path(__file__).with_name('citation_output.py')),
        results=results)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, audit)
    for report in results:
        print(f'{report["file"]}: {report["structurally_valid_recorded_outputs"]} structurally valid; {report["generation_status_counts"]}')


if __name__ == '__main__':
    main()
