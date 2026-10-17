# Blobs

Blobs is an offline Android research assistant. It searches a local knowledge library, runs a local language model, and keeps conversations, source passages, and settings on the device. The app has no `INTERNET` permission, account requirement, cloud API, or Google Play Services dependency.

The active implementation is in [`android/`](android/README.md). Reproducible corpus preparation and evaluation tools are in [`corpus/`](corpus/README.md).

## What is implemented

- First-run setup for importing local model and knowledge packages.
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

The first preparation/build needs network access on the development computer. The installed app and imported packages operate offline.

See the [Android build and import guide](android/README.md), [architecture](android/docs/ARCHITECTURE.md), [testing](android/docs/TESTING.md), and [release guide](android/docs/RELEASE.md).

## Repository layout

| Path | Purpose |
| --- | --- |
| `android/` | Kotlin/Compose app, JNI runtime, package tools, tests, and Android notices |
| `corpus/` | uv-managed corpus extraction, normalization, packaging, evaluation, and data notices |
| `.github/` | CI, dependency updates, and pull-request template |

## Distribution model

The APK is kept small and offline. Model and library payloads are separate `.blobs` files that users select through Android's document picker. Blobs validates package structure, declared sizes, checksums, and compatibility before activation. A checksum detects corruption; it does not authenticate a publisher, so package provenance must be communicated by the distributor.

## License

No license is granted for the original Blobs application code or artwork at this time. Third-party components and datasets retain their own licenses; see [`android/THIRD_PARTY_NOTICES.md`](android/THIRD_PARTY_NOTICES.md) and [`corpus/THIRD_PARTY_NOTICES.md`](corpus/THIRD_PARTY_NOTICES.md).
