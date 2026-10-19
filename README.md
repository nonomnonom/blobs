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

Model weights, generated `.blobs` packages, APKs, signing keys, and local caches are excluded from Git. Existing reviewed corpus editions use Git LFS; the current travel records are also published at a pinned Hugging Face revision. Selected benchmark evidence is versioned alongside the tools and lock files needed to reproduce it. App users download installable assets from Hugging Face through Blobs.

## Build

Requirements: JDK 17, uv 0.12.19, Android SDK platform 37, build tools 36.0.0, NDK 27.1.12297006, and CMake 3.22.1.

For an app build, clone the source without downloading corpus record shards. With Git LFS installed, these commands configure only the new checkout:

```powershell
git clone --no-checkout https://github.com/nonomnonom/blobs.git
git -C blobs lfs install --local --skip-smudge
git -C blobs checkout
cd blobs
```

The LFS pointers can remain in place for app builds and unit tests. Corpus maintainers can fetch a selected edition using the [corpus guide](corpus/README.md#fetch-reviewed-records).

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
For code ownership, naming, checks and source delivery, see [Contributing](CONTRIBUTING.md).

## Repository layout

| Path | Purpose |
| --- | --- |
| `android/` | Kotlin/Compose app, JNI runtime, package tools, tests, and Android notices |
| `android/corpus/` | Versioned source records for the in-app starter library |
| `corpus/` | uv-managed corpus extraction, normalization, packaging, evaluation, Git LFS records, and data notices |
| `docs/` | Product architecture and asset distribution decisions |
| `.github/` | CI, dependency updates, and pull-request template |

## Asset distribution

GitHub hosts the source and Android releases. Hugging Face hosts the signed download list and Blobs packages; Gemma weights remain in their upstream model repositories. Users can check for catalog updates and download selected assets in the app. Installed models, libraries, chats, and retrieval stay on device. See [`docs/architecture.md`](docs/architecture.md) for the current distribution design.

## License

Original Blobs source code, tooling and documentation are licensed under [Apache-2.0](LICENSE). Original Blobs artwork and branding are excluded from that grant; see [NOTICE](NOTICE). Third-party components, model weights and datasets retain their own licenses; see [`android/THIRD_PARTY_NOTICES.md`](android/THIRD_PARTY_NOTICES.md) and [`corpus/THIRD_PARTY_NOTICES.md`](corpus/THIRD_PARTY_NOTICES.md).

Report ordinary bugs or feature requests through [GitHub issues](https://github.com/nonomnonom/blobs/issues). For security vulnerabilities, use [private reporting](SECURITY.md). Participation follows the [community code of conduct](CODE_OF_CONDUCT.md).
