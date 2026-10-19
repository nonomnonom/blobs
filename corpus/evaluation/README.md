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

## Measure an application journey per device profile

`AppJourneyPerformanceTest` measures local-library search, time to the first visible response,
stopping an active response, and sampled process memory. Response start is the first nonempty
streamed text state ready for display, not a measured frame presentation time. It uses an installed model and the
enabled libraries, stops each of three responses after text appears, and checks that the next
draft remains saved. Generated wording is not scored. APK, model, library, RAM, CPU, ABI, and
OS identities accompany the measurements.

Install the debug app and instrumentation APK, set up the model and libraries in Blobs, and
keep the test device unlocked. From `corpus/`:

```powershell
uv run --locked python -m evaluation.android.app_performance `
  --serial emulator-5554 `
  --profile android16-4gb-2core `
  --model-key MODEL_KEY `
  --output work/application-performance
```

Pass `--adb PATH_TO_ADB` if it is not on PATH. `MODEL_KEY` is the installed package key:
`id-version-first16charactersOfPayloadSha256`, as recorded in package installation reports.
The command restarts the debug app five times to capture Android's process-cold startup
`TotalTime`, retaining OS caches. It then runs the instrumentation capture. It does not clear
app data, download assets, or enable networking. Repeat on each device or explicitly configured
emulator profile; retain separate reports instead of combining different hardware into one score.
Memory is the largest observed PSS sample taken once per second, not the exact peak allocation.

## Published travel retrieval check

The [2026-09.3 travel report](baselines/wikivoyage-en-2026-09.3-retrieval-smoke-v1-host.json) uses the exact archive downloaded from its immutable public HF revision and the [v2 case lock](cases/travel-retrieval-smoke-v2.json). The six questions and expected source identities are unchanged from v1; only the package identity is updated. All six expected source documents appeared at rank 1. Host median retrieval was 78.3 ms and nearest-rank p95 was 235.5 ms in this single run. This verifies the fixed source-retrieval cases, not general relevance, phone latency, or generated-answer correctness.

```powershell
uv run --locked python -m evaluation.retrieval.retrieval_smoke `
  --library-package PATH_TO/wikivoyage-en-2026-09-v3.blobs `
  --cases evaluation/cases/travel-retrieval-smoke-v2.json `
  --work-directory work/travel-retrieval `
  --output reports/travel-retrieval.json
```

## Measure in-app model and library downloads

`PackageLifecyclePerformanceTest` starts the normal `ProductViewModel` download action, then records catalog refresh, download plus archive verification/inspection, and package install/validation/activation separately. It downloads only from the signature-verified app package list, checks the final database entry and SHA-256, and records APK/package/device identities. This is a manual Android instrumentation test because it needs network access and a package that is not already installed in the debug app. Use a fresh debug app install or a package ID that has not been installed; it does not clear app data.

The separate manual method `PackageLifecyclePerformanceTest#refreshesPublishedLibrariesWithoutDownloadingPackages` checks the live signed list using the app's trusted keys and revision boundary. It verifies that the focused preparedness packs are listed while installed packages and download files remain unchanged. It needs a validated internet connection and performs no inference.

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

The published travel edition `2026-09.3` also completed this flow in the installed v0.3.9 debug app: remote signed-list refresh took 723 ms; download and inspection took 31,958 ms; installation, validation, and activation took 29,307 ms (61,263 ms from the download action to completion). This single Android 16 x86_64 run downloaded 295,304,448 bytes and installed a 628,633,600-byte library. The [travel lifecycle report](evidence/android-package-lifecycle-travel-v3-x86_64-android16-2026-10-19.json) records the exact APK and package hashes. It verifies first installation of this travel edition, not replacement of an older installed travel edition or model answer quality. The emulator was returned to airplane mode with Wi-Fi disabled afterward.

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

