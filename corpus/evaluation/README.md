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
  --work-directory work/reports/travel-retrieval-smoke-v3 `
  --output evaluation/baselines/wikivoyage-en-2026-09.2-retrieval-smoke-v3-host.json
```

The output path is write-once. Give a new run a new output filename so the checked-in result remains reproducible evidence for its exact source and case-set hashes. Reports include expected-source recall, first expected-source rank, mean reciprocal rank, and host elapsed time; these measure source retrieval and do not score generated answers. In the October 19 policy update, the same six travel cases retained 6/6 expected-source recall, moved top-1 coverage from 3/6 to 4/6, and raised mean reciprocal rank from 0.700 to 0.783. Host median retrieval time moved from 170.2 ms to 72.8 ms; p95 moved from 400.8 ms to 226.5 ms on the recorded Windows host. The follow-up title-overlap result is [`v3`](baselines/wikivoyage-en-2026-09.2-retrieval-smoke-v3-host.json): 6/6 expected sources at rank 1, MRR 1.0, host median 99.8 ms and p95 251.8 ms. A matched Android 16 x86_64 emulator comparison is recorded in [`the title-ranking evidence`](evidence/android-retrieval-travel-title-ranking-x86_64-android16-2026-10-19.json): all three candidate runs reached 6/6 at rank 1 versus 4/6 baseline; the median of run medians rose from 70 ms to 77 ms and median run p95 from 247 ms to 262 ms. This modest absolute latency cost buys a clear source-ordering improvement on the measured cases. These timings describe only their named host/emulator; additional topic libraries should use their own package identity and retrieval cases while sharing the same schema and retrieval implementation.

The U.S. outdoor-safety package uses the same retrieval-only check:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/nps-outdoor-safety-2026-10-v1.blobs `
  --cases evaluation/cases/nps-outdoor-safety-retrieval-v1.json `
  --work-directory work/reports/nps-outdoor-safety-retrieval-v1 `
  --output evaluation/baselines/nps-outdoor-safety-2026-10.1-retrieval-v3-host.json
```

The U.S. household emergency-preparedness package uses its own versioned case set and the same retrieval-only check:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/ready-emergency-preparedness-2026-10-v1.blobs `
  --cases evaluation/cases/ready-emergency-preparedness-retrieval-v1.json `
  --work-directory work/reports/ready-emergency-preparedness-retrieval-v1 `
  --output evaluation/baselines/ready-emergency-preparedness-2026-10.1-retrieval-v2-host.json
```

The initial fixed set has eight questions and ten expected source-title matches. The recorded host run found all ten expected sources, with seven at rank 1 (reciprocal rank 0.817). This small set is a source-routing smoke test, not a broad relevance study or answer evaluation.

The camping-safety library has a separate eight-question retrieval smoke set across all four steps of the NPS camping-safety series:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/camping-safely-2026-10-v1.blobs `
  --cases evaluation/cases/camping-safety-retrieval-v1.json `
  --work-directory work/reports/camping-safety-retrieval-v1 `
  --output evaluation/baselines/camping-safety-2026-10.1-retrieval-v1-host.json
```

The recorded host smoke found all eight expected source titles, with seven at rank 1 (MRR 0.938); median host retrieval time was 1.332 ms. This measures source routing for this package and case set only, not generated answers or phone latency.

The backcountry-planning package has an eight-question set across its Ten Essentials, Trip Planning Guide, and Wilderness & Backcountry Camping sources:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/backcountry-basics-2026-10-v3.blobs `
  --cases evaluation/cases/backcountry-basics-retrieval-v3.json `
  --work-directory work/reports/backcountry-basics-retrieval-v3 `
  --output evaluation/baselines/backcountry-basics-2026-10.3-retrieval-v2-host.json
```

The Android 16 x86_64 emulator report for package v3 is [`backcountry-basics retrieval evidence`](evidence/android-retrieval-backcountry-basics-v3-x86_64-android16-2026-10-19.json). It exercises the production SQLite retrieval path five times per case: all eight expected sources were found, seven ranked first, with a 4 ms median of case medians and a 9 ms p95 on this emulator. Its case-set hash matches the host report. It records source ranks and elapsed time and does not evaluate generated answers.

For repeatable Android model-load, prefill, first-token, and decode measurements, use the [native inference smoke guide](native/README.md). It records device-specific performance only and never scores generated answers.

## Measure retrieval on an Android emulator

`LibraryRetrievalBenchmarkTest` imports the exact pinned package and versioned case set on-device, verifies their identities, then runs every query five times through the production `LibraryStore`. It reports source-title ranks and elapsed time for each query, together with case-set hash, emulator, app APK, test APK, and package identities. It never runs a model or records answer text. From the repository root with an Android emulator connected, stage whichever package and case set you want to measure:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb shell mkdir -p /sdcard/Android/data/dev.blobs.app.debug/files
& $adb push corpus/work/packages/ready-emergency-preparedness-2026-10-v1.blobs `
  /sdcard/Android/data/dev.blobs.app.debug/files/retrieval-benchmark-package.blobs
& $adb push corpus/evaluation/cases/ready-emergency-preparedness-retrieval-v1.json `
  /sdcard/Android/data/dev.blobs.app.debug/files/retrieval-benchmark-cases.json
Push-Location android
.\gradlew.bat `
  '-Pandroid.testInstrumentationRunnerArguments.class=dev.blobs.app.LibraryRetrievalBenchmarkTest' `
  :app:connectedDebugAndroidTest --offline --console=plain
Pop-Location
& $adb logcat -d -s BlobsRetrievalBench:I
```

The complete report is emitted as ordered `REPORT_PART:i/n:` chunks under the `BlobsRetrievalBench` log tag, followed by a concise summary. Concatenate the chunk contents in order to reconstruct the JSON before recording evidence; Android's log buffer truncates a single long message. The recorded Ready.gov package run is in [`evidence/android-retrieval-ready-emergency-preparedness-x86_64-android16-2026-10-19.json`](evidence/android-retrieval-ready-emergency-preparedness-x86_64-android16-2026-10-19.json): eight queries, five repetitions, ten of ten expected sources found, median per-case median 3 ms, and p95 6 ms on that emulator. These timings describe only that emulator; use the same package and case set for before-and-after comparisons. Host smoke timings are not device-performance proxies.

The pinned travel package was also compared before and after stable title-overlap ranking on the same Android 16 x86_64 emulator, using three runs per build and the same six-query case set. [`The comparison evidence`](evidence/android-retrieval-travel-title-ranking-x86_64-android16-2026-10-19.json) records 6/6 expected sources at rank 1 in all candidate runs, up from 4/6 at baseline; mean reciprocal rank rose from 0.783 to 1.0. The median of run medians increased from 70 ms to 78 ms, and the median run p95 from 247 ms to 261 ms. That is a small absolute latency cost on this emulator in exchange for better source ordering; it is not a device-wide performance claim.

The new camping-safety package was exercised through the same Android 16 x86_64 production retrieval path. [`Its report`](evidence/android-retrieval-camping-safety-x86_64-android16-2026-10-19.json) records five repeats for each of eight queries: all eight expected sources were found, seven at rank 1, with a 3 ms median of case medians and a 6 ms p95 on this emulator. The report identifies the exact package, app APK, test APK, and case-set hashes. It evaluates retrieval only.
