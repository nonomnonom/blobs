# Optional native inference measurements

Use these checks to diagnose native model loading, prefill, context reuse, decoding, and cancellation. They are not required for ordinary app verification and do not grade generated answers. A result applies to its recorded workload and device.

Build the debug app and instrumentation APK using the [Android guide](../../../android/README.md). Run the commands below from `corpus/`. Supply a local `qwen3-integration.gguf` whose bytes match `evaluation/locks/qwen3-0.6b-native-smoke-v1.lock.json`; the staging tool rejects a mismatch. A local `.blobs` model package stores that payload as `model.gguf`.

Stage and verify it on the selected emulator. Use a new output filename and a unique remote target for each new set of runs:

```powershell
uv run --locked python -m evaluation.native.stage_android_model `
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
