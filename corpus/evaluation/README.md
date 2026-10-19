# Evaluation tools

These optional Python tools measure Blobs app/runtime performance and corpus retrieval behavior. They are not part of the Android runtime and do not grade model-generated answers. Host latency is a reproducible implementation diagnostic, not an estimate of phone speed.

## Measure installed-app launch and memory

`evaluation.android.app_startup` drives the installed Android app through `adb`, recording process-cold activity launch timing, process PSS, device fingerprint, installed APK SHA-256, and version. Each run force-stops the app before launch; the OS page cache remains warm, so this is not a first-install or device-cold measurement. The report contains raw samples and median/nearest-rank p95, and refuses to overwrite existing evidence.

Run from `corpus/` with the app installed on the selected emulator or phone:

```powershell
uv run --locked --project . python -m evaluation.android.app_startup `
  --adb "$env:ANDROID_HOME/platform-tools/adb.exe" `
  --serial emulator-5554 `
  --package dev.blobs.app `
  --runs 7 `
  --output reports/android-startup-local.json
```

The v0.3.8 release APK measured on Android 16 x86_64 had 682 ms median activity launch time (753 ms nearest-rank p95) and 31,680 KiB median process PSS (33,753 KiB p95) across seven process-cold launches. The v0.3.7 run on the same emulator measured 684 ms median (739 ms p95) and 31,955 KiB median PSS (33,107 KiB p95). These small repeated emulator samples show similar startup and memory use across the two releases; they are not device-wide estimates. The installed APK digests and raw samples are in [`the v0.3.8 report`](baselines/android-startup-v0.3.8-x86_64-android16-2026-10-19.json) and [`the v0.3.7 report`](baselines/android-startup-v0.3.7-x86_64-android16-2026-10-19.json).

## Measure in-app model and library downloads

`PackageLifecyclePerformanceTest` starts the normal `ProductViewModel` download action, then records catalog refresh, download plus archive verification/inspection, and package install/validation/activation separately. It downloads only from the signature-verified app package list, checks the final database entry and SHA-256, and records APK/package/device identities. This is a manual Android instrumentation test because it needs network access and a package that is not already installed in the debug app. Use a fresh debug app install or a package ID that has not been installed; it does not clear app data.

From `android/`, build and install the debug app and instrumentation APK, then run a package from the signed list:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$runId = 'qwen-q8-01'
& $adb -s emulator-5554 shell am instrument -w `
  -e class 'dev.blobs.app.PackageLifecyclePerformanceTest#downloadsAndInstallsPinnedCatalogPackage' `
  -e packageId qwen3-06b-q8 `
  -e runId $runId `
  dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner
$report = & $adb -s emulator-5554 exec-out run-as dev.blobs.app.debug cat "files/package-lifecycle-$runId.json"
$report | Set-Content -Encoding utf8 "../corpus/evaluation/evidence/package-lifecycle-qwen-$runId.json"
```

