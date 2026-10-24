# Manual Android evidence capture

Validation is active, but this capture requires a connected target Android
device and verified model/library inputs. Do not run a capture on an emulator
to make a performance claim. Record serial/fingerprint, ABI, and OS version.
This activity is never a CI gate.

## Preparation

1. Build and install the APK and instrumentation APK that will be recorded.
2. Stage one GGUF model and one library package whose hashes were verified.
   Record the library manifest and retrieval case-file hash when applicable.
3. Block device network transport, then record the blocking method and visible
   state before the run.
4. Choose a new run ID. Never overwrite a previous report.
5. Pull every report off the device as soon as the run ends. `connectedDebugAndroidTest`
   uninstalls the app when it finishes, which deletes the staged packages and every
   report in the app data directory, and a later manual capture then has to re-stage
   about 1.5 GB before it can run.

`ManualCheck` is excluded from the normal instrumentation suite. Run only the
class or method that provides the desired evidence and pass every input
explicitly. The normal suite is not state-free: the reading-control check taps the
model chip, which needs an installed model, so a wiped device shows that check
failing for a reason that has nothing to do with the code under review.

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

Read the private report produced by the test without mutating it, and copy it to
the host folder that holds recorded results (`corpus/evaluation/results/`, kept
out of git like every other result artifact):

```powershell
& $adb -s $serial exec-out run-as $package cat "files/answer-$runId.json" > ..\..\..\corpus\evaluation\results\answer-$runId.json
```

The report names the recorded identity and the durable turn state:
`installed_apk_sha256` (the artifact under test), `application_id`, `fingerprint`,
`api`, `model_sha256`, `library_sha256`, `status`, `grounding`, `finish_reason`,
`model_support` (the support claim decoded from the response document; empty means
the completion did not return one), `follow_up_count`, `evidence_count`, `error`,
and the end-to-end timings. Stage inputs where the application process can read
them: files pushed into `/sdcard/Android/data/...` may be unreadable to the app,
while a copy under `run-as <package>` in the app data directory is not.

## Turn latency and follow-up quality

`GenerationCostTest` separates the parts of a turn on one model and prompt:
`grammarPromptAndThreadCosts` sweeps threads with and without the response grammar,
and `appPromptCostsWithAndWithoutGrammar` builds the real request from a retrieved
library and runs it constrained and unconstrained. `FollowUpJourneyTest` sends each
turn's first stored offer back as the next question, which is what tapping a chip
does, and records per turn: prompt characters, retained history pairs, retrieval
time, first visible text, end to end, evidence, offers, and the decoded support claim.

```powershell
& $adb -s $serial shell "am instrument -w -e class dev.blobs.app.GenerationCostTest#appPromptCostsWithAndWithoutGrammar -e installedModel MODEL_KEY -e installedLibrary LIBRARY_KEY -e runId cost-001 -e limit 320 -e threads 4 ${package}.test/androidx.test.runner.AndroidJUnitRunner"
& $adb -s $serial shell "am instrument -w -e class dev.blobs.app.FollowUpJourneyTest#fiveTurnsOfOfferedFollowUps -e installedModel MODEL_KEY -e installedLibrary LIBRARY_KEY -e runId journey-001 -e turns 5 -e question 'What should a household pack in an emergency kit?' ${package}.test/androidx.test.runner.AndroidJUnitRunner"
```

Pass the whole `am instrument` line as one quoted argument to `adb shell`. Quoting a
`-e question` value through `adb shell` without that outer quoting makes `am` fail
with `Invalid userId`, and dropping the value entirely leaves the default question,
which silently changes what was measured.

Three ways to record a run that measures nothing:

- `installedModel` and `installedLibrary` are the installed keys stored in the
  package table (`<id>-<version>-<sha256 prefix>`, e.g.
  `qwen35-2b-q4km-f6d5376b-aaf42c8b7c3cab2b`), not the bare manifest ids. A short id
  fails at `packages.single { }` before any token is generated; the staged
  `*-stage-*.json` pointers in app storage name the key that was installed.
- A question the staged library cannot answer retrieves no passage, and the capture
  then measures a near-empty prompt. Read `source_count` and `prompt_characters`
  from the report before quoting any per-token or end-to-end figure.
- Every capture needs a run id it has never used. Each writer refuses an existing
  report file, and reports are named by id alone, so reusing an id either stops the
  run or silently replaces the artifact a document already cites.
- Under Git Bash, `adb push <file> /data/local/tmp/` gets its destination rewritten to
  a Windows path and fails with `remote secure_mkdirs() failed`. Export
  `MSYS_NO_PATHCONV=1` (or double the leading slash) before pushing.
- To evaluate a model that is pinned but not yet in the app's catalog:
  `uv run --locked --project ../corpus python tools/prepare_model.py <lock-key>` builds
  `android/packages/<id>.blobs` from the locked bytes; `adb push` it to
  `/data/local/tmp`, `run-as <applicationId> cp` it into `files/`, then run
  `NativeInferenceTest#installedLibraryAndModelCompleteOfflineChatFlow` with
  `-e modelPackage /data/user/0/<applicationId>/files/<id>.blobs -e installedLibrary
  <library key> -e question '...' -e runId <id>`. That imports it through the normal
  package path, and the installed key is
  `<id>-<revision8>-<sha256 first 16>`, which is what `-e installedModel` takes for the
  cost and journey captures.

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
