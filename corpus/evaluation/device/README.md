# Manual Android evidence capture

Use this document only after the owner re-enables validation. Do not run a
capture on an emulator to make a performance claim. Use the target Android
device and record serial/fingerprint, ABI, and OS version. This activity is
never a CI gate.

## Preparation

1. Build and install the APK and instrumentation APK that will be recorded.
2. Stage one GGUF model and one library package whose hashes were verified.
   Record the library manifest and retrieval case-file hash when applicable.
3. Block device network transport, then record the blocking method and visible
   state before the run.
4. Choose a new run ID. Never overwrite a previous report.

`ManualCheck` is excluded from the normal instrumentation suite. Run only the
class or method that provides the desired evidence and pass every input
explicitly.

## One offline chat

`NativeInferenceTest#verifiedModelFileCompletesOfflineChatFlow` verifies the
fixed product path: one local retrieval, one JNI completion, durable turn
persistence, evidence, and UI state. It is not a token-level benchmark or a
semantic-correctness judge.

```powershell
$adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
$serial = "DEVICE_SERIAL"
$package = "dev.blobs.app.debug"
$runId = "offline-001"
$model = "/sdcard/Android/data/$package/files/eval/model.gguf"
$library = "/sdcard/Android/data/$package/files/eval/library.blobs"

& $adb -s $serial shell am instrument -w -e class 'dev.blobs.app.NativeInferenceTest#verifiedModelFileCompletesOfflineChatFlow' -e modelFile $model -e modelSha256 MODEL_SHA256 -e libraryPackage $library -e runId $runId -e requireAirplaneMode true "${package}.test/androidx.test.runner.AndroidJUnitRunner"
```

Read the private report produced by the test without mutating it:

```powershell
& $adb -s $serial exec-out run-as $package cat "files/answer-$runId.json"
```

Review the answer together with recorded evidence/passages. Unsupported claims,
retrieval misses, partial answers, and cancellation must be recorded as
observed; never rewrite them as a failure merely because they differ from an
example.

## Other captures

- `MultiLibraryRetrievalScalingTest` records retrieval across caller-staged
  libraries.
- `AppJourneyPerformanceTest` records response state, cancellation, and memory
  across an app journey.
- `PackageLifecyclePerformanceTest` records package lifecycle.

Compare only runs with the same APK/test APK, model, library, case, device,
inference configuration, and warm/cold policy. Evidence missing those identities
must be marked **historical**.
