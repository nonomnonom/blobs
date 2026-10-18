# Blobs

Blobs is an offline Android research app compatible with GrapheneOS. It searches a local knowledge library, runs a local language model, and keeps conversations, source passages, and settings on the device. First-run setup can download the pinned model and library packages after the user taps Download; chat, search, inference, citations, and storage do not make network requests. Blobs has no account, hosted inference dependency, or Google Play Services dependency.

The active implementation is in [`android/`](android/README.md). Reproducible corpus preparation and evaluation tools are in [`corpus/`](corpus/README.md). The adopted asset distribution design is documented in [`docs/architecture.md`](docs/architecture.md).

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
| `android/corpus/` | Versioned source records for the in-app starter library |
| `corpus/` | uv-managed corpus extraction, normalization, packaging, evaluation, Git LFS records, and data notices |
| `docs/` | Product architecture and asset distribution decisions |
| `.github/` | CI, dependency updates, and pull-request template |

## Asset distribution

The adopted architecture keeps source and build automation on GitHub while Hugging Face hosts model and corpus assets and a small, curated asset catalog. The app now supports signed online catalog refresh, offline cache fallback, and explicit user-started downloads. The new Hugging Face namespace and corpus migration are still pending, so the released package list continues to use its bundled catalog. Installed models, libraries, chats, and retrieval stay on device. See [`docs/architecture.md`](docs/architecture.md) for implementation status and migration steps.

## License

No license is granted for the original Blobs application code or artwork at this time. Third-party components and datasets retain their own licenses; see [`android/THIRD_PARTY_NOTICES.md`](android/THIRD_PARTY_NOTICES.md) and [`corpus/THIRD_PARTY_NOTICES.md`](corpus/THIRD_PARTY_NOTICES.md).
