# Blobs Android

Offline Android research chat compatible with GrapheneOS, built with Kotlin, Jetpack Compose, Room, DataStore, SQLite FTS5, and llama.cpp. The app imports local model and library packages, retrieves source passages, streams answers, and opens citations without leaving the app.

The APK has no Google Play Services dependency. First-run setup offers user-started downloads for pinned model weights and a starter knowledge library. It verifies every downloaded byte against the bundled catalog's pinned size and SHA-256. `.blobs` packages also validate their manifest; direct GGUF model downloads pass the GGUF validator before atomic installation. Chat, retrieval, inference, citations, and reading make no network requests. Local `.blobs` import remains available later in Settings for air-gapped setup and alternate packages. The target remote-catalog and Hugging Face asset architecture, including what is not implemented yet, is documented in [`../docs/architecture.md`](../docs/architecture.md).

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

Use `./gradlew` on Linux or macOS. `verify` runs formatting checks, lint, JVM tests, debug/release assembly, and instrumentation-test compilation. Run `connectedDebugAndroidTest` for Android platform integration checks.

## Prepare local packages

The builders never download implicitly. `prepare_model.py` downloads only the selected pinned fallback model and verifies its exact size and SHA-256 before packaging it. First-run Gemma 4 model downloads use the exact repository revision, file size, and SHA-256 pinned in `app/src/main/assets/package-catalog.json`.

```powershell
uv run --locked --project ../corpus python tools/prepare_model.py small
uv run --locked --project ../corpus python tools/build_package.py library corpus/starter.documents.jsonl packages/wikipedia-starter.blobs --id wikipedia-starter --version 2026-10-17.3 --title "Science and everyday questions" --license "CC BY-SA 4.0" --origin "https://en.wikipedia.org/"
```

Generated packages are ignored by Git. The recommended first-run models and libraries are offered directly in setup. Alternate `.blobs` packages can be imported from **Settings → Models and libraries → Add from this phone**. Every download goes through pinned byte count and hash checks; `.blobs` packages also validate manifests, and all payloads pass storage and SQLite/GGUF validation before activation.

Third-party component and dataset licenses are listed in [third-party notices](THIRD_PARTY_NOTICES.md).
