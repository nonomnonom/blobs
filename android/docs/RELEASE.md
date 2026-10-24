# Android release guide

Blobs distributes the Android app through GitHub. The app's signed Hugging Face download list supplies model and library packages inside the app; those large assets do not need to be duplicated in every GitHub release.

## Settle the version and changelog

Every change merged to `main` since the last release carries a changeset, so the
release begins by consuming them. From the repository root:

```powershell
npm ci
npm run release:version
```

`changeset version` raises the version in `package.json`, moves each note into
`CHANGELOG.md`, and deletes the consumed `.changeset` files. The sync step then
writes that version to `versionName` in `app/build.gradle.kts` and advances
`versionCode` by one, because Android only accepts an update signed with the
existing key when the code increases. `npm run release:check` fails when the two
files disagree, and CI runs it on every pull request.

Choose the semver level the way you would choose the next app version: patch for
a fix, minor for new user-visible behavior, major for a breaking change such as a
library format or data transition that older releases cannot read. Commit the
result as the release commit and open it as a pull request against `main`, or
commit it directly on `main`; the consumed changesets satisfy the release-note
check. Publish the matching tag and GitHub Release after the kit verifies.

The changelog text is the starting point for the release notes, which still need
the published ZIP SHA-256 and `SHA256SUMS.txt` digest.

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

Every update to `dev.blobs.app` must use the same Android signing key as the existing public release. Configure `BLOBS_KEYSTORE`, `BLOBS_STORE_PASSWORD`, `BLOBS_KEY_PASSWORD`, `BLOBS_KEY_ALIAS`, and `BLOBS_CERT_SHA256` in the protected local release environment. `BLOBS_CERT_SHA256` is the 64-character SHA-256 fingerprint of the signer certificate from an already trusted public Blobs APK, not a fingerprint copied from the candidate being released. Release 0.4.0 is the first release signed with the replacement key created on 2026-10-25 after the 0.3.14 key was found unrecoverable; its certificate fingerprint is `626e4f7f02a11f74ddcab001c79a340c1e47918184bab606946b20cb96a444e3`, and every later update must match it. Then run:

```powershell
.\gradlew.bat :app:assembleRelease
uv run --locked --project ../corpus python tools/verify_apk.py app/build/outputs/apk/release/app-release.apk --certificate-sha256 $env:BLOBS_CERT_SHA256
```

The kit takes one raw GGUF model and one `.blobs` library, with filenames that exactly match `app/src/main/assets/package-catalog.json`. Download the pinned files the catalog names; for the GGUF, the catalog URL and `manifest.origin` must point at the same 40-character upstream commit:

```powershell
hf download LiquidAI/LFM2-1.2B-GGUF `
  --revision 5399e76c648f4eb8c053feb1ab747277dea5bf8b `
  --include "LFM2-1.2B-Q4_K_M.gguf" `
  --local-dir .local\model-catalog-assets\lfm2
uv run --locked --project ../corpus python tools/package_release.py --model .\.local\model-catalog-assets\lfm2\LFM2-1.2B-Q4_K_M.gguf --library .\.local\model-catalog-assets\wikivoyage\packages\wikivoyage-en-2026-09.5.blobs --certificate-sha256 $env:BLOBS_CERT_SHA256
```

The assembler checks the GGUF header, byte count, and SHA-256 against both the catalog download metadata and its manifest, and it still verifies the `.blobs` library through the package manifest.

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

The 0.4.0 release is the one documented exception. The 0.3.14 signing key is not on this host, in Git history, or in any published artifact, so 0.4.0 starts a new identity. Users on 0.3.14 must export their conversations before uninstalling; the new APK cannot install over the old one, and the release notes say so.

`tools/release.ps1` deliberately refuses to generate or select a key for you.
It requires all five protected settings, verifies that `BLOBS_KEYSTORE` names a
file, and compares the candidate signer certificate to `BLOBS_CERT_SHA256`
before assembly. The assembler repeats that certificate check, so a direct
assembler invocation cannot bypass the update-key invariant. This makes
accidental first-use keys impossible in the normal update workflow.
