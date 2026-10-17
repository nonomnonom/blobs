# Release guide

Blobs is distributed as an APK plus optional model and library `.blobs` packages. Packages stay separate so users can choose local assets and the APK remains small.

## Build a candidate

From `android/`, prepare pinned dependencies and run the full gate:

```powershell
$env:UV_CACHE_DIR = "../corpus/.cache/uv"
uv sync --locked --project ../corpus
uv run --locked --project ../corpus python tools/fetch_runtime.py
.\gradlew.bat verify
uv run --locked --project ../corpus python tools/verify_apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

CI intentionally produces unsigned artifacts. Sign a public candidate in a protected release environment with a stable key. Keep the key and recoverable password outside Git and outside the distributable folder.

## Assemble a local kit

On Windows, `tools/release.ps1` runs the host and Android gates, verifies the APK, signs it using local protected configuration, and optionally assembles an immutable kit:

```powershell
.\tools\release.ps1 -Model .\packages\qwen3-small.blobs -Library .\packages\wikipedia-starter.blobs
```

Use `-CreateKey` only for the initial local signing setup. For another signing environment, set `BLOBS_KEYSTORE`, `BLOBS_STORE_PASSWORD`, `BLOBS_KEY_PASSWORD`, and `BLOBS_KEY_ALIAS`, then run `:app:assembleRelease`.

For manual assembly after testing and signing:

```powershell
uv run --locked --project ../corpus python tools/package_release.py --model PATH_TO_MODEL.blobs --library PATH_TO_LIBRARY.blobs
```

The assembler refuses debug application IDs, unsigned APKs, incomplete package pairs, and an existing destination. It writes to a temporary directory, streams package verification, records an exact inventory and a `FOOTPRINT.json` with the selected assets' distribution, installed, and modeled import-peak bytes, then atomically publishes the completed edition.

## Verify after transport

Run the verifier included in the source tree or assembled kit:

```powershell
uv run --locked --project ../corpus python tools/verify_release.py PATH_TO_RELEASE_FOLDER
```

It rejects missing, extra, renamed, or changed files; verifies package payload CRC, size, and SHA-256; recomputes the storage and import peaks from the delivered APK and package payloads; checks recorded APK gates; and validates source-archive composition. The footprint includes only measured APK/package bytes and modeled import staging; it excludes Android app data, conversations, filesystem overhead, device RAM, and performance. Publish the reported `SHA256SUMS.txt` digest beside a release so reviewers have an independent inventory anchor.

Also inspect signing identity with `apksigner verify --verbose --print-certs` and rerun `tools/verify_apk.py` against the distributed APK.

## Install

1. Copy the APK and selected packages to local device storage.
2. Install the APK through Android's package installer.
3. Open Blobs and choose **Add from this phone**.
4. Review and add a model package, then a library package.
5. Start a conversation and open a citation to verify local reading.

Imports temporarily require the source file, staging copy, and installed payload. Keep the headroom shown by the app until installation completes. Removing the app removes private conversations and installed packages; export conversations and retain package backups before uninstalling.

Android 9 or later on ARM64 or x86_64 is the supported platform boundary. Model speed and memory use depend on the exact device, model, context size, Android build, and thermal state. Verify sustained inference on representative hardware before broad distribution.
