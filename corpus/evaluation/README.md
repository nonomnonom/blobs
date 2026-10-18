# Evaluation tooling

This package contains reproducible checks for retrieval, answer structure, citation identity, package fixtures, and native model execution.

| Path | Purpose |
| --- | --- |
| `answers/` | Answer parsing, citation, structure, and support checks |
| `cases/` | Versioned evaluation questions and expected evidence constraints |
| `fixtures/` | Small deterministic inputs for tests and diagnostics |
| `locks/` | Exact model identity, source, size, hash, and license metadata |
| `native/` | Android staging and native-output audit helpers |
| `prompts/` | Versioned prompt experiments |
| `retrieval/` | Retrieval comparison and ranking tools |

Install the evaluation dependency group and run the test suite:

```powershell
$env:UV_CACHE_DIR = "$PWD/.cache/uv"
uv sync --locked --group evaluation
uv run --locked --group evaluation python -m unittest discover -s tests
```

Write local run output under `reports/`; that directory is ignored by Git. Do not overwrite a prior run. Record the model hash, prompt/template revision, sampler, runtime revision, input evidence, hardware/ABI, timings, and cancellation/timeout behavior with each result.

Mechanical checks can validate source-ID syntax, supplied-source bounds, retrieval ordering, and configured thresholds. They do not establish factual support by themselves. Review claim-to-source support, contradiction, insufficiency handling, and misleading citation placement before promoting a model or prompt.

For an Android native model smoke comparison, stage each locked GGUF with `native/stage_android_model.py`, run `NativeInferenceTest#realModelStreamsAndCancellationStopsNativeLoop` at least three times per model, and validate every raw run with `native/audit_native_smoke.py`. Then compare the audited reports:

```powershell
uv run --locked python -m evaluation.native.compare_native_smoke `
  --baseline-audit reports/runs/baseline-1-audit.json reports/runs/baseline-2-audit.json reports/runs/baseline-3-audit.json `
  --candidate-audit reports/runs/candidate-1-audit.json reports/runs/candidate-2-audit.json reports/runs/candidate-3-audit.json `
  --runtime-lock ..\android\third_party\llama.lock.json `
  --output reports/runs/native-model-comparison.json
```

The comparator requires identical app/test APK hashes, device fingerprint, and prompt token count. It reports median and range for model load, prefill, first token, total latency, and decode rate. Its fixed native smoke prompt is only a compatibility/latency probe; it does not rank grounded answer quality or predict phone performance. Keep broader RAG answer runs and source-level reviews separate.

Curated, reviewed evidence is kept under [`results/`](results/); exploratory runs stay in ignored `reports/`. The [2026-10-18 model smoke snapshot](results/native-smoke-models-2026-10-18/comparison.json) includes all six raw runs, staging receipts, model-lock wrappers, per-run audits, and the aggregate comparison so the numbers can be checked from the public tree.

For a controlled prompt comparison, `answers/compare_runs.py` requires complete runs with the same pinned model, runtime, sampler, questions, and retrieved evidence. It reports answer text, mechanical citation-ID validity, and host latency; it does not score factual support. Example:

```powershell
uv run --locked python -m evaluation.answers.compare_runs `
  --baseline reports/runs/baseline.json `
  --candidate reports/runs/candidate.json `
  --output reports/runs/prompt-comparison.json
```

The output path must be new so earlier evidence is preserved. Review every answer against its cited passages before treating a prompt change as a quality improvement. Host timing is not a proxy for Android device performance.

The [native answer-contract development result](results/native-answer-contract-development-2026-10-18.json) records five synthetic source-boundary cases and an empty-library control through `WorkController` on one Android 16 x86_64 emulator. These cases exercise citation identity, source-instruction resistance, conflicting records, missing causal evidence, special-token text, and the `NO_SOURCES` path. The included review rubrics and raw app reports make the small manual review inspectable; this is development evidence, not held-out accuracy, a frontier comparison, or a device-performance claim.

Rebuild the synthetic library with a fresh output directory:

```powershell
uv run --locked python -m evaluation.native.build_answer_contract_fixture `
  --output-dir work/answer-contract-next
```

`android/app/src/androidTest/kotlin/dev/blobs/app/NativeInferenceTest.kt` contains the manual Android path. First install matching debug and test APKs, run `prepareEvaluationDrop`, and push the locked raw model plus the generated `.blobs` fixture into `/sdcard/Android/data/dev.blobs.app.debug/files/eval/`. Then run `stageEvaluationFileFromAppExternalStorage` for each input with its exact byte count and SHA-256. It copies through Android's shell file descriptor into app-private storage, verifies the copy, and can delete only the staged external source when `deleteSourceAfterCopy=true`. Run `verifiedModelFileProducesSavedCitedAnswer` for each case and collect `files/answer-RUN_ID.json` with `adb shell run-as dev.blobs.app.debug cat ...`; run `emptyLibraryReturnsNoSourcesWithoutLoadingModel` for the no-evidence control. Use new run IDs and output names each time. The raw-GGUF answer test verifies and validates the model file but does not test model-package installation; this boundary is recorded in every report.
