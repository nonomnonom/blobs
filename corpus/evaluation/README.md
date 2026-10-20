# Optional evaluation tools

Use these tools when investigating a specific retrieval or performance issue. They are separate from normal [application verification](../../CONTRIBUTING.md#verification) and do not run inside the Android app. Run Python commands from `corpus/` and keep output in ignored `work/`, `reports/`, or `evaluation/results/` directories.

## Choose the check you need

| Question | Tool or Android test |
| --- | --- |
| Does a package retrieve the expected articles? | `evaluation.retrieval.retrieval_smoke` |
| Does host replay match Android's selected passages? | `evaluation.retrieval.host_replay` |
| How does retrieval behave across installed libraries? | `MultiLibraryRetrievalScalingTest` |
| How long does app launch take? | `evaluation.android.app_startup` |
| How does chat start, stop, and preserve drafts? | `evaluation.android.app_performance` |
| Where is corpus build time spent? | `evaluation.retrieval.profile_build` |
| Where is native inference time spent? | [Native measurements](native/README.md) |

Each Python module supports `--help`. Authored inputs live in [`cases/`](cases/) and pinned fixture identities in [`locks/`](locks/). Reports identify the code, APK, device, model, library, and case set used; keep baseline and candidate inputs comparable.

## Check a library on the host

Supply the exact package expected by the case file:

```powershell
uv run --locked python -m evaluation.retrieval.retrieval_smoke `
  --library-package PATH_TO/wikivoyage-en-2026-09-v3.blobs `
  --cases evaluation/cases/travel-retrieval-smoke-v2.json `
  --work-directory work/travel-retrieval `
  --output reports/travel-retrieval.json
```

This checks retrieval against known inputs. Host timings do not predict phone performance, and finding an article does not establish the correctness of a generated answer.

## Check multiple libraries on Android

From the repository root, stage the exact catalog packages required by the case file:

```powershell
uv run --locked --project corpus python android/tools/stage_multi_library_benchmark.py `
  --cases corpus/evaluation/cases/multi-library-retrieval-scaling-v5.json `
  --catalog android/app/src/main/assets/package-catalog.json `
  --output-dir android/.local/hf-publish/packages
```

This command downloads missing or invalid packages. Install the debug app and instrumentation APK, copy the case file to `/sdcard/Android/data/dev.blobs.app.debug/files/retrieval-benchmark-multi-library.json`, and copy its package files into the same directory. Run `MultiLibraryRetrievalScalingTest` through `adb shell am instrument -w -e class dev.blobs.app.MultiLibraryRetrievalScalingTest dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner`. Join ordered `REPORT_PART:i/n:` log chunks to recover the JSON report.

## Measure launch time

With the app installed and the selected device connected:

```powershell
uv run --locked python -m evaluation.android.app_startup `
  --adb "$env:ANDROID_HOME/platform-tools/adb.exe" `
  --serial emulator-5554 --package dev.blobs.app `
  --runs 7 --output reports/android-startup.json
```

The tool force-stops the app between launches but leaves OS caches warm. Its PSS samples describe launch memory, not peak inference memory. Use a fresh output path for each capture.

## Inspect generated answers

`PublicResearchWorkflowTest#questionsThroughChatAndSourceReader` is an opt-in UI capture for supplied public questions; `NativeInferenceTest#publicFixtureAnswerEvaluation` captures answer text and passages from a verified local fixture. These content-bearing reports belong apart from performance reports and may contain conversation text. Use only material you intend to record.

Inspect delivered answers against their actual passages and an identified reference. Record omissions, unsupported claims, and outdated material. A passing UI test or `COMPLETE` state proves workflow behavior, not factual accuracy. Neither self-authored examples nor emulator timing establishes frontier-model utility or compatibility with every phone.
