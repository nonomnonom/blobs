# Native inference performance smoke

This manual Android instrumentation run measures model load, prompt preparation, context setup, prefill, first-token callback, and decode latency. It also records the device profile, exact model and llama.cpp revision, application/test APK digests, prompt hashes, output-token count, and selected thread count. The generated text is discarded; no answer is saved or graded. Results describe the measured emulator or phone and should not be generalized to other hardware.

The pinned Qwen3 0.6B fixture and fixed workload are described in [`../locks/qwen3-0.6b-native-smoke-v1.lock.json`](../locks/qwen3-0.6b-native-smoke-v1.lock.json). The checked-in [`../baselines/qwen3-0.6b-native-inference-smoke-v1-x86_64-2026-10-19.json`](../baselines/qwen3-0.6b-native-inference-smoke-v1-x86_64-2026-10-19.json) contains three successful runs on an Android 16 x86_64 emulator, with median and range rather than a device-independent target.

That emulator's medians were 2.835 seconds for the full native call, 684 ms to the first native callback, and 33.85 generated tokens per second. The run range is part of the baseline because scheduling noise affects emulator timing. The auditable input reports and checks are retained under [`../evidence/native-inference-smoke-v1-x86_64-2026-10-19/`](../evidence/native-inference-smoke-v1-x86_64-2026-10-19/).

The same native smoke test also passed with Android airplane mode enabled and asserted inside the test process. The retained [`offline run evidence`](../evidence/native-offline-perf-x86_64-android16-2026-10-19/) is bound to the exact debug and instrumentation APK hashes. On this Android 16 x86_64 emulator, that run measured 2.803 seconds cold and 2.472 seconds warm, with 650 ms and 298 ms to the first native callback respectively; decode measured 33.0 and 32.7 tokens/s. These measurements establish that this pinned native inference path runs with the radio disabled. They describe one emulator and one fixed workload, and do not predict phone performance or grade generated answers.

The current `main` debug build (version name 0.3.8) was measured three more times with the same model lock, runtime, prompt, device, and adaptive thread policy. The [`v2 comparison`](../baselines/qwen3-0.6b-native-inference-smoke-v2-x86_64-2026-10-19.json) records median cold-call latency of 2.839 seconds, first callback at 543 ms, and decode at 31.70 tokens/s. The [`warm-cache comparison`](../baselines/qwen3-0.6b-native-model-cache-v2-x86_64-2026-10-19.json) records 2.585 seconds, 302 ms to first callback, and 31.09 tokens/s for warm turns, with no model load. The warm-turn latency and throughput ranges overlap the prior cache run; the small run group does not establish a performance regression or improvement. No native inference or runtime files changed between v0.3.7 and this build; the application changes in that interval were in retrieval and package-catalog data. Full current-run inputs and audits are in [`the v2 evidence folder`](../evidence/native-inference-smoke-v2-x86_64-android16-2026-10-19/). Generated output text is absent from these reports.

A same-build thread sweep on that four-core emulator kept the model, runtime, prompt, and 71-token output length fixed across three runs per setting. Adaptive 4-thread inference measured 32.90 tokens/s median; fixed 3 threads measured 31.17, 2 threads 24.33, and 1 thread 13.34. The comparison reports are [`4 vs 3`](../baselines/native-thread-sweep-qwen3-06b-2026-10-19/adaptive-4-vs-fixed-3.json), [`4 vs 2`](../baselines/native-thread-sweep-qwen3-06b-2026-10-19/adaptive-4-vs-fixed-2.json), and [`4 vs 1`](../baselines/native-thread-sweep-qwen3-06b-2026-10-19/adaptive-4-vs-fixed-1.json); per-run inputs and audits are in [`the sweep evidence folder`](../evidence/native-thread-sweep-qwen3-06b-2026-10-19/). This result supports the current four-thread choice on this emulator only; it is not a reason to force that choice on other devices.

## Adaptive context allocation probe

The native path now allocates the smallest 512-token power-of-two context that can hold the tokenized prompt and requested output, up to the existing 4,096-token ceiling. The product requests at most 768 output tokens and rejects prompts above 3,072 tokens, so the current maximum workload still fits. The instrumentation records `context_tokens` and checks that the selected context covers the full prompt plus output budget. Smaller allocations reduce unused KV-cache reservation for short questions.

The three-run probe in [`the context-capacity evidence folder`](../evidence/native-context-capacity-adaptive-x86_64-android16-2026-10-19/) held the exact model, runtime, prompt, and 71-token output fixed. Median context setup fell from 169.094 ms to 24.021 ms (-85.8%), and first-token callback time fell from 684.478 ms to 528.656 ms (-22.8%). Total native-call latency measured 3,008 ms versus the 2,835 ms baseline (+6.1%); decode throughput measured 29.09 versus 33.85 tokens/s (-14.1%). This probe demonstrates lower setup time and first-token latency, but not a total-latency or decode-throughput win; the small emulator run group is too noisy for a broad performance claim. Repeat on additional device profiles before drawing device-wide conclusions.

