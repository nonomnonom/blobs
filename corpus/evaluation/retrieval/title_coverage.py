"""Inspect source-title availability without running retrieval or judging answerability."""
import argparse
from collections import Counter
import json
from pathlib import Path

from blobs_corpus.extract import write_json
from blobs_corpus.records import documents
from .host_replay import sha


def inspect(records, questions):
    record_hash, question_hash = sha(records), sha(questions)
    cases = json.loads(questions.read_text(encoding='utf-8'))['cases']
    if not isinstance(cases, list) or not cases:
        raise ValueError('Require a nonempty question set')
    seen = set()
    for case in cases:
        if not isinstance(case, dict):
            raise ValueError('Each case must be an object')
        if not isinstance(case.get('id'), str) or not case['id'].strip() or case['id'] in seen:
            raise ValueError('Require unique nonempty case IDs')
        seen.add(case['id'])
        if not isinstance(case.get('category'), str) or not case['category'].strip():
            raise ValueError('Require a category for each case')
        targets = case.get('expected_titles')
        if (not isinstance(targets, list) or not targets or
                any(not isinstance(title, str) or not title.strip() for title in targets) or
                len(set(targets)) != len(targets)):
            raise ValueError('Require distinct nonempty title targets for each case')
    titles, count = set(), 0
    for document in documents(records):
        titles.add(document['title'])
        count += 1
    if sha(records) != record_hash or sha(questions) != question_hash:
        raise ValueError('Inputs changed while inspecting title availability')
    rows = [dict(case_id=case['id'], category=case['category'],
                 present=[title for title in case['expected_titles'] if title in titles],
                 absent=[title for title in case['expected_titles'] if title not in titles])
            for case in cases]
    targets = {title for case in cases for title in case['expected_titles']}
    return dict(format=1, complete=True,
        scope='Exact source-title availability only, not passage relevance, answerability or recall. An absent title may be covered by another article. A present title may lack the required evidence. No retrieval or model answers executed.',
        records_sha256=record_hash, questions_sha256=question_hash,
        script_sha256=sha(Path(__file__)), documents=count, unique_titles=len(titles),
        cases=len(cases), categories=dict(Counter(case['category'] for case in cases)),
        expected_unique_titles=len(targets), present_unique_titles=len(targets & titles),
        absent_unique_titles=sorted(targets - titles),
        cases_with_all_target_titles=sum(not row['absent'] for row in rows),
        cases_with_some_target_titles=sum(bool(row['present']) and bool(row['absent']) for row in rows),
        cases_with_no_target_titles=sum(not row['present'] for row in rows), results=rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--records', type=Path, required=True)
    parser.add_argument('--questions', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = inspect(args.records, args.questions)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(f'{report["cases"]} questions; {report["present_unique_titles"]}/{report["expected_unique_titles"]} target titles present. Answerability not evaluated.')


if __name__ == '__main__':
    main()
