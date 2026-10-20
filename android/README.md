# Blobs Android

Build the Kotlin/Compose app with its pinned llama.cpp runtime. To install a published APK, use [Get started](https://nonomnonom.github.io/blobs/docs/get-started/).

## Requirements

- JDK 17
- uv 0.12.19
- Android SDK platform 37 and build tools 36.0.0
- Android NDK 27.1.12297006
- CMake 3.22.1

Set `ANDROID_HOME` or create an ignored `local.properties`. Dependency versions, Gradle wrapper files, native-source revisions, model metadata, and Python dependencies are pinned in the repository.

## Build and verify

App builds do not need large corpus record shards. With Git LFS installed, clone from your workspace directory:

```powershell
git clone --no-checkout https://github.com/nonomnonom/blobs.git
git -C blobs lfs install --local --skip-smudge
git -C blobs checkout
cd blobs/android
```

From this directory:

```powershell
$env:UV_CACHE_DIR = "../corpus/.cache/uv"
uv sync --locked --project ../corpus
uv run --locked --project ../corpus python tools/fetch_runtime.py
.\gradlew.bat verify
uv run --locked --project ../corpus python tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Use `./gradlew` on Linux or macOS. The first build downloads dependencies. `verify` runs ktlint, Android lint, JVM tests, debug/release assembly, and instrumentation-test compilation.

With a device or emulator connected, run `.\gradlew.bat installDebug`. See [Contributing](../CONTRIBUTING.md#verification) for Android integration checks and optional asset-dependent tests.

## Prepare local packages

The app offers model and library downloads during setup. To prepare the pinned fallback model for local installation:

```powershell
uv run --locked --project ../corpus python tools/prepare_model.py small
```

Build the starter library using its [versioned recipe and source records](corpus/README.md), or follow [corpus preparation](../corpus/README.md) for larger collections. Import `.blobs` packages through **Library → Import a library** or **Settings → Models → Add a model file**. Package builders refuse existing outputs; generated files stay out of Git.

See [architecture](../docs/architecture.md) for storage, retrieval, and package verification; [release preparation](docs/RELEASE.md) for signing; and [third-party notices](THIRD_PARTY_NOTICES.md) for component and dataset licenses.