## Active-chat model reuse

The native bridge keeps at most one loaded model while Blobs is active. It releases that model when the app backgrounds, when the selected model is changed or removed, or when Android reports low memory. The cache is keyed by the installed model file identity; if the identity cannot be checked, inference uses a temporary model instead of reusing an uncertain cache entry. This avoids repeated model loading across chat turns without retaining multiple model files in native memory.

The three-run comparison in [`the model-cache evidence folder`](../evidence/native-model-cache-qwen3-06b-x86_64-android16-2026-10-19/) verifies cold load, warm cache hit, explicit release and reload, cancellation, and missing-model failure on each run. Its report is [`the comparison result`](../baselines/native-model-cache-qwen3-06b-x86_64-android16-2026-10-19.json). On the measured Android 16 x86_64 emulator, warm-turn median native-call latency was 2.235 seconds and first-callback latency was 294 ms, compared with 2.835 seconds and 684 ms for the earlier cold-per-turn baseline (-21.2% and -57.0%). The warm-turn model-load time was zero. These are results for this emulator and fixed 71-token probe; they do not establish behavior on every phone, measure retrieval quality, or grade generated answers.

## Inference after in-app model installation

The Qwen 0.6B file in this measurement was downloaded from Blobs' signed in-app package list, installed and hash-checked by `PackageInstaller`, then loaded from its installed package path. The package lifecycle report is [`the installation evidence`](../evidence/android-package-lifecycle-qwen-q8-x86_64-android16-2026-10-19.json). Six matched smoke runs are [`run 1`](../evidence/android-native-inference-installed-qwen-v0.3.7-x86_64-android16-2026-10-19/run-01.json), [`run 2`](../evidence/android-native-inference-installed-qwen-v0.3.7-x86_64-android16-2026-10-19/run-02.json), [`run 3`](../evidence/android-native-inference-installed-qwen-v0.3.7-x86_64-android16-2026-10-19/run-03.json), [`run 4`](../evidence/android-native-inference-installed-qwen-v0.3.7-x86_64-android16-2026-10-19/run-04.json), [`run 5`](../evidence/android-native-inference-installed-qwen-v0.3.7-x86_64-android16-2026-10-19/run-05.json), and [`run 6`](../evidence/android-native-inference-installed-qwen-v0.3.7-x86_64-android16-2026-10-19/run-06.json); each has a passing provenance and runtime audit in the same folder. The six-run aggregate is [`the installed-model baseline`](../baselines/android-native-inference-installed-qwen-v0.3.7-x86_64-android16-2026-10-19-6runs.json).

On this Android 16 x86_64 emulator and debug app build, six-run cold medians were 3,049 ms total, 613 ms to first callback, and 30.56 tokens/s; warm medians were 2,564 ms total, 302 ms to first callback, and 31.38 tokens/s. All runs generated 71 tokens under the fixed performance probe; generated text was discarded. Run ranges were broad: cold total 2,722–3,594 ms, first callback 526–1,138 ms, and decode 23.15–36.64 tokens/s. Compared with the earlier three-run same-device baseline, the six-run result has 7.5% higher cold total latency, 10.4% lower first-callback latency, 85.2% lower context setup, and 9.7% lower decode throughput. The groups have different sample sizes and their ranges overlap; this is an operational comparison, not evidence of a confirmed code regression or a device-wide estimate.

A six-run path control compared loading the installed package file with loading an identical private-file copy. The report is [`the package-path comparison`](../evidence/android-native-inference-package-vs-file-copy-x86_64-android16-2026-10-19.json), and the control inputs and audits are [`the copy-control folder`](../evidence/android-native-inference-path-control-v0.3.7-x86_64-android16-2026-10-19/README.md). The medians were 3,049 ms versus 2,943 ms cold total, 2,564 ms versus 2,573 ms warm total, and 30.56 versus 29.55 tokens/s cold decode. The ranges overlap and the medians move in different directions; this control finds no consistent package-path penalty at the measured scale.

The native audit accepts either the older staged-file workflow (`--staging`) or an in-app package report (`--package-install`). For an installed model, it checks that the package report, model lock, native run, device profile and APK hashes agree before allowing the run into `compare_native_smoke`.

The installed-package audits in the evidence folder were created with `--package-install evaluation/evidence/android-package-lifecycle-qwen-q8-x86_64-android16-2026-10-19.json`; their output summary includes both cold and warm medians. To audit a later run from the installed package, pass that run's package lifecycle report to the same option and provide an installed-app record whose APK hashes match that report. This keeps the app download/install path and the native timing evidence tied to the same model bytes and build.

