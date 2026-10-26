# Blobs 0.5.0 Release Documentation

**Release Date**: 2026-10-26
**Version**: 0.5.0 (versionCode 20)
**Status**: Production Ready ✅

## Release Assets

### Signed APK
- **File**: `blobs-0.5.0-release.apk`
- **Size**: 31 MB
- **SHA-256**: `e3e1ab7a9677357405d8ab059b5dc1daff776f8d82dc6f0beb09856aec604b06`
- **Download**: [GitHub Release v0.5.0](https://github.com/nonomnonom/blobs/releases/tag/v0.5.0)

### Signing Certificate
- **SHA-256**: `77:81:8F:69:02:8E:7D:5E:7E:D1:C3:79:1E:1C:CF:91:63:62:06:9A:6A:3F:37:39:31:5F:80:B5:84:E3:F6:14`
- **Keystore**: `android/blobs-release-keystore.jks` (keep secure!)
- **Alias**: `blobs-key`
- **Validity**: 10,000 days (~27 years)

## Installation

### For End Users

1. **Download APK** from [GitHub Releases](https://github.com/nonomnonom/blobs/releases/tag/v0.5.0)
2. **Open on device** (Android 9+ / API 28+, ARM64 or x86_64)
3. **Allow installation** from browser/file manager when prompted
4. **Open Blobs app**
5. **Download model** (app recommends based on device RAM)
6. **Download starter library** (Wikivoyage English)
7. **Ask questions** — works fully offline after setup

### Upgrade Paths

**From 0.4.0**: Installs in place, no data loss
**From 0.3.14 or older**:
1. Export conversations from old app
2. Uninstall old app
3. Install 0.5.0
4. Import conversations

## What's New

### Performance (0.4.0 → 0.5.0)
- **Turn time**: 97.6s → 25.5-28.9s median (3× faster)
- **Prompt bound**: real 6,000 characters with history shedding
- **Answer bound**: 320 → 200 tokens
- **Context capacity**: fixed KV cache doubling issue
- **Turn attribution**: per-stage native timing

### Features
- Chat shows retrieved sources on screen while model writes
- Download list revision 2026102502: Qwen2.5 0.5B Q4_K_M for 2GB devices
- Per-stage native timings carried across JNI
- Prompt sheds retained history before cutting passages

### Technical Changes
68 commits including:
- Speed governor wave-a implementation
- Offline research engine features
- Prompt and answer bounding
- Context capacity fixes
- Turn attribution system

## Verification

### Build Verification
```bash
cd android
bash ./gradlew verify
# Expected: BUILD SUCCESSFUL (163 tasks)
```

### APK Signature Verification
```bash
uv run --locked --project corpus python android/tools/verify_apk.py \
  android/app/build/outputs/apk/release/app-release.apk \
  --certificate-sha256 77818f69028e7d5e7ed1c3791e1ccf916362069a6a3f3739315f80b584e3f614
# Expected: PASS
```

### Test Results
- ✅ Android build: PASS
- ✅ Corpus tests: PASS (84 tests)
- ✅ Android tools tests: PASS
- ✅ Documentation build: PASS
- ✅ All CI checks: GREEN

## Release Process (For Maintainers)

### Creating Future Releases

1. **Update version** (automatic via changesets):
   ```bash
   npm run release:version
   ```

2. **Build release APK**:
   ```bash
   cd android
   export BLOBS_KEYSTORE=$(pwd)/blobs-release-keystore.jks
   export BLOBS_STORE_PASSWORD=blobs2026release
   export BLOBS_KEY_ALIAS=blobs-key
   export BLOBS_KEY_PASSWORD=blobs2026release
   bash ./gradlew :app:assembleRelease
   ```

3. **Verify APK**:
   ```bash
   cd ..
   uv run --locked --project corpus python android/tools/verify_apk.py \
     android/app/build/outputs/apk/release/app-release.apk \
     --certificate-sha256 77818f69028e7d5e7ed1c3791e1ccf916362069a6a3f3739315f80b584e3f614
   ```

4. **Create GitHub release**:
   ```bash
   git tag -a v0.X.0 -m "Release 0.X.0"
   git push origin v0.X.0
   gh release create v0.X.0 --title "Blobs 0.X.0" --notes "..."
   gh release upload v0.X.0 android/app/build/outputs/apk/release/app-release.apk#blobs-0.X.0-release.apk
   ```

### Optional: Offline Kit

For users who want everything pre-packaged (no in-app downloads):

```bash
# Download model and library (1-2GB each)
hf download LiquidAI/LFM2-1.2B-GGUF \
  --revision 5399e76c648f4eb8c053feb1ab747277dea5bf8b \
  --include "LFM2-1.2B-Q4_K_M.gguf" \
  --local-dir .local/model-catalog-assets/lfm2

# Create release package
uv run --locked --project corpus python android/tools/package_release.py \
  --model .local/model-catalog-assets/lfm2/LFM2-1.2B-Q4_K_M.gguf \
  --library .local/model-catalog-assets/wikivoyage/packages/wikivoyage-en-2026-09.5.blobs \
  --certificate-sha256 77818f69028e7d5e7ed1c3791e1ccf916362069a6a3f3739315f80b584e3f614

# Create offline ZIP
uv run --locked --project corpus python android/tools/archive_release.py \
  android/release/0.5.0 \
  .local/blobs-0.5.0-offline-kit.zip
```

## Security

### Keystore Security
- **Location**: `android/blobs-release-keystore.jks`
- **Status**: Added to `.gitignore` (not in version control)
- **Backup**: Store securely offline (password manager, encrypted drive)
- **Access**: Only product owner should have keystore password

### Certificate Pinning
- All future releases MUST use the same certificate
- Certificate SHA-256: `77:81:8F:69:02:8E:7D:5E:7E:D1:C3:79:1E:1C:CF:91:63:62:06:9A:6A:3F:37:39:31:5F:80:B5:84:E3:F6:14`
- Android will reject updates signed with different certificate

## Support

- **Documentation**: https://nonomnonom.github.io/blobs/
- **Issues**: https://github.com/nonomnonom/blobs/issues
- **Discussions**: https://github.com/nonomnonom/blobs/discussions
- **Security**: https://github.com/nonomnonom/blobs/security

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed technical notes.

---

**Release Status**: ✅ COMPLETE AND PRODUCTION READY

Users can download, install, and use Blobs 0.5.0 immediately.
