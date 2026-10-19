# Blobs

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/banner-dark.png">
  <img src="docs/assets/banner-light.png" alt="Blobs. Stay curious. Go anywhere. Offline research, right on your phone. Android, compatible with GrapheneOS." width="1200">
</picture>

Blobs is an offline Android research app compatible with GrapheneOS. Search a local knowledge library, ask questions, and read the sources inside the app. Conversations, documents, and model inference stay on your phone. No account or Google Play Services is required.

**[Download the Android app](https://github.com/nonomnonom/blobs/releases/latest)** · **[Read the guides](https://nonomnonom.github.io/blobs/)** · **[See the app](docs/web/public/screens/)**

## Get started

1. Download the APK from the latest release and open it on Android 9 or later. Allow installation from your browser or file manager when Android asks. ARM64 and x86_64 are supported.
2. Open Blobs. Review the model recommended for your phone, then choose **Download**. You can compare other models before choosing.
3. Download the starter library and finish setup. Optional collections are available from **Library → Discover more**.
4. Ask your first question. Once a model and library are installed, chat, library search, citations, and source reading work offline.

Only downloads and checking for new models or libraries need a connection. They start when you choose them. Installation verifies the package before activating it. Model and library sizes, languages, sources, and licenses are listed in the app and the [guides](https://nonomnonom.github.io/blobs/docs/library/).

You can install a newer APK over an existing version signed with the same certificate. Keep the app installed to preserve private conversations and downloaded libraries. Release checksums and the signing certificate fingerprint accompany every APK.

The active implementation is in [`android/`](android/README.md). Reproducible corpus preparation and evaluation tools are in [`corpus/`](corpus/README.md). The adopted asset distribution design is documented in [`docs/architecture.md`](docs/architecture.md).

The English documentation website is in [`docs/web/`](docs/web/README.md), built with Next.js and Fumadocs. It includes setup, models, libraries, privacy, troubleshooting, and contributor guides.

## What is implemented

- First-run setup for downloading and verifying a model and knowledge library in the app. Add documents or import a library from Library; manage models in Settings.
- Native CPU inference through a pinned llama.cpp revision.
- SQLite FTS5 retrieval with immutable source evidence.
- Streamed answers with source citations and in-app article reading.
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

The first preparation/build needs network access on the development computer. During first-run setup, users choose when to download the model and library in the app; once both are installed, chat and research work offline. Local package installation in Library and Settings → Models supports air-gapped setup.

See the [Android build and import guide](android/README.md) and [corpus preparation guide](corpus/README.md).
For code ownership, naming, checks and source delivery, see [Contributing](CONTRIBUTING.md).

## Repository layout

| Path | Purpose |
| --- | --- |
| `android/` | Kotlin/Compose app, JNI runtime, package tools, tests, and Android notices |
| `android/corpus/` | Versioned source records for the in-app starter library |
| `corpus/` | uv-managed corpus extraction, normalization, packaging, evaluation, Git LFS records, and data notices |
| `docs/` | Product architecture and the Next.js/Fumadocs website in `docs/web/` |
| `.github/` | CI, dependency updates, and pull-request template |

## Asset distribution

GitHub hosts the source and Android releases. Hugging Face hosts the signed download list and Blobs packages; Gemma weights remain in their upstream model repositories. Users can check for new models and libraries and download selected assets in the app. Installed models, libraries, chats, and retrieval stay on device. See [`docs/architecture.md`](docs/architecture.md) for the current distribution design.

## License

Original Blobs source code, tooling and documentation are licensed under [Apache-2.0](LICENSE). Original Blobs artwork and branding are excluded from that grant; see [NOTICE](NOTICE). Third-party components, model weights and datasets retain their own licenses; see [`android/THIRD_PARTY_NOTICES.md`](android/THIRD_PARTY_NOTICES.md) and [`corpus/THIRD_PARTY_NOTICES.md`](corpus/THIRD_PARTY_NOTICES.md).

Report ordinary bugs or feature requests through [GitHub issues](https://github.com/nonomnonom/blobs/issues). For security vulnerabilities, use [private reporting](SECURITY.md). Participation follows the [community code of conduct](CODE_OF_CONDUCT.md).