Pull `files/package-lifecycle-$runId.json` from `dev.blobs.app.debug` with `adb exec-out run-as` and retain it under `evaluation/evidence/`. On the recorded Android 16 x86_64 emulator, the 3.83 MB Wikipedia starter package took 2,796 ms for download and inspection, then 514 ms to install and validate (3,309 ms total). The 639.45 MB Qwen 0.6B package took 95,207 ms for download and inspection, then 8,007 ms to install and validate (103,213 ms total, about 6.7 MB/s over that emulator's network). These are single-run network diagnostics; package identity, archive and installed hashes are in [`the Qwen report`](evidence/android-package-lifecycle-qwen-q8-x86_64-android16-2026-10-19.json) and [`the starter-library report`](evidence/android-package-lifecycle-starter-x86_64-android16-2026-10-19.json).

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
  --output evaluation/baselines/wikivoyage-en-2026-09.2-retrieval-smoke-v6-host.json
```

The output path is write-once. Give a new run a new output filename so the checked-in result remains reproducible evidence for its exact source and case-set hashes. Reports include expected-source recall, first expected-source rank, mean reciprocal rank, and host elapsed time; these measure source retrieval and do not score generated answers. In the October 19 policy update, the same six travel cases retained 6/6 expected-source recall, moved top-1 coverage from 3/6 to 4/6, and raised mean reciprocal rank from 0.700 to 0.783. Host median retrieval time moved from 170.2 ms to 72.8 ms; p95 moved from 400.8 ms to 226.5 ms on the recorded Windows host. The follow-up title-overlap result is [`v3`](baselines/wikivoyage-en-2026-09.2-retrieval-smoke-v3-host.json): 6/6 expected sources at rank 1, MRR 1.0, host median 99.8 ms and p95 251.8 ms. A matched Android 16 x86_64 emulator comparison is recorded in [`the title-ranking evidence`](evidence/android-retrieval-travel-title-ranking-x86_64-android16-2026-10-19.json): all three candidate runs reached 6/6 at rank 1 versus 4/6 baseline; the median of run medians rose from 70 ms to 77 ms and median run p95 from 247 ms to 262 ms. This modest absolute latency cost buys a clear source-ordering improvement on the measured cases. These timings describe only their named host/emulator; additional topic libraries should use their own package identity and retrieval cases while sharing the same schema and retrieval implementation.
The same six travel cases still find all expected sources under the current query policy; the latest host report is [`v5`](baselines/wikivoyage-en-2026-09.2-retrieval-smoke-v5-host.json), with all six at rank 1 (MRR 1.0). Host median was 83.3 ms and p95 was 234.9 ms on the recorded Windows machine; these timings are diagnostic, not an Android latency estimate.

The U.S. outdoor-safety package uses the same retrieval-only check:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/nps-outdoor-safety-2026-10-v1.blobs `
  --cases evaluation/cases/nps-outdoor-safety-retrieval-v1.json `
  --work-directory work/reports/nps-outdoor-safety-retrieval-v1 `
  --output evaluation/baselines/nps-outdoor-safety-2026-10.1-retrieval-v6-host.json
```

The latest NPS outdoor-safety report is [`v5`](baselines/nps-outdoor-safety-2026-10.1-retrieval-v5-host.json): all five expected documents ranked first (MRR 1.0), with a 1.95 ms host median and 2.43 ms p95. This small retrieval smoke is about source selection, not field-safety validation.

The U.S. household emergency-preparedness package uses its own versioned case set and the same retrieval-only check:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/ready-emergency-preparedness-2026-10-v1.blobs `
  --cases evaluation/cases/ready-emergency-preparedness-retrieval-v1.json `
  --work-directory work/reports/ready-emergency-preparedness-retrieval-v1 `
  --output evaluation/baselines/ready-emergency-preparedness-2026-10.1-retrieval-v5-host.json
```

The initial fixed set has eight questions and ten expected source-title matches. The recorded host run found all ten expected sources, with seven at rank 1 (reciprocal rank 0.817). This small set is a source-routing smoke test, not a broad relevance study or answer evaluation.

The latest host report is [`v4`](baselines/ready-emergency-preparedness-2026-10.1-retrieval-v4-host.json), with 10/10 expected sources found and seven ranked first (MRR 0.817). Median host retrieval was 1.57 ms and p95 2.03 ms.

The camping-safety library has a separate eight-question retrieval smoke set across all four steps of the NPS camping-safety series:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/camping-safely-2026-10-v1.blobs `
  --cases evaluation/cases/camping-safety-retrieval-v1.json `
  --work-directory work/reports/camping-safety-retrieval-v1 `
  --output evaluation/baselines/camping-safety-2026-10.1-retrieval-v4-host.json
```

The latest host smoke report is [`v3`](baselines/camping-safety-2026-10.1-retrieval-v3-host.json): all eight expected source titles found, seven at rank 1 (MRR 0.938), with 1.71 ms median and 2.44 ms p95. This measures source routing for this package and case set only, not generated answers or phone latency.

The backcountry-planning package has an eight-question set across its Ten Essentials, Trip Planning Guide, and Wilderness & Backcountry Camping sources:

```powershell
uv run --locked --project . python -m evaluation.retrieval.retrieval_smoke `
  --library-package work/packages/backcountry-basics-2026-10-v3.blobs `
  --cases evaluation/cases/backcountry-basics-retrieval-v3.json `
  --work-directory work/reports/backcountry-basics-retrieval-v3 `
  --output evaluation/baselines/backcountry-basics-2026-10.3-retrieval-v5-host.json
```

The Android 16 x86_64 emulator report for package v3 before the query cleanup is [`the prior retrieval run`](evidence/android-retrieval-backcountry-basics-v3-x86_64-android16-2026-10-19.json). After treating generic “guide” and “list” wording as stop words, [`the candidate run`](evidence/android-retrieval-backcountry-basics-v3-query-stopwords-x86_64-android16-2026-10-19.json) found all eight expected sources and ranked all eight first, up from seven at rank 1. Median of case medians remained 4 ms; p95 moved from 9 ms to 8 ms on this emulator. Both reports use identical case-set and package hashes, exercise the production SQLite retrieval path five times per query, and do not evaluate generated answers.

The latest host replay reports for backcountry, Wikivoyage, outdoor safety, emergency preparedness, and camping are [`backcountry v4`](baselines/backcountry-basics-2026-10.3-retrieval-v4-host.json), [`travel v5`](baselines/wikivoyage-en-2026-09.2-retrieval-smoke-v5-host.json), [`outdoor safety v2`](baselines/nps-outdoor-safety-2026-10.2-retrieval-v1-host.json), [`emergency preparedness v4`](baselines/ready-emergency-preparedness-2026-10.1-retrieval-v4-host.json), and [`camping v3`](baselines/camping-safety-2026-10.1-retrieval-v3-host.json). Expected-source recall is 100% in each fixed case set; rank-1 coverage is 8/8, 6/6, 8/8, 7/10, and 7/8 respectively. These reports measure source retrieval only. The outdoor-safety v2 case set adds natural-water hazards, currents, and life-jacket/water-watcher retrieval cases.

For repeatable Android model-load, prefill, first-token, and decode measurements, use the [native inference smoke guide](native/README.md). It records device-specific performance only and never scores generated answers.

## Measure retrieval on an Android emulator

### Measure retrieval as libraries accumulate

`MultiLibraryRetrievalScalingTest` exercises production retrieval against the starter library, four optional outdoor and emergency libraries, and the full Wikivoyage travel package. It verifies every pinned payload, then runs six fixed queries against progressively larger enabled-library sets (one through six), warming each query and taking five timed samples. It records source-title rank and local retrieval latency only; it does not run or grade a model. The package list and expected source identities are pinned in [`multi-library-retrieval-scaling-v3.json`](cases/multi-library-retrieval-scaling-v3.json). The travel package's SQLite payload is about 628 MiB, so this benchmark also exercises a materially larger library than the compact safety packages.

Matched Android 16 x86_64 runs compare a 48-candidate per-library rerank pool with a 12-candidate pool using the same emulator, v2 case-set hash, test APK hash, and package payloads. With all six libraries enabled, the 48-candidate control measured 945 ms median and 1,246 ms p95 across query medians. The 12-candidate run measured 426 ms median and 564 ms p95: 55% lower on both measures. All six expected sources were found, and each source kept the same rank between the runs (four of six at rank 1). This is an emulator-specific retrieval result, not a phone-latency prediction. Full, hash-identified reports are [`the 48-candidate control`](evidence/android-multi-library-retrieval-scaling-x86_64-android16-2026-10-19-baseline48-v2-no-cache.json) and [`the 12-candidate run`](evidence/android-multi-library-retrieval-scaling-x86_64-android16-2026-10-19-candidate12-v2-no-cache.json). The earlier five-library comparison is recorded in [its 48-candidate control](evidence/android-multi-library-retrieval-scaling-x86_64-android16-2026-10-19-baseline48-no-cache.json) and [12-candidate run](evidence/android-multi-library-retrieval-scaling-x86_64-android16-2026-10-19-candidate12-no-cache.json).

A matched v0.3.7 comparison then reduced the production multi-library candidate limit from 12 to 8. Both reports use the same Android 16 x86_64 emulator, six pinned package payloads, v3 case-set hash, and instrumentation APK; the limit is read from the running app at runtime. For six enabled libraries, the median across case medians fell from 385 ms to 292 ms (-24%), and p95 fell from 528 ms to 426 ms (-19%). All expected sources kept their ranks at every enabled-library count. This is one repeated five-sample run per build on this emulator, so it supports the smaller limit for this workload but does not predict other hardware. See [`the v0.3.7 limit-12 control`](evidence/android-multi-library-retrieval-scaling-x86_64-android16-2026-10-19-baseline12-v5.json) and [`the v0.3.7 limit-8 candidate`](evidence/android-multi-library-retrieval-scaling-x86_64-android16-2026-10-19-candidate8-v7.json).

The v3 case set adds a maximum rank of 4 for the specific camping wildlife and food-storage source. The Android test enforces the bound whenever the expected library is enabled. The candidate meets it at library counts four, five, and six. Its reports include the selected library and document IDs, sections, and lexical overlap counts, but omit passage text and model output. Generic one-word titles no longer outrank more specific evidence solely for an exact one-word match, and cross-library ties preserve the caller's package and per-library candidate order.

To reproduce, first stage the six exact `.blobs` files named in the case set under `android/.local/hf-publish/packages/`, then install the debug app and instrumentation APK. Push the fixture and packages *after* installation because reinstalling the test target clears its app-specific external files:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
Push-Location android
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest --offline --console=plain
Pop-Location

$stage = '/sdcard/Android/data/dev.blobs.app.debug/files'
& $adb shell mkdir -p $stage
& $adb push corpus/evaluation/cases/multi-library-retrieval-scaling-v3.json `
  "$stage/retrieval-benchmark-multi-library.json"
Get-ChildItem android/.local/hf-publish/packages -Filter '*.blobs' |
  Where-Object Name -In @(
    'wikipedia-starter-schema3.blobs',
    'nps-outdoor-safety-2026-10-v1.blobs',
    'ready-emergency-preparedness-2026-10-v1.blobs',
    'camping-safety-2026-10-v1.blobs',
    'backcountry-basics-2026-10-v3.blobs',
    'wikivoyage-en-2026-09-v2.blobs'
  ) | ForEach-Object { & $adb push $_.FullName "$stage/$($_.Name)" }
& $adb shell am instrument -w `
  -e class dev.blobs.app.MultiLibraryRetrievalScalingTest `
  dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner
& $adb logcat -d -s BlobsCorpusScaleBench:I
```

The report is emitted in ordered `REPORT_PART:i/n:` chunks. Join those chunks to recover its JSON. It includes the APK and test-APK digests, device fingerprint, package identities, and per-query samples. Keep each run in a new evidence file.

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
