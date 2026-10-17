"""Audit retained native model smoke evidence against its pinned model and APK identity."""
import argparse
import json
from pathlib import Path
import re

from blobs_corpus.extract import write_json
from ..retrieval.host_replay import sha


def load(path: Path):
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict):
        raise ValueError(f'Expected an object: {path}')
    return value


def audit(run_path: Path, staging_path: Path, lock_path: Path, installed_path: Path,
          app_apk_sha256: str, test_apk_sha256: str):
    run, staging, lock, installed = map(load, (run_path, staging_path, lock_path, installed_path))
    for value in (app_apk_sha256, test_apk_sha256):
        if not re.fullmatch(r'[a-f0-9]{64}', value):
            raise ValueError('APK hashes must be lowercase SHA-256 values')
    expected = lock.get('model', {})
    identity = (
        type(expected.get('bytes')) is int and expected['bytes'] > 0 and
        re.fullmatch(r'[a-f0-9]{64}', expected.get('sha256', '')) and
        run.get('model_bytes') == expected['bytes'] == staging.get('staged', {}).get('bytes') and
        run.get('model_sha256') == expected['sha256'] == staging.get('staged', {}).get('sha256')
    )
    answer = run.get('answer_metrics', {})
    cancellation = run.get('cancellation_metrics', {})
    failure = run.get('failure_metrics', {})
    checks = dict(
        inputs_complete=run.get('complete') is True and staging.get('complete') is True and
                        installed.get('complete') is True,
        exact_model_identity=bool(identity),
        installed_app_identity=installed.get('apk_sha256') == app_apk_sha256,
        generated_nonempty_answer=(run.get('answer_result') in {0, 2} and
                                   isinstance(run.get('raw_answer'), str) and
                                   bool(run['raw_answer'].strip()) and
                                   type(answer.get('prompt_tokens')) is int and answer['prompt_tokens'] > 0 and
                                   type(answer.get('output_tokens')) is int and answer['output_tokens'] > 0),
        qwen35_non_thinking_prefix=answer.get('non_thinking_prefix') is True,
        answer_stage_order=(type(answer.get('total_us')) is int and
                            type(answer.get('first_token_callback_us')) is int and
                            type(answer.get('prefill_us')) is int and
                            answer['total_us'] >= answer['first_token_callback_us'] >=
                            answer['prefill_us'] >= 0),
        exact_three_token_cancellation=(run.get('cancellation_result') == 1 and
                                        cancellation.get('output_tokens') == 3 and
                                        cancellation.get('non_thinking_prefix') is True),
        missing_model_stopped_before_prompt=(type(failure.get('total_us')) is int and
                                             failure['total_us'] >= 0 and
                                             failure.get('first_token_callback_us') == -1 and
                                             failure.get('output_tokens') == 0),
    )
    if not all(checks.values()):
        failed = ', '.join(key for key, value in checks.items() if not value)
        raise ValueError('Native smoke evidence failed: ' + failed)
    return dict(
        format=1,
        complete=True,
        scope=('Exact x86_64 emulator model compatibility, native generation, cancellation and '
               'failure-path evidence. This is not model-package import, broad answer quality, '
               'ARM execution or phone-performance evidence.'),
        inputs={name: dict(file=path.as_posix(), bytes=path.stat().st_size, sha256=sha(path))
                for name, path in {
                    'run': run_path, 'staging': staging_path, 'model_lock': lock_path,
                    'installed_app': installed_path,
                }.items()},
        application=dict(package=installed.get('package'), apk_sha256=app_apk_sha256,
                         test_apk_sha256=test_apk_sha256),
        model=dict(bytes=expected['bytes'], sha256=expected['sha256']),
        device_fingerprint=run.get('fingerprint'),
        checks=checks,
        observations=dict(
            raw_answer=run['raw_answer'],
            answer_metrics=answer,
            cancellation_metrics=cancellation,
            failure_metrics=failure,
        ),
        interpretation={
            'timing': 'Recorded stage durations describe this emulator run only.',
            'quality': 'One short supported answer proves execution, not research-answer reliability.',
            'packaging': 'The raw verified GGUF was staged directly; normal .blobs import remains separate.',
        },
        script_sha256=sha(Path(__file__)),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--staging', type=Path, required=True)
    parser.add_argument('--model-lock', type=Path, required=True)
    parser.add_argument('--installed-app', type=Path, required=True)
    parser.add_argument('--app-apk-sha256', required=True)
    parser.add_argument('--test-apk-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = audit(args.run, args.staging, args.model_lock, args.installed_app,
                   args.app_apk_sha256, args.test_apk_sha256)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(json.dumps(report['checks'], indent=2))


if __name__ == '__main__':
    main()
