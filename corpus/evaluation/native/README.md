# Native inference performance smoke

This manual Android instrumentation run measures model load, prompt preparation, context setup, prefill, first-token callback, and decode latency. It also records the device profile, exact model and llama.cpp revision, application/test APK digests, prompt hashes, output-token count, and selected thread count. The generated text is discarded; no answer is saved or graded. Results describe the measured emulator or phone and should not be generalized to other hardware.

The pinned Qwen3 0.6B fixture and fixed workload are described in [`../locks/qwen3-0.6b-native-smoke-v1.lock.json`](../locks/qwen3-0.6b-native-smoke-v1.lock.json). The checked-in [`../baselines/qwen3-0.6b-native-inference-smoke-v1-x86_64-2026-10-19.json`](../baselines/qwen3-0.6b-native-inference-smoke-v1-x86_64-2026-10-19.json) contains three successful runs on an Android 16 x86_64 emulator, with median and range rather than a device-independent target.

That emulator's medians were 2.835 seconds for the full native call, 684 ms to the first native callback, and 33.85 generated tokens per second. The run range is part of the baseline because scheduling noise affects emulator timing. The auditable input reports and checks are retained under [`../evidence/native-inference-smoke-v1-x86_64-2026-10-19/`](../evidence/native-inference-smoke-v1-x86_64-2026-10-19/).

A same-build thread sweep on that four-core emulator kept the model, runtime, prompt, and 71-token output length fixed across three runs per setting. Adaptive 4-thread inference measured 32.90 tokens/s median; fixed 3 threads measured 31.17, 2 threads 24.33, and 1 thread 13.34. The comparison reports are [`4 vs 3`](../baselines/native-thread-sweep-qwen3-06b-2026-10-19/adaptive-4-vs-fixed-3.json), [`4 vs 2`](../baselines/native-thread-sweep-qwen3-06b-2026-10-19/adaptive-4-vs-fixed-2.json), and [`4 vs 1`](../baselines/native-thread-sweep-qwen3-06b-2026-10-19/adaptive-4-vs-fixed-1.json); per-run inputs and audits are in [`the sweep evidence folder`](../evidence/native-thread-sweep-qwen3-06b-2026-10-19/). This result supports the current four-thread choice on this emulator only; it is not a reason to force that choice on other devices.

## Adaptive context allocation probe

The native path now allocates the smallest 512-token power-of-two context that can hold the tokenized prompt and requested output, up to the existing 4,096-token ceiling. The product requests at most 768 output tokens and rejects prompts above 3,072 tokens, so the current maximum workload still fits. The instrumentation records `context_tokens` and checks that the selected context covers the full prompt plus output budget. Smaller allocations reduce unused KV-cache reservation for short questions.

The three-run probe in [`the context-capacity evidence folder`](../evidence/native-context-capacity-adaptive-x86_64-android16-2026-10-19/) held the exact model, runtime, prompt, and 71-token output fixed. Median context setup fell from 169.094 ms to 24.021 ms (-85.8%), and first-token callback time fell from 684.478 ms to 528.656 ms (-22.8%). Total native-call latency measured 3,008 ms versus the 2,835 ms baseline (+6.1%); decode throughput measured 29.09 versus 33.85 tokens/s (-14.1%). This probe demonstrates lower setup time and first-token latency, but not a total-latency or decode-throughput win; the small emulator run group is too noisy for a broad performance claim. Repeat on additional device profiles before drawing device-wide conclusions.

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

Install the app and test APKs, then run the fixed instrumentation test at least three times. Each run writes a small JSON report in the app's private files directory; pull it to a unique local filename after each invocation:

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

Baseline and candidate groups must use the same emulator profile, exact model, runtime revision, prompt workload, and output-token count. Their app and instrumentation APK hashes may differ, which lets this comparison measure code changes. Thread selection may differ between groups and is recorded in the result. At least three audited runs per build are required; the output reports medians, ranges, and relative latency/throughput changes, not answer quality. To record a new initial baseline without a candidate group, omit `--candidate-audit` and use three paths with `--baseline-audit`.
