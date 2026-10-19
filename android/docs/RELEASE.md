# Android release guide

Blobs distributes the Android app through GitHub. The app's signed Hugging Face download list supplies model and library packages inside the app; those large assets do not need to be duplicated in every GitHub release.

## Build and verify a candidate

From `android/`, prepare the pinned runtime and run the complete local verification gate:

```powershell
$env:UV_CACHE_DIR = "../corpus/.cache/uv"
uv sync --locked --project ../corpus
uv run --locked --project ../corpus python tools/fetch_runtime.py
.\gradlew.bat verify
uv run --locked --project ../corpus python tools/verify_apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

Run `connectedDebugAndroidTest` on an emulator for platform checks. The manual HF integration checks `LaunchTest#starterLibraryFromHubAppearsInYourLibrary` and `LaunchTest#emergencyPreparednessLibraryFromHubAppearsInYourLibrary` verify the live signed list, package downloads, installation, and Library screen.

## Sign and assemble

Every update to `dev.blobs.app` must use the same Android signing key as the existing public release. Configure `BLOBS_KEYSTORE`, `BLOBS_STORE_PASSWORD`, `BLOBS_KEY_PASSWORD`, and `BLOBS_KEY_ALIAS` in the protected local release environment, then run:

```powershell
.\gradlew.bat :app:assembleRelease
uv run --locked --project ../corpus python tools/verify_apk.py app/build/outputs/apk/release/app-release.apk
uv run --locked --project ../corpus python tools/package_release.py --model .\packages\qwen3-integration.blobs --library .\packages\wikipedia-starter-schema3.blobs
```

The release assembler checks APK identity and signature, exact package metadata, source archive contents, footprint, and a complete SHA-256 inventory. It creates an immutable `android/release/<version>/` folder.

The APK version and download-list edition advance independently. An application update can reuse an unchanged model/library edition; the assembler still requires the exact catalog filenames, archive sizes, SHA-256 hashes, and manifests. Do not change or republish the signed download list solely to match an APK version. Verify the folder before publication:

```powershell
uv run --locked --project ../corpus python tools/verify_release.py .\release\<version>
```

Create a complete offline ZIP outside the immutable release folder:

```powershell
uv run --locked --project ../corpus python tools/archive_release.py `
  .\release\<version> .\.local\blobs-<version>-offline-kit.zip
```

The archiver first verifies the release kit, then checks every archived file against the verified checksum inventory. It preserves the nested license files, refuses existing output paths, and publishes the ZIP only after successful verification. The JSON output includes the archive size, archive SHA-256, and `SHA256SUMS.txt` digest.

Publish the APK, source archive, and complete offline ZIP to a GitHub Release matching the APK version. Publish both the ZIP SHA-256 and the `SHA256SUMS.txt` digest in the release notes. Direct users to extract the ZIP and follow its `VERIFY.md` and `INSTALL.md`; uploading only the top-level kit files omits the required license directory. ZIP byte hashes may differ across filesystem metadata or entry ordering even when the checked file contents are identical; always publish the digest of the actual uploaded ZIP.

Never generate a replacement signing key for an existing application identity unless the release intentionally requires users to uninstall their current app. Android will reject an APK signed by a different key as an update; uninstalling removes private conversations and installed packages. Keep the signing key and its recoverable password outside Git and all release artifacts.