The recorded [`v4 run`](evidence/android-multi-library-retrieval-scaling-v4-x86_64-android16-2026-10-19.json) uses the v0.3.8 debug build, the six-library fixture, and the updated NPS outdoor-safety 2026-10.2 package. It adds a natural-water-hazards query. On this Android 16 x86_64 emulator, the seven-query, five-sample run measured 309 ms median and 425 ms p95 across query medians at six libraries. Every expected source was retrieved whenever its library was enabled; `Water Hazards` ranked first at enabled-library counts two through six. The report records the APK hash and per-query samples. This is device-specific retrieval evidence, with no model or generated-answer evaluation.

To reproduce the six-library benchmark, stage the case's exact catalog versions by their package SHA-256, then install the debug app and instrumentation APK. The staging command downloads only missing or invalid files from the catalog's immutable Hugging Face revision:

```powershell
uv run --locked --project corpus python android/tools/stage_multi_library_benchmark.py `
  --cases corpus/evaluation/cases/multi-library-retrieval-scaling-v5.json `
  --catalog android/app/src/main/assets/package-catalog.json `
  --output-dir android/.local/hf-publish/packages

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
Push-Location android
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest --offline --console=plain
Pop-Location

$stage = '/sdcard/Android/data/dev.blobs.app.debug/files'
& $adb shell mkdir -p $stage
& $adb push corpus/evaluation/cases/multi-library-retrieval-scaling-v5.json `
  "$stage/retrieval-benchmark-multi-library.json"
Get-ChildItem android/.local/hf-publish/packages -Filter '*.blobs' |
  Where-Object Name -In @(
    'wikipedia-starter-schema3.blobs',
    'nps-outdoor-safety-2026-10-v2.blobs',
    'ready-emergency-preparedness-2026-10-v1.blobs',
    'camping-safety-2026-10-v1.blobs',
    'backcountry-basics-2026-10-v3.blobs',
    'wikivoyage-en-2026-09-v2.blobs'
  ) | ForEach-Object { & $adb push $_.FullName "$stage/$($_.Name)" }
& $adb shell am instrument -w `
  -e class dev.blobs.app.MultiLibraryRetrievalScalingTest `
  dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner
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

## Measure the offline chat path

`NativeInferenceTest#verifiedModelFileCompletesOfflineChatFlow` measures the app flow after local assets are staged: model-file validation, library-package installation, retrieval, generation, and saved-turn completion. The test checks offline mode, package integrity, a completed persisted turn, draft persistence, and runtime measurements. It does not assert retrieved titles, publishers, or generated-answer correctness. Generated answer text is neither stored nor evaluated. `UNVERIFIED` is a valid app state and is recorded without grading answer content.

The [`three-run Android 16 x86_64 report`](evidence/android-offline-chat-performance-v2-x86_64-android16-2026-10-19/summary.json) is a historical timing profile of the Qwen3 0.6B Q8 file and NPS outdoor-safety package. Median end-to-end time was 21.915 s (nearest-rank p95 22.264 s); first-visible-state time was 17.218 s median (17.396 s p95). It predates the flow-only acceptance criteria below, so its source-title checks are not used to judge app behavior. This verified-file fixture does not measure in-app model download or predict phone performance.

Profiling showed that prompt prefill, rather than retrieval, dominated this workload. `WorkController` now sends the four highest-ranked sources to generation and presents that same evidence set with the turn. The six-library retrieval scaling case set keeps its expected sources within those first four ranks. In three same-device offline runs with the same model, library, and question, the profiled baseline's median end-to-end time fell from 22.618 s to 16.631 s (26.5%); median first-visible-state time fell from 17.752 s to 13.130 s (26.0%), and prompt tokens fell from 1,550 to 1,156 (25.4%). Retrieval stayed negligible at 22 ms versus 11 ms median, while decode throughput remained about 10.7–10.8 tokens/s. See the [before-change profile](evidence/android-offline-generation-profile-x86_64-android16-2026-10-19/baseline-summary.json) and [four-source result](evidence/android-offline-generation-sourcecap-x86_64-android16-2026-10-19/summary.json). This measures one Android 16 x86_64 emulator with one small quantized model; it does not predict latency on other devices. Answer text is not retained or evaluated.

