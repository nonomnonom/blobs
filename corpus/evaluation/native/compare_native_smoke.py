"""Compare repeated, audited native model smoke runs without claiming answer quality."""
import argparse
import json
from pathlib import Path
import statistics

from blobs_corpus.extract import write_json
from ..retrieval.host_replay import sha


def load(path: Path):
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict) or value.get('complete') is not True:
        raise ValueError(f'Expected a complete native smoke audit: {path}')
    if not value.get('checks') or not all(value['checks'].values()):
        raise ValueError(f'Native smoke audit contains failed checks: {path}')
    return value


def summarize(paths):
    if len(paths) < 3:
        raise ValueError('Require at least three audited runs per model')
    reports = [load(path) for path in paths]
    fingerprints = {report['observations'].get('device_fingerprint') or report.get('device_fingerprint')
                    for report in reports}
    models = {(report['model']['bytes'], report['model']['sha256']) for report in reports}
    applications = {(report['application']['apk_sha256'], report['application']['test_apk_sha256'])
                    for report in reports}
    if len(fingerprints) != 1 or None in fingerprints:
        raise ValueError('Runs must use the same identified device')
    if len(models) != 1 or len(applications) != 1:
        raise ValueError('Runs must use the same model and installed test build')

    rows = []
    for path, report in zip(paths, reports):
        observation = report['observations']
        metrics = observation.get('inference_metrics')
        if not isinstance(metrics, dict):
            raise ValueError('Native smoke audit lacks stage metrics')
        required = ('total_us', 'first_token_callback_us', 'prefill_us', 'model_load_us',
                    'generation_loop_us', 'prompt_tokens', 'output_tokens', 'inference_threads')
        if any(type(metrics.get(field)) is not int or metrics[field] < 0 for field in required):
            raise ValueError('Native smoke metrics are incomplete or invalid')
        if (not metrics['prompt_tokens'] or not metrics['output_tokens'] or
                not metrics['generation_loop_us'] or not 1 <= metrics['inference_threads'] <= 4):
            raise ValueError('Native smoke did not produce complete inference measurements')
        if not metrics['total_us'] >= metrics['first_token_callback_us'] >= metrics['prefill_us']:
            raise ValueError('Native smoke timing stages are out of order')
        rows.append(dict(
            audit_file=path.name,
            audit_sha256=sha(path),
            run_file=report['inputs']['run']['file'],
            run_sha256=report['inputs']['run']['sha256'],
            prompt_tokens=metrics['prompt_tokens'], output_tokens=metrics['output_tokens'],
            total_ms=metrics['total_us'] / 1000,
            first_token_ms=metrics['first_token_callback_us'] / 1000,
            prefill_ms=metrics['prefill_us'] / 1000,
            model_load_ms=metrics['model_load_us'] / 1000,
            inference_threads=metrics['inference_threads'],
            decode_tokens_per_second=(metrics['output_tokens'] * 1_000_000 / metrics['generation_loop_us']
                                      if metrics['generation_loop_us'] else None),
        ))

    fields = ('total_ms', 'first_token_ms', 'prefill_ms', 'model_load_ms',
              'decode_tokens_per_second', 'output_tokens', 'inference_threads')
    medians = {field: statistics.median(row[field] for row in rows) for field in fields}
    ranges = {field: [min(row[field] for row in rows), max(row[field] for row in rows)]
              for field in fields}
    prompt_counts = {row['prompt_tokens'] for row in rows}
    if len(prompt_counts) != 1:
        raise ValueError('Runs in one model group must have identical prompt token counts')
    return dict(model=dict(bytes=next(iter(models))[0], sha256=next(iter(models))[1]),
                device_fingerprint=next(iter(fingerprints)),
                apk_sha256=next(iter(applications))[0], test_apk_sha256=next(iter(applications))[1],
                prompt_tokens=next(iter(prompt_counts)), runs=rows, median=medians, range=ranges)


def compare(baseline_paths, candidate_paths, runtime_lock_path: Path):
    baseline, candidate = summarize(baseline_paths), summarize(candidate_paths)
    if baseline['device_fingerprint'] != candidate['device_fingerprint']:
        raise ValueError('Baseline and candidate must use the same device fingerprint')
    if (baseline['apk_sha256'], baseline['test_apk_sha256']) != (
            candidate['apk_sha256'], candidate['test_apk_sha256']):
        raise ValueError('Baseline and candidate must use identical application and test APKs')
    if baseline['prompt_tokens'] != candidate['prompt_tokens']:
        raise ValueError('Baseline and candidate must use identical prompt token counts')
    runtime_lock = json.loads(runtime_lock_path.read_text(encoding='utf-8'))
    if not isinstance(runtime_lock.get('commit'), str) or not runtime_lock['commit']:
        raise ValueError('Runtime lock lacks a pinned commit')

    before, after = baseline['median'], candidate['median']
    return dict(
        format=1,
        complete=True,
        scope=('Repeated native smoke comparison on one Android emulator, with the same app APK, '
               'instrumentation APK, fixed prompt and pinned llama.cpp runtime. This checks '
               'basic model compatibility and latency only; it is not RAG quality, phone performance, '
               'or a production-model selection.'),
        runtime=dict(commit=runtime_lock['commit'], lock_sha256=sha(runtime_lock_path)),
        device_fingerprint=baseline['device_fingerprint'],
        application=dict(apk_sha256=baseline['apk_sha256'],
                         test_apk_sha256=baseline['test_apk_sha256']),
        baseline=baseline,
        candidate=candidate,
        candidate_vs_baseline_percent=dict(
            total_latency=100 * (after['total_ms'] / before['total_ms'] - 1),
            first_token_latency=100 * (after['first_token_ms'] / before['first_token_ms'] - 1),
            decode_tokens_per_second=100 * (after['decode_tokens_per_second'] /
                                            before['decode_tokens_per_second'] - 1)),
        interpretation=('A lower total/first-token latency or higher decode rate is favorable in this '
                        'probe only. It measures app/runtime behavior, not generated-content quality.'),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-audit', type=Path, nargs='+', required=True)
    parser.add_argument('--candidate-audit', type=Path, nargs='+', required=True)
    parser.add_argument('--runtime-lock', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = compare(args.baseline_audit, args.candidate_audit, args.runtime_lock)
    report['inputs'] = dict(
        baseline_audits=[dict(file=path.as_posix(), sha256=sha(path)) for path in args.baseline_audit],
        candidate_audits=[dict(file=path.as_posix(), sha256=sha(path)) for path in args.candidate_audit],
        runtime_lock=dict(file=args.runtime_lock.as_posix(), sha256=sha(args.runtime_lock)),
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(json.dumps({'baseline_median': report['baseline']['median'],
                      'candidate_median': report['candidate']['median'],
                      'candidate_vs_baseline_percent': report['candidate_vs_baseline_percent']}, indent=2))


if __name__ == '__main__':
    main()
