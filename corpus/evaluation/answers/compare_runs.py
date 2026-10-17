"""Compare controlled host answer runs without scoring factual support."""
import argparse
import json
import math
from pathlib import Path
import re
import statistics

from blobs_corpus.extract import write_json
from evaluation.retrieval.host_replay import sha


CITATION = re.compile(r'\[(S[1-9][0-9]*)\]')


def read_run(path):
    report = json.loads(path.read_text(encoding='utf-8-sig'))
    if report.get('format') != 1 or report.get('complete') is not True:
        raise ValueError(f'Run is incomplete or unsupported: {path}')
    return report


def comparable(baseline, candidate):
    for key in ('runtime_commit', 'server_sha256', 'retrieval_report_sha256',
                'retrieval_variant', 'sampling_profile', 'sampling', 'context',
                'threads', 'batch', 'ubatch', 'source_id_scheme'):
        if baseline.get(key) != candidate.get(key):
            raise ValueError(f'Runs differ in controlled field: {key}')
    if baseline.get('model', {}).get('sha256') != candidate.get('model', {}).get('sha256'):
        raise ValueError('Runs use different model weights')
    left, right = baseline.get('results'), candidate.get('results')
    if not isinstance(left, list) or not isinstance(right, list) or not left or len(left) != len(right):
        raise ValueError('Runs must contain the same nonempty case set')
    rows = []
    for before, after in zip(left, right):
        if (before.get('case_id') != after.get('case_id') or
                before.get('question') != after.get('question') or
                before.get('evidence') != after.get('evidence')):
            raise ValueError('Runs must use identical questions and retrieved evidence in the same order')
        before_answer = before.get('response', {}).get('content', '')
        after_answer = after.get('response', {}).get('content', '')
        sources = {f'S{index}' for index in range(1, len(after['evidence']) + 1)}
        before_ids, after_ids = set(CITATION.findall(before_answer)), set(CITATION.findall(after_answer))
        rows.append(dict(
            case_id=before['case_id'], question=before['question'], evidence=after['evidence'],
            baseline_answer=before_answer, candidate_answer=after_answer,
            baseline_elapsed_ms=before.get('host_elapsed_ms'),
            candidate_elapsed_ms=after.get('host_elapsed_ms'),
            baseline_answer_characters=len(before_answer), candidate_answer_characters=len(after_answer),
            baseline_citation_ids=sorted(before_ids), candidate_citation_ids=sorted(after_ids),
            baseline_invalid_citation_ids=sorted(before_ids - sources),
            candidate_invalid_citation_ids=sorted(after_ids - sources)))
    return rows


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, min(len(ordered) - 1, math.ceil(len(ordered) * fraction) - 1))]


def compare(baseline_path, candidate_path):
    baseline, candidate = read_run(baseline_path), read_run(candidate_path)
    rows = comparable(baseline, candidate)
    before = [row['baseline_elapsed_ms'] for row in rows if isinstance(row['baseline_elapsed_ms'], (int, float))]
    after = [row['candidate_elapsed_ms'] for row in rows if isinstance(row['candidate_elapsed_ms'], (int, float))]
    if len(before) != len(rows) or len(after) != len(rows):
        raise ValueError('Every case needs a recorded host elapsed time')

    def latency(values):
        return dict(mean_ms=statistics.mean(values), median_ms=statistics.median(values),
                    p95_ms=percentile(values, .95), maximum_ms=max(values))

    return dict(
        format=1, complete=True,
        scope=('Controlled host CPU comparison on identical model, runtime, sampler, questions, and evidence. '
               'Host timings are not Android latency. Valid source-ID syntax does not establish claim support.'),
        baseline=dict(sha256=sha(baseline_path), system=baseline['system'], latency=latency(before)),
        candidate=dict(sha256=sha(candidate_path), system=candidate['system'], latency=latency(after)),
        model=baseline['model'], runtime_commit=baseline['runtime_commit'],
        retrieval_report_sha256=baseline['retrieval_report_sha256'],
        sampling_profile=baseline['sampling_profile'], sampling=baseline['sampling'],
        cases=len(rows), results=rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = compare(args.baseline, args.candidate)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(f"Compared {report['cases']} identical cases; host p50 "
          f"{report['baseline']['latency']['median_ms']:.0f} -> "
          f"{report['candidate']['latency']['median_ms']:.0f} ms. Factual support needs review.")


if __name__ == '__main__':
    main()