Catalog refreshes now start only when the user taps **Check for updates**; opening the app or Library/Models does not initiate a request. The latest build was run through the same three airplane-mode chat cases: median end-to-end was 15.982 s, first visible state 12.289 s, retrieval 13 ms, and decode 11.4 tokens/s. The [three-run report](evidence/android-offline-explicit-catalog-x86_64-android16-2026-10-19/summary.json) records the new APK hashes and source identity without answer text. Seven process-cold launches on the same emulator measured 1.092 s median and 91,609 KiB median PSS before a model was loaded; the [startup report](evidence/android-startup-explicit-catalog-x86_64-android16-2026-10-19/report.json) is an idle debug-app measure, not inference peak memory or a 12 GB device certification.

The v0.3.9 debug build was measured in three airplane-mode runs on an Android 16 x86_64 emulator configured with a 12 GB data partition. That historical profile measured 17.563 s median end-to-end, 13.626 s first-visible-state, 21 ms retrieval, and 12.24 decoded tokens/s. Its old source-identity gate is not part of the current flow benchmark. See the [v0.3.9 report](evidence/android-offline-chat-performance-v0.3.9-12gb-data-retry-x86_64-android36-2026-10-19/summary.json).

### Profile chat memory

`evaluation.android.chat_resources` reruns the offline chat instrumentation while sampling PSS from both the app and instrumentation processes with `adb shell dumpsys meminfo`. It validates model and library digests, airplane-mode state, completion, and app state, without requiring any particular retrieved source or answer. The instrumentation report omits generated answer text. Sampled PSS is a useful device-specific measure, not a guarantee of the process's instantaneous allocation peak.

The [three-run profile](evidence/android-chat-resources-qwen3-06b-x86_64-android16-2026-10-19/profile.json) used the Qwen3 0.6B Q8 fixture and the NPS outdoor-safety library on the Android 16 x86_64 emulator. Combined peak sampled PSS for the app and test process was 1,045,278 KiB median and 1,045,564 KiB maximum. That small-model emulator run is far below 12 GiB, but it does not establish peak usage for the larger Gemma models or on other devices.

After staging the model and library as described above, run at least three unique IDs and write to a new output path:

```powershell
uv run --locked python -m evaluation.android.chat_resources `
  --adb "$env:ANDROID_HOME/platform-tools/adb.exe" `
  --serial emulator-5554 `
  --version-name 0.3.8 `
  --model-local ../android/.local/perf/qwen3-integration.gguf `
  --model-path /data/user/0/dev.blobs.app.debug/files/qwen3-smoke.gguf `
  --model-sha256 9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031 `
  --library-local ../android/.local/hf-publish/packages/nps-outdoor-safety-2026-10-v2.blobs `
  --library-path /data/user/0/dev.blobs.app.debug/files/nps-outdoor-safety.blobs `
  --question 'What hazards are present in natural water?' `
  --run-id memory-01 --run-id memory-02 --run-id memory-03 `
  --output evaluation/evidence/android-chat-resources-local/profile.json
