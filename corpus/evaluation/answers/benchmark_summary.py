"""Promote a complete host answer run to a compact, text-free benchmark record."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import statistics

from .export_host_run import export_run, read_json, sha256


def _percentile(values, fraction):
    ordered = sorted(values)
    return ordered[max(0, min(len(ordered) - 1, math.ceil(len(ordered) * fraction) - 1))] if ordered else None


def summarize(run, cases, run_path: Path, cases_path: Path):
    """Keep provenance and mechanical metrics; omit generated text and source passages."""
    exported = export_run(run, cases, run_path, cases_path)
    completed = [row for row in exported['results'] if row['generation_status'] == 'RECORDED']
    elapsed = [row['elapsed_ms'] for row in completed if isinstance(row['elapsed_ms'], (int, float))]
    first_content = [row['first_content_ms'] for row in completed
                     if isinstance(row['first_content_ms'], (int, float))]
    source_counts = [row['source_count'] for row in completed]
    raw_results = {row['case_id']: row for row in run['results']}

    def timing_for(case_id, name):
        response = raw_results[case_id].get('response') or {}
        return response.get('timings', {}).get(name)

    def timing_values(name):
        values = []
        for item in completed:
            timing = timing_for(item['case_id'], name)
            if isinstance(timing, (int, float)):
                values.append(timing)
        return values

    prefill = timing_values('prompt_ms')
    generation = timing_values('predicted_ms')
    prefill_rate = timing_values('prompt_per_second')
    generation_rate = timing_values('predicted_per_second')
    generated_tokens = timing_values('predicted_n')
    summary = dict(
        case_count=exported['summary']['case_count'],
        categories=exported['summary']['categories'],
        generation_status_counts=exported['summary']['generation_status_counts'],
        host_elapsed_ms_median=statistics.median(elapsed) if elapsed else None,
        host_elapsed_ms_p95=_percentile(elapsed, .95),
        first_content_ms_median=statistics.median(first_content) if first_content else None,
        first_content_ms_p95=_percentile(first_content, .95),
        prompt_tokens_median=exported['summary']['prompt_tokens_median'],
        evidence_passages_median=statistics.median(source_counts) if source_counts else None,
        model_prefill_ms_median=statistics.median(prefill) if prefill else None,
        model_generation_ms_median=statistics.median(generation) if generation else None,
        model_prefill_tokens_per_second_median=statistics.median(prefill_rate) if prefill_rate else None,
        model_generation_tokens_per_second_median=statistics.median(generation_rate) if generation_rate else None,
        generated_tokens_median=statistics.median(generated_tokens) if generated_tokens else None,
    )
    return dict(
        format=1,
        complete=True,
        scope=('Host-only model-execution and resource-shape observations on a pinned case set, model, '
               'runtime, and retrieved evidence. No answer correctness or semantic quality is scored. '
               'These timings do not predict phone performance.'),
        inputs=exported['inputs'],
        model=exported['model'],
        model_package_sha256=run.get('model_package_sha256'),
        runtime=exported['runtime'],
        host=exported['host'],
        inference=exported['inference'],
        system_prompt_sha256=hashlib.sha256(run.get('system', '').encode('utf-8')).hexdigest(),
        summary=summary,
        cases=[{
            'case_id': row['case_id'],
            'category': row['category'],
            'generation_status': row['generation_status'],
            'elapsed_ms': row['elapsed_ms'],
            'first_content_ms': row['first_content_ms'],
            'prompt_tokens': row['prompt_tokens'],
            'source_count': row['source_count'],
            'model_prefill_ms': timing_for(row['case_id'], 'prompt_ms'),
            'model_generation_ms': timing_for(row['case_id'], 'predicted_ms'),
            'model_prefill_tokens_per_second': timing_for(row['case_id'], 'prompt_per_second'),
            'model_generation_tokens_per_second': timing_for(row['case_id'], 'predicted_per_second'),
            'generated_tokens': timing_for(row['case_id'], 'predicted_n'),
        } for row in exported['results']],
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--cases', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    record = summarize(read_json(args.run), read_json(args.cases), args.run, args.cases)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + '.partial')
    if temporary.exists():
        raise FileExistsError(temporary)
    try:
        temporary.write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        temporary.replace(args.output)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise
    print(json.dumps(record['summary'], ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