## Run a measurement

The extraction command below runs from `android/`. Run the staging, audit, and summary commands from `corpus/`. Build the debug app and instrumentation APK first. Extract the fixture's `model.gguf` payload from the v0.3.3 `qwen3-integration.blobs` package, preserving the filename required by the lock:

```powershell
New-Item -ItemType Directory -Force .local/perf | Out-Null
uv run --locked --project ../corpus python -c "import zipfile; archive=zipfile.ZipFile('release/0.3.3/qwen3-integration.blobs'); archive.extract('model.gguf', '.local/perf')"
Move-Item .local/perf/model.gguf .local/perf/qwen3-integration.gguf
```

Stage and verify it on the selected emulator. Use a new output filename and a unique remote target for each new set of runs:

```powershell
uv run --locked --project ../corpus python -m evaluation.native.stage_android_model `
  --adb "$env:ANDROID_HOME\platform-tools\adb.exe" `
  --serial emulator-5554 `
  --source ../android/.local/perf/qwen3-integration.gguf `
  --lock evaluation/locks/qwen3-0.6b-native-smoke-v1.lock.json `
  --target /data/local/tmp/blobs-perf-qwen3-06b.gguf `
  --output reports/native/staging.json

$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -s emulator-5554 install -r ../android/app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5554 install -r ../android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5554 shell run-as dev.blobs.app.debug mkdir -p files
& $adb -s emulator-5554 shell run-as dev.blobs.app.debug cp `
  /data/local/tmp/blobs-perf-qwen3-06b.gguf files/qwen3-smoke.gguf
```

Install the app and test APKs, then run the fixed instrumentation test at least three times. Each run measures a cold call, a warm call with the same model, a call after explicit release, cancellation, and missing-model handling. It writes a small JSON report in the app's private files directory; pull it to a unique local filename after each invocation:

```powershell
$runId = 'candidate-01'
& $adb -s emulator-5554 shell am instrument -w `
  -e class 'dev.blobs.app.NativeInferenceTest#realModelStreamsAndCancellationStopsNativeLoop' `
  -e smokeModel '/data/user/0/dev.blobs.app.debug/files/qwen3-smoke.gguf' `
  -e runId $runId `
  dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner
$report = & $adb -s emulator-5554 exec-out run-as dev.blobs.app.debug cat "files/native-$runId.json"
$report | Set-Content -Encoding utf8 "reports/native/$runId.json"
```

To measure a fixed thread count for a thread-policy sweep, add `-e smokeThreads 1` (or `2`, `3`, or `4`) to the instrumentation command. Omit that argument to measure Blobs' adaptive thread selection. The audit records the mode and selected count; comparisons may vary that implementation setting while holding the prompt, model, runtime, device, and output-token count fixed.

Audit each run against the pinned model/runtime and the built APK hashes. Create the local installed-app record from the APKs used for that run:

```powershell
$appApkSha256 = (Get-FileHash ../android/app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256).Hash.ToLowerInvariant()
$testApkSha256 = (Get-FileHash ../android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk -Algorithm SHA256).Hash.ToLowerInvariant()
[ordered]@{ complete=$true; package='dev.blobs.app.debug'; apk_sha256=$appApkSha256; test_apk_sha256=$testApkSha256 } |
  ConvertTo-Json | Set-Content -Encoding utf8 reports/native/installed-app.json
```

Use new report names because staging, audit, and summary outputs are write-once.

```powershell
uv run --locked --project . python -m evaluation.native.audit_native_smoke `
  --run reports/native/candidate-01.json `
  --staging reports/native/staging.json `
  --model-lock evaluation/locks/qwen3-0.6b-native-smoke-v1.lock.json `
  --installed-app reports/native/installed-app.json `
  --app-apk-sha256 $appApkSha256 `
  --test-apk-sha256 $testApkSha256 `
  --output reports/native/candidate-01-audit.json

uv run --locked --project . python -m evaluation.native.compare_native_smoke `
  --baseline-audit reports/native/baseline-01-audit.json reports/native/baseline-02-audit.json reports/native/baseline-03-audit.json `
  --candidate-audit reports/native/candidate-01-audit.json reports/native/candidate-02-audit.json reports/native/candidate-03-audit.json `
  --runtime-lock ../android/third_party/llama.lock.json `
  --output reports/native/comparison.json
```

Baseline and candidate groups must use the same emulator profile, exact model, runtime revision, prompt workload, and output-token count. Their app and instrumentation APK hashes may differ, which lets this comparison measure code changes. Thread selection may differ between groups and is recorded in the result. At least three audited runs per build are required; the output reports cold and warm medians, ranges, and relative latency/throughput changes, not answer quality. To record a new initial baseline without a candidate group, omit `--candidate-audit` and use three paths with `--baseline-audit`.
