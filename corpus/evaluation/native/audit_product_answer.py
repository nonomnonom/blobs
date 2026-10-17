"""Audit a retained native product-path answer without claiming semantic correctness."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re

from blobs_corpus.extract import write_json
from blobs_corpus.package import verify_package
from ..retrieval.host_replay import sha


def load(path: Path):
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict):
        raise ValueError(f'Expected an object: {path}')
    return value


def audit(run_path: Path, failure_path: Path, staging_path: Path, cleanup_path: Path,
          lock_path: Path, library_package: Path, installed_path: Path,
          app_apk_sha256: str, test_apk_sha256: str):
    run, failure, staging, cleanup, lock, installed = map(load, (
        run_path, failure_path, staging_path, cleanup_path, lock_path, installed_path))
    for value in (app_apk_sha256, test_apk_sha256):
        if not re.fullmatch(r'[a-f0-9]{64}', value):
            raise ValueError('APK hashes must be lowercase SHA-256 values')
    model = lock.get('model', {})
    library = verify_package(library_package)
    try:
        evidence = json.loads(run.get('evidence', ''))
    except json.JSONDecodeError as error:
        raise ValueError('Saved evidence is not JSON') from error
    if not isinstance(evidence, list) or not 1 <= len(evidence) <= 6 or any(
        not isinstance(item, dict) or not isinstance(item.get('text'), str) or
        not item['text'].strip() or not isinstance(item.get('title'), str)
        for item in evidence
    ):
        raise ValueError('Saved evidence does not match the bounded product contract')
    answer = run.get('answer')
    if not isinstance(answer, str) or not answer.strip():
        raise ValueError('Saved answer is empty')
    references = [int(value) for value in re.findall(r'\[(\d+)]', answer)]
    checks = dict(
        run_completed=(run.get('complete') is True and run.get('generation_started') is True and
                       run.get('finished_within_timeout') is True and run.get('status') == 'COMPLETE' and
                       run.get('error') == ''),
        exact_model_identity=(staging.get('complete') is True and
                              run.get('model_file_bytes') == model.get('bytes') ==
                              staging.get('staged', {}).get('bytes') and
                              run.get('model_sha256') == model.get('sha256') ==
                              staging.get('staged', {}).get('sha256')),
        exact_library_identity=(run.get('library_manifest') == library and
                                run.get('library_sha256') == library.get('sha256')),
        installed_app_identity=(installed.get('complete') is True and
                                installed.get('apk_sha256') == app_apk_sha256),
        citations_present_and_in_range=bool(references) and all(1 <= value <= len(evidence)
                                                               for value in references),
        bounded_evidence=len(evidence) <= 6,
        persisted_timing_order=(type(run.get('first_visible_state_ms')) is int and
                                type(run.get('saved_elapsed_ms')) is int and
                                type(run.get('wall_elapsed_ms')) is int and
                                0 < run['first_visible_state_ms'] <= run['saved_elapsed_ms'] <=
                                run['wall_elapsed_ms']),
        storage_refusal_preserved=(failure.get('complete') is False and
                                   failure.get('generation_started') is False and
                                   failure.get('status') == 'PREPARATION_FAILED' and
                                   failure.get('error') == 'java.lang.IllegalStateException: STORAGE'),
        staged_inputs_removed=(cleanup.get('complete') is True and
                               all(cleanup.get('targets_absent', {}).values())),
    )
    if not all(checks.values()):
        failed = ', '.join(key for key, value in checks.items() if not value)
        raise ValueError('Product-answer evidence failed: ' + failed)
    titles = [item['title'] for item in evidence]
    return dict(
        format=1,
        complete=True,
        scope=('Mechanical audit of one native retrieval-to-persisted-answer run and one storage '
               'refusal. Citation range does not establish claim support; source-level review remains required.'),
        inputs={name: dict(file=path.as_posix(), bytes=path.stat().st_size, sha256=sha(path))
                for name, path in {
                    'run': run_path, 'storage_failure': failure_path, 'staging': staging_path,
                    'cleanup': cleanup_path, 'model_lock': lock_path,
                    'library_package': library_package, 'installed_app': installed_path,
                }.items()},
        application=dict(package=run.get('application_id'), apk_sha256=app_apk_sha256,
                         test_apk_sha256=test_apk_sha256),
        model=dict(bytes=model['bytes'], sha256=model['sha256']),
        library=library,
        checks=checks,
        observations=dict(
            question=run.get('question'), status=run.get('status'),
            answer_characters=len(answer), evidence_passages=len(evidence),
            evidence_titles=titles, evidence_title_counts=dict(Counter(titles)),
            citation_occurrences=references, unique_citations=sorted(set(references)),
            first_visible_state_ms=run['first_visible_state_ms'],
            saved_elapsed_ms=run['saved_elapsed_ms'], wall_elapsed_ms=run['wall_elapsed_ms'],
        ),
        interpretation={
            'quality': 'Read the retained answer and each saved passage; this audit does not score factual support.',
            'timing': 'One x86_64 emulator observation; not a phone prediction or latency distribution.',
            'storage': 'The failed first attempt proves the production allocatable-space guard remained active.',
            'packaging': 'The model was a verified raw fixture; normal model-package import was not exercised.',
        },
        script_sha256=sha(Path(__file__)),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--storage-failure', type=Path, required=True)
    parser.add_argument('--staging', type=Path, required=True)
    parser.add_argument('--cleanup', type=Path, required=True)
    parser.add_argument('--model-lock', type=Path, required=True)
    parser.add_argument('--library-package', type=Path, required=True)
    parser.add_argument('--installed-app', type=Path, required=True)
    parser.add_argument('--app-apk-sha256', required=True)
    parser.add_argument('--test-apk-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = audit(args.run, args.storage_failure, args.staging, args.cleanup,
                   args.model_lock, args.library_package, args.installed_app,
                   args.app_apk_sha256, args.test_apk_sha256)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(json.dumps(report['checks'], indent=2))


if __name__ == '__main__':
    main()
