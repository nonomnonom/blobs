# Evaluation tools

These optional Python tools measure package retrieval and pinned host inference. They are not part of the Android runtime. Host latency is a reproducible implementation diagnostic, not an estimate of phone speed.

Install the locked evaluation dependencies and run the corpus tests from this directory's parent:

```powershell
uv sync --locked --group evaluation
uv run --locked --project . python -m unittest discover -s tests
```

## Reproduce the v0.3.3 host baseline

The baseline uses the exact Qwen3 0.6B model and starter library attached to the [v0.3.3 GitHub release](https://github.com/nonomnonom/blobs/releases/tag/v0.3.3), the versioned 40-case set, and the llama.cpp build pinned in `locks/host-runtime.lock.json`. Download that runtime archive and verify its byte length and SHA-256 against the lock before extracting `llama-server.exe`.

Run from `corpus/` in PowerShell. `reports/` is ignored, so generated raw outputs stay local:

```powershell
uv run --locked --project . python -m evaluation.retrieval.package_report `
  --library-package ../android/release/0.3.3/wikipedia-starter-schema3.blobs `
  --cases evaluation/cases/answer-validation-v2-40.json `
  --work-directory reports/work/v0.3.3 `
  --output reports/runs/v0.3.3-retrieval.json

$caseSet = Get-Content evaluation/cases/answer-validation-v2-40.json -Raw | ConvertFrom-Json
$caseIds = @($caseSet.cases.id)
uv run --locked --project . python -m evaluation.answers.host_answers `
  --server PATH_TO_PINNED_LLAMA_SERVER `
  --model-package ../android/release/0.3.3/qwen3-integration.blobs `
  --work-directory reports/work/v0.3.3 `
  --model-sha256 9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031 `
  --retrieval-report reports/runs/v0.3.3-retrieval.json `
  --cases $caseIds `
  --sampling-profile product --namespaced-citations `
  --output reports/runs/v0.3.3-host-run.json

uv run --locked --project . python -m evaluation.answers.benchmark_summary `
  --run reports/runs/v0.3.3-host-run.json `
  --cases evaluation/cases/answer-validation-v2-40.json `
  --output evaluation/baselines/v0.3.3-qwen3-0.6b-starter-answer-v2-40-host.json
```

The checked-in baseline keeps per-case host elapsed time, first-content time, model prefill and generation time/rate, prompt size, source count, and execution status. It omits generated answers and passages. It does not score answer quality. The raw run remains in ignored `reports/runs/`; the baseline records its SHA-256 and all model, runtime, case, retrieval, and prompt hashes needed to identify the exact experiment. Re-run into a new output path; tools refuse to overwrite existing evidence.

Evaluation cases and model/runtime locks are versioned. A new run is a development measurement once inspected or used to change implementation; do not describe it as a held-out quality set.

## Reproduce travel-library retrieval smoke

This checks whether a fixed set of travel questions retrieves their expected source documents from the exact Wikivoyage package. It records titles and host retrieval time only; it neither stores passages nor grades model answers. The result is a host diagnostic, not a prediction of phone latency. Run from `corpus/`:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/wikivoyage-en-2026-09-v2.blobs `
  --cases evaluation/cases/travel-retrieval-smoke-v1.json `
  --work-directory work/reports/travel-retrieval-smoke-v1 `
  --output evaluation/baselines/wikivoyage-en-2026-09.2-retrieval-smoke-v1-host.json
```

The output path is write-once. Give a new run a new output filename so the checked-in result remains reproducible evidence for its exact source and case-set hashes. Reports include expected-source recall, first expected-source rank, mean reciprocal rank, and host elapsed time; these measure source retrieval and do not score generated answers. In the October 19 policy update, the same six travel cases retained 6/6 expected-source recall, moved top-1 coverage from 3/6 to 4/6, and raised mean reciprocal rank from 0.700 to 0.783. Host median retrieval time moved from 170.2 ms to 72.8 ms; p95 moved from 400.8 ms to 226.5 ms on the recorded Windows host. These are single-run host diagnostics, not phone-latency claims; the separate emulator run records five repetitions per query. Additional topic libraries should use their own package identity and topic-specific retrieval cases; they can share the same schema and retrieval implementation while keeping source licensing and update cadence independent.

The U.S. outdoor-safety package uses the same retrieval-only check:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/nps-outdoor-safety-2026-10-v1.blobs `
  --cases evaluation/cases/nps-outdoor-safety-retrieval-v1.json `
  --work-directory work/reports/nps-outdoor-safety-retrieval-v1 `
  --output evaluation/baselines/nps-outdoor-safety-2026-10.1-retrieval-v1-host.json
```

For repeatable Android model-load, prefill, first-token, and decode measurements, use the [native inference smoke guide](native/README.md). It records device-specific performance only and never scores generated answers.

## Measure retrieval on an Android emulator

`LibraryRetrievalBenchmarkTest` imports the exact pinned Travel package on-device, verifies its library payload SHA-256, then runs six fixed queries five times through the production `LibraryStore`. It reports source-title rank and elapsed time for each query, together with emulator, app APK, test APK, and package identities. It never runs a model or records answer text. From the repository root with an Android 16 x86_64 emulator connected:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb shell mkdir -p /sdcard/Android/data/dev.blobs.app.debug/files
& $adb push corpus/work/packages/wikivoyage-en-2026-09-v2.blobs `
  /sdcard/Android/data/dev.blobs.app.debug/files/retrieval-benchmark-package.blobs
Push-Location android
.\gradlew.bat `
  '-Pandroid.testInstrumentationRunnerArguments.class=dev.blobs.app.LibraryRetrievalBenchmarkTest' `
  '-Pandroid.testInstrumentationRunnerArguments.retrievalPackage=/sdcard/Android/data/dev.blobs.app.debug/files/retrieval-benchmark-package.blobs' `
  :app:connectedDebugAndroidTest --offline --console=plain
Pop-Location
& $adb logcat -d -s BlobsRetrievalBench:I
```

The structured report is emitted under the `BlobsRetrievalBench` log tag. Performance numbers describe only the recorded emulator; use the same package and case set for before-and-after comparisons. The v1/v2 host smoke results remain useful for repeatable host retrieval diagnostics and are not device-performance proxies.
