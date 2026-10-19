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


def audit(run_path: Path, staging_path: Path | None, lock_path: Path, installed_path: Path,
          app_apk_sha256: str, test_apk_sha256: str,
          package_install_path: Path | None = None):
    if (staging_path is None) == (package_install_path is None):
        raise ValueError('Provide exactly one model staging or in-app installation report')
    run, lock, installed = map(load, (run_path, lock_path, installed_path))
    staging = load(staging_path) if staging_path else {}
    package_install = load(package_install_path) if package_install_path else {}
    for value in (app_apk_sha256, test_apk_sha256):
        if not re.fullmatch(r'[a-f0-9]{64}', value):
            raise ValueError('APK hashes must be lowercase SHA-256 values')
    expected = lock.get('model', {})
    runtime = lock.get('runtime', {})
    runtime_commit = runtime.get('revision') or runtime.get('commit')
    workload = run.get('workload')
    locked_workload = lock.get('workload')
    profile = run.get('device_profile')
    staged_device = staging.get('device', {})
    if package_install_path:
        profile_valid = (
            isinstance(profile, dict) and
            profile.get('fingerprint') == run.get('fingerprint') ==
            package_install.get('device_fingerprint') and
            profile.get('model') == package_install.get('device_model') and
            profile.get('api') == package_install.get('api') and
            all(isinstance(profile.get(field), str) and profile[field]
                for field in ('model', 'device', 'abi')) and
            type(profile.get('api')) is int and profile['api'] >= 28 and
            type(profile.get('available_processors')) is int and profile['available_processors'] > 0 and
            type(profile.get('max_heap_bytes')) is int and profile['max_heap_bytes'] > 0
        )
    else:
        profile_valid = (
            isinstance(profile, dict) and
            profile.get('fingerprint') == run.get('fingerprint') and
            profile.get('fingerprint') == staged_device.get('fingerprint') and
            all(isinstance(profile.get(field), str) and profile[field]
                for field in ('model', 'device', 'abi')) and
            profile.get('abi') == staged_device.get('abi') and
            isinstance(staging.get('serial'), str) and bool(staging['serial']) and
            type(profile.get('api')) is int and profile['api'] >= 28 and
            type(profile.get('available_processors')) is int and profile['available_processors'] > 0 and
            type(profile.get('max_heap_bytes')) is int and profile['max_heap_bytes'] > 0
        )
    workload_valid = (
        isinstance(workload, dict) and
        workload.get('id') == 'native-inference-smoke-v1' and
        workload == locked_workload and
        all(re.fullmatch(r'[a-f0-9]{64}', workload.get(field, ''))
            for field in ('system_sha256', 'user_sha256')) and
        type(workload.get('max_output_tokens')) is int and
        1 <= workload['max_output_tokens'] <= 768
    )
    if package_install_path:
        model_identity = (
            package_install.get('complete') is True and
            package_install.get('kind') == 'model' and
            package_install.get('package_id') == expected.get('id') and
            package_install.get('installed_bytes') == expected.get('bytes') and
            package_install.get('installed_sha256') == expected.get('sha256')
        )
    else:
        model_identity = (
            run.get('model_bytes') == expected.get('bytes') ==
            staging.get('staged', {}).get('bytes') and
            run.get('model_sha256') == expected.get('sha256') ==
            staging.get('staged', {}).get('sha256')
        )
    identity = (
        type(expected.get('bytes')) is int and expected['bytes'] > 0 and
        re.fullmatch(r'[a-f0-9]{64}', expected.get('sha256', '')) and
        run.get('model_bytes') == expected['bytes'] and
        run.get('model_sha256') == expected['sha256'] and bool(model_identity)
    )
    inference = run.get('inference_metrics', {})
    if not isinstance(inference, dict):
        inference = {}
    profile_values = profile if isinstance(profile, dict) else {}
    thread_mode = run.get('thread_mode', 'adaptive')
    requested_threads = run.get('requested_inference_threads')
    if thread_mode == 'adaptive':
        thread_selection_valid = (
            requested_threads is None and
            type(inference.get('inference_threads')) is int and
            inference['inference_threads'] == min(max(profile_values.get('available_processors', 0), 1), 4)
        )
    elif thread_mode == 'fixed':
        thread_selection_valid = (
            type(requested_threads) is int and 1 <= requested_threads <= 4 and
            type(inference.get('inference_threads')) is int and
            inference['inference_threads'] == requested_threads
        )
    else:
        thread_selection_valid = False
    cancellation = run.get('cancellation_metrics', {})
    failure = run.get('failure_metrics', {})
    warm_inference = run.get('warm_inference_metrics')
    reload_inference = run.get('reload_inference_metrics')
    cache_run_present = warm_inference is not None or reload_inference is not None
    cache_lifecycle_valid = not cache_run_present or (
        isinstance(warm_inference, dict) and isinstance(reload_inference, dict) and
        inference.get('model_cache_hit') is False and
        warm_inference.get('model_cache_hit') is True and
        warm_inference.get('model_load_us') == 0 and
        reload_inference.get('model_cache_hit') is False and
        type(reload_inference.get('model_load_us')) is int and
        reload_inference['model_load_us'] > 0 and
        warm_inference.get('prompt_tokens') == inference.get('prompt_tokens') and
        reload_inference.get('prompt_tokens') == inference.get('prompt_tokens') and
        warm_inference.get('output_tokens') == inference.get('output_tokens') and
        reload_inference.get('output_tokens') == inference.get('output_tokens') and
        run.get('warm_inference_result') in {0, 2} and
        run.get('reload_inference_result') in {0, 2} and
        cancellation.get('model_cache_hit') is True and
        failure.get('model_cache_hit') is False
    )
    context_tokens = inference.get('context_tokens')
    context_capacity_valid = (
        context_tokens is None or (
            type(context_tokens) is int and
            context_tokens in {512, 1024, 2048, 4096} and
            type(inference.get('prompt_tokens')) is int and
            type(workload.get('max_output_tokens')) is int and
            context_tokens >= inference['prompt_tokens'] + workload['max_output_tokens']
        )
    )
    checks = dict(
        inputs_complete=(run.get('complete') is True and
                         (staging.get('complete') is True if staging_path else
                          package_install.get('complete') is True) and
                         installed.get('complete') is True),
        exact_model_identity=bool(identity),
        device_profile_complete=bool(profile_valid),
        pinned_runtime=bool(re.fullmatch(r'[a-f0-9]{40}', runtime_commit or '')),
        fixed_workload=bool(workload_valid),
        installed_build_identity=(installed.get('apk_sha256') == app_apk_sha256 and
                                  installed.get('test_apk_sha256') == test_apk_sha256 and
                                  (not package_install_path or
                                   (package_install.get('app_apk_sha256') == app_apk_sha256 and
                                    package_install.get('test_apk_sha256') == test_apk_sha256))),
        inference_completed=run.get('inference_result') in {0, 2},
        inference_metrics_present=(type(inference.get('prompt_tokens')) is int and
                                   inference['prompt_tokens'] > 0 and
                                   type(inference.get('output_tokens')) is int and
                                   inference['output_tokens'] > 0),
        model_cache_lifecycle_valid=bool(cache_lifecycle_valid),
        context_capacity_sufficient=bool(context_capacity_valid),
        thread_selection_valid=thread_selection_valid,
        inference_stage_order=(type(inference.get('total_us')) is int and
                               type(inference.get('first_token_callback_us')) is int and
                               type(inference.get('prefill_us')) is int and
                               inference['total_us'] >= inference['first_token_callback_us'] >=
                               inference['prefill_us'] >= 0),
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
        scope=('Identified Android model compatibility, native inference timing, adaptive thread '
               'selection, cancellation and failure-path evidence. This is not a phone-performance '
               'prediction or generated-answer quality evaluation.'),
        inputs={name: dict(file=path.as_posix(), bytes=path.stat().st_size, sha256=sha(path))
                for name, path in {
                    **({'run': run_path, 'staging': staging_path} if staging_path else
                       {'run': run_path, 'package_install': package_install_path}),
                    'model_lock': lock_path, 'installed_app': installed_path,
                }.items()},
        application=dict(package=installed.get('package'), apk_sha256=app_apk_sha256,
                         test_apk_sha256=test_apk_sha256),
        model=dict(bytes=expected['bytes'], sha256=expected['sha256']),
        runtime_commit=runtime_commit,
        workload=workload,
        thread_mode=thread_mode,
        requested_inference_threads=requested_threads,
        device_fingerprint=run.get('fingerprint'),
        device_profile=(dict(profile, serial=staging.get('serial')) if staging_path else profile)
        if profile_valid else None,
        checks=checks,
        observations=dict(
            inference_metrics=inference,
            warm_inference_metrics=warm_inference,
            reload_inference_metrics=reload_inference,
            cancellation_metrics=cancellation,
            failure_metrics=failure,
        ),
        interpretation={
            'timing': 'Recorded stage durations describe this emulator run only.',
            'model_source': ('Installed through the signed in-app package list.'
                             if package_install_path else
                             'Raw verified model staged as a test fixture.'),
        },
        script_sha256=sha(Path(__file__)),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--staging', type=Path)
    source.add_argument('--package-install', type=Path)
    parser.add_argument('--model-lock', type=Path, required=True)
    parser.add_argument('--installed-app', type=Path, required=True)
    parser.add_argument('--app-apk-sha256', required=True)
    parser.add_argument('--test-apk-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = audit(args.run, args.staging, args.model_lock, args.installed_app,
                   args.app_apk_sha256, args.test_apk_sha256, args.package_install)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(json.dumps(report['checks'], indent=2))


if __name__ == '__main__':
    main()
