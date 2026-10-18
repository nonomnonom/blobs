# Blobs

Blobs is an offline Android research assistant. It searches a local knowledge library, runs a local language model, and keeps conversations, source passages, and settings on the device. First-run setup can download the pinned model and library packages after the user taps Download; chat, search, inference, citations, and storage do not make network requests. Blobs has no account, cloud API, or Google Play Services dependency.

The active implementation is in [`android/`](android/README.md). Reproducible corpus preparation and evaluation tools are in [`corpus/`](corpus/README.md).

## What is implemented

- First-run setup for downloading and verifying a model and knowledge library in the app. Local package import is available later in Settings.
- Native CPU inference through a pinned llama.cpp revision.
- SQLite FTS5 retrieval with immutable source evidence.
- Streamed answers with `[S1]` source references and in-app article reading.
- Conversation history, drafts, cancellation, export, appearance, storage, privacy, and help.
- Bounded package validation, resumable import, atomic activation, and rollback-safe failure handling.
- Android, JVM, Python, package, APK, and release-kit verification.

Model weights, generated `.blobs` packages, APKs, signing keys, local caches, and evaluation reports are intentionally excluded from Git. The repository contains the tools and lock files needed to reproduce and verify them.

## Build

Requirements: JDK 17, uv 0.12.19, Android SDK platform 37, build tools 36.0.0, NDK 27.1.12297006, and CMake 3.22.1.

```powershell
cd corpus
$env:UV_CACHE_DIR = "$PWD/.cache/uv"
uv sync --locked

cd ../android
uv run --locked --project ../corpus python tools/fetch_runtime.py
.\gradlew.bat verify
```

The first preparation/build needs network access on the development computer. During first-run setup, users choose when to download the model and library in the app; once both are installed, chat and research work offline. Local package installation in Settings supports air-gapped setup.

See the [Android build and import guide](android/README.md) and [corpus preparation guide](corpus/README.md).

## Repository layout

| Path | Purpose |
| --- | --- |
| `android/` | Kotlin/Compose app, JNI runtime, package tools, tests, and Android notices |
| `corpus/` | uv-managed corpus extraction, normalization, packaging, evaluation, and data notices |
| `.github/` | CI, dependency updates, and pull-request template |

## Distribution model

The APK is kept small. On first run, users can download the pinned model and library `.blobs` packages from the matching public GitHub release. Blobs checks the HTTPS source, exact archive size and SHA-256, then passes each package through the same manifest, payload, compatibility, and storage validation used for local imports. Downloads run only after an explicit tap; after setup, research works offline. Local package import remains available later in Settings for air-gapped setup and other editions. The release must publish the exact filenames and hashes pinned in `PackageCatalog.kt`.

## License

No license is granted for the original Blobs application code or artwork at this time. Third-party components and datasets retain their own licenses; see [`android/THIRD_PARTY_NOTICES.md`](android/THIRD_PARTY_NOTICES.md) and [`corpus/THIRD_PARTY_NOTICES.md`](corpus/THIRD_PARTY_NOTICES.md).