```

To repeat, build/install the debug and instrumentation APKs, then copy the locked model fixture and pinned library package to app-private files. This avoids external-storage access differences between Android versions:

```powershell
$adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
Push-Location android
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest --offline --console=plain
Pop-Location
& $adb push android/.local/perf/qwen3-integration.gguf /data/local/tmp/blobs-qwen3.gguf
& $adb shell run-as dev.blobs.app.debug cp /data/local/tmp/blobs-qwen3.gguf files/qwen3-smoke.gguf
& $adb push android/.local/hf-publish/packages/nps-outdoor-safety-2026-10-v2.blobs /data/local/tmp/nps-outdoor-safety.blobs
& $adb shell run-as dev.blobs.app.debug cp /data/local/tmp/nps-outdoor-safety.blobs files/nps-outdoor-safety.blobs
```

Use a unique run ID each time. URL-safe Base64 keeps the question intact through `adb shell`; the instrumentation decodes it locally. Save each report from the app-private directory. Preserve the previous airplane-mode value and restore it in a `finally` block when scripting repeated runs.

```powershell
$question = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('What hazards are present in natural water?')).TrimEnd('=').Replace('+','-').Replace('/','_')
$previousAirplaneMode = (& $adb shell settings get global airplane_mode_on).Trim()
try {
  & $adb shell cmd connectivity airplane-mode enable
  & $adb shell am instrument --user 0 -w `
    -e class 'dev.blobs.app.NativeInferenceTest#verifiedModelFileCompletesOfflineChatFlow' `
    -e modelFile '/data/user/0/dev.blobs.app.debug/files/qwen3-smoke.gguf' `
    -e modelSha256 9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031 `
    -e libraryPackage '/data/user/0/dev.blobs.app.debug/files/nps-outdoor-safety.blobs' `
    -e runId offline-chat-01 -e questionBase64 $question `
    -e timeoutSeconds 180 -e requireAirplaneMode true `
    dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner
  $report = & $adb exec-out run-as dev.blobs.app.debug cat files/answer-offline-chat-01.json
  $report | Set-Content -Encoding utf8NoBOM corpus/evaluation/evidence/chat-run-01.json
} finally {
  if ($previousAirplaneMode -eq '1') {
    & $adb shell cmd connectivity airplane-mode enable
  } else {
    & $adb shell cmd connectivity airplane-mode disable
  }
}
```

After at least three successful runs, hash the exact installed app and test APKs into `installed-app.json`, then create a summary in a new write-once output path:

```powershell
uv run --locked --project . python -m evaluation.android.chat_performance `
  --run evaluation/evidence/chat-run-01.json `
  --run evaluation/evidence/chat-run-02.json `
  --run evaluation/evidence/chat-run-03.json `
  --installed-app evaluation/evidence/installed-app.json `
  --output evaluation/baselines/android-offline-chat-local.json
```

The summarizer requires matching device, app, model, and library identities; verified airplane mode; and successful saved turns. It rejects reports containing generated answer text to keep performance evidence free of conversation content. It never checks answer text or scores retrieved titles.

Gemma 4 E2B Q4 was downloaded from the signed in-app library and installed through the normal package flow on an Android 16 x86_64 emulator with about 8 GiB RAM and a 12 GiB data partition. The 3.35 GB download, hash and GGUF validation, and activation completed in 407.2 s on that emulator's network. The latest three airplane-mode chat-flow runs used the installed Gemma model and NPS library. All three completed and saved a turn without checking retrieved titles, publishers, or answer text. Median app-observed end-to-end time was 65.7 s (p95 66.3 s), first visible state was 45.7 s (p95 46.8 s), retrieval was 12 ms median, and decode throughput was 4.96 tokens/s median. Sampled app-plus-instrumentation PSS was 3,559,557 KiB median and 3,565,943 KiB maximum; `/data` had 12,223,704 KiB total capacity. See the [flow-only latency report](evidence/android-offline-chat-performance-gemma4-e2b-q4-x86_64-android16-2026-10-19/summary-flow-only.json), [flow-only PSS report](evidence/android-offline-chat-performance-gemma4-e2b-q4-x86_64-android16-2026-10-19/resources-flow-only.json), [app identity](evidence/android-offline-chat-performance-gemma4-e2b-q4-x86_64-android16-2026-10-19/installed-app-flow-only.json), and the [download/install receipts](evidence/android-offline-chat-performance-gemma4-e2b-q4-x86_64-android16-2026-10-19/installed-model-package.json). These are measurements of one emulator profile; they do not predict phone performance.
