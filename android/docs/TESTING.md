# Testing

Run Android commands from `android/`. Use `gradlew.bat` on Windows or `./gradlew` on Linux/macOS.

| Check | Command | Purpose |
| --- | --- | --- |
| Complete local gate | `gradlew verify` | Formatting, lint, JVM tests, debug/release assembly, instrumentation compilation |
| Android behavior | `gradlew connectedDebugAndroidTest` | Room migration, drafts, cancellation, package import, FTS5, preferences, recreation, and UI flows |
| APK structure | `uv run --locked --project ../corpus python tools/verify_apk.py APK` | Permissions, ABI coverage, ELF and ZIP page alignment |
| Corpus tooling | `uv run --locked --project ../corpus python -m unittest discover -s ../corpus/tests` | Extraction, retrieval, packaging, validation, and evaluation helpers |
| Release kit | `uv run --locked --project ../corpus python tools/verify_release.py PATH` | Inventory, checksums, package payloads, APK gates, and source composition |

Connected tests may uninstall the debug app, so use a disposable emulator. Ordinary test runs do not need model weights and never download them.

## Real local inference

Prepare model and library packages, copy them to a disposable emulator, install the debug and test APKs, then run the explicit instrumentation class:

```sh
adb shell am instrument -w \
  -e class dev.blobs.app.NativeInferenceTest#installedLibraryAndModelProduceSavedCitedAnswer \
  -e modelPackage /data/local/tmp/blobs-model.blobs \
  -e libraryPackage /data/local/tmp/blobs-library.blobs \
  dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The test checks native streaming, persisted source identity, saved evidence, and draft preservation. It does not prove that every generated claim is supported. Review factual support and citation placement manually for release candidates.

For UI changes, exercise onboarding, import review, chat, history, library, article reading, settings, dark mode, keyboard/insets, TalkBack, and 200% font size. Device and emulator measurements apply only to the tested hardware, ABI, Android version, memory, and thermal state.
