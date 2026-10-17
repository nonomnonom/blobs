# Contributing to Blobs

Use this directory as the repository root. Read the [architecture](android/docs/ARCHITECTURE.md) before changing native behavior and keep each change focused on an observable result.

1. Identify the component that owns the state, data, or failure invariant.
2. Add a regression test at the narrowest useful layer for persistence, retrieval, package, concurrency, or recovery changes.
3. Run formatting and the checks relevant to the change.
4. Describe user-visible behavior, validation, and material limitations in the pull request.

From `android/`:

```powershell
.\gradlew.bat ktlintFormat
.\gradlew.bat verify
```

From `corpus/`:

```powershell
$env:UV_CACHE_DIR = "$PWD/.cache/uv"
uv run --locked python -m unittest discover -s tests
```

Run affected connected Android tests for platform behavior and `tools/verify_apk.py` for native, dependency, permission, or packaging changes. See [testing](android/docs/TESTING.md) for the full command set.

Do not commit credentials, signing material, `local.properties`, local model weights, generated packages, APKs, caches, emulator state, build output, screenshots, or evaluation reports. Commit source, dependency locks, Room schemas, reproducible fixtures, and required third-party notices.

Changes must preserve draft ownership, request cancellation, source provenance, bounded file handling, and the offline boundary. Treat retrieved text and model output as untrusted data. Do not add placeholder answers or fake compatibility results to production code.
