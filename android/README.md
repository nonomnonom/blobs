# Blobs Android

Native offline research chat built with Kotlin, Jetpack Compose, Room, DataStore, SQLite FTS5, and llama.cpp. The app imports local model and library packages, retrieves source passages, streams answers, and opens citations without leaving the app.

The APK has no Google Play Services dependency. First-run setup offers one-time, user-started downloads for the pinned model and knowledge library. The app verifies the complete package archive and its manifest before using the existing package installer. Chat, retrieval, inference, citations, and reading make no network requests. Local `.blobs` import remains available for air-gapped setup and alternate packages.

## Requirements

- JDK 17
- uv 0.12.19
- Android SDK platform 37 and build tools 36.0.0
- Android NDK 27.1.12297006
- CMake 3.22.1

Set `ANDROID_HOME` or create an ignored `local.properties`. Dependency versions, Gradle wrapper files, native-source revisions, model metadata, and Python dependencies are pinned in the repository.

## Build and verify

From this directory:

```powershell
$env:UV_CACHE_DIR = "../corpus/.cache/uv"
uv sync --locked --project ../corpus
uv run --locked --project ../corpus python tools/fetch_runtime.py
.\gradlew.bat verify
uv run --locked --project ../corpus python tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Use `./gradlew` on Linux or macOS. `verify` runs formatting checks, lint, JVM tests, debug/release assembly, and instrumentation-test compilation. Run `connectedDebugAndroidTest` on a disposable emulator when platform behavior changes.

## Prepare local packages

The builders never download implicitly. `prepare_model.py` downloads only the selected pinned model and verifies its exact size and SHA-256 before packaging it.

```powershell
uv run --locked --project ../corpus python tools/prepare_model.py small
uv run --locked --project ../corpus python tools/build_package.py library corpus/starter.documents.jsonl packages/wikipedia-starter.blobs --id wikipedia-starter --version 2026-10-17.3 --title "Science and everyday questions" --license "CC BY-SA 4.0" --origin "https://en.wikipedia.org/"
```

Generated packages are ignored by Git. The recommended first-run packages are offered directly in setup. Alternate packages can be imported from the welcome flow or **Settings → Models and libraries → Add from this phone**. Every package goes through the same manifest, payload hash, compatibility, storage, and SQLite/GGUF validation before activation.

## Code ownership

| Path | Responsibility |
| --- | --- |
| `app/src/main/kotlin/dev/blobs/app/feature/` | Chat, history, library, settings, and welcome screens |
| `app/src/main/kotlin/dev/blobs/app/ui/` | Shared components, theme, navigation, and product state |
| `app/src/main/kotlin/dev/blobs/app/data/local/` | Room conversations, turns, package records, and migrations |
| `app/src/main/kotlin/dev/blobs/app/data/library/` | Read-only FTS5 retrieval and immutable evidence |
| `app/src/main/kotlin/dev/blobs/app/data/packages/` | Streaming validation, staging, activation, pause, and resume |
| `app/src/main/kotlin/dev/blobs/app/data/WorkController.kt` | Single owner for generation and package/data mutations |
| `app/src/main/cpp/` and `inference/` | JNI boundary and pinned local inference runtime |
| `tools/` | Runtime preparation, package/APK verification, signing, and release assembly |

Read the [architecture](docs/ARCHITECTURE.md), [engineering rules](docs/ENGINEERING.md), [testing guide](docs/TESTING.md), [release guide](docs/RELEASE.md), and [third-party notices](THIRD_PARTY_NOTICES.md).
