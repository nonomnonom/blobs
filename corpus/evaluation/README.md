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

For a controlled prompt comparison, `answers/compare_runs.py` requires complete runs with the same pinned model, runtime, sampler, questions, and retrieved evidence. It reports answer text, mechanical citation-ID validity, and host latency; it does not score factual support. Example:

```powershell
uv run --locked python -m evaluation.answers.compare_runs `
  --baseline reports/runs/baseline.json `
  --candidate reports/runs/candidate.json `
  --output reports/runs/prompt-comparison.json
```

The output path must be new so earlier evidence is preserved. Review every answer against its cited passages before treating a prompt change as a quality improvement. Host timing is not a proxy for Android device performance.
