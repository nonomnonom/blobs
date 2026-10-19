# Contributing to Blobs

Start with the [build guide](README.md#build) and [architecture](docs/architecture.md). Blobs has one Android application module and a separate uv-managed corpus workspace. Keep a change in the layer that owns its behavior; introduce another module or dependency when it solves a concrete ownership or build problem.

Bug fixes, accessibility improvements, documentation, reproducible corpus corrections and focused performance improvements are welcome. Use the [issue forms](https://github.com/nonomnonom/blobs/issues/new/choose) for bugs and proposals; discuss major changes before building them so the work fits the project's scope. Include reproduction steps, an affected version and relevant evidence. Use [private reporting](SECURITY.md) for vulnerabilities and follow the [code of conduct](CODE_OF_CONDUCT.md) in project spaces.

## Where changes belong

| Area | Location |
| --- | --- |
| Application composition and lifecycle | `android/app/src/main/kotlin/dev/blobs/app/` |
| Screens and feature-specific components | `android/app/src/main/kotlin/dev/blobs/app/feature/` |
| Navigation, application UI state and shared UI | `android/app/src/main/kotlin/dev/blobs/app/ui/` |
| Local storage, retrieval, packages and work coordination | `android/app/src/main/kotlin/dev/blobs/app/data/` |
| Model integration and answer formatting | `android/app/src/main/kotlin/dev/blobs/app/inference/` |
| JNI runtime and native prompt contract | `android/app/src/main/cpp/` |
| Corpus acquisition, normalization and packaging | `corpus/blobs_corpus/` |
| Optional app performance and retrieval tools | `corpus/evaluation/` |
| Android preparation and release tooling | `android/tools/` |
| Documentation website, content and styling | `docs/web/` |

Use a matching filename for a screen or primary type. Keep Kotlin package declarations aligned with directories in every source set. Shared feature components belong beside their consumers; shared application components belong in `ui/`. The Python package contract belongs to `blobs_corpus.package`; `android/tools/build_package.py` is its compatibility entry point.

## Code and data conventions

Use UTF-8, LF and four-space indentation; XML, JSON and YAML use two spaces. Android formatting is enforced by ktlint and `android/.editorconfig`. Follow existing Kotlin and Python naming conventions. Open text files with an explicit encoding in Python so preparation behaves consistently on Windows and Linux.

Keep generated packages, models, signing keys, caches and local reports out of source control. Use `android/.local/`, root `.local/`, root `work/`, or the ignored corpus work/results directories for local work. Add benchmark evidence deliberately with the inputs, identity and assumptions needed to reproduce it. Internal review material belongs outside the public repository.

Preserve package identity and digest checks, offline operation, cancellation and atomic activation. Keep an old library payload when activating another edition. Change stored data through explicit Room migrations; never use destructive migration fallback. JNI names and persisted package IDs are compatibility boundaries.

## Verification

Run the checks relevant to the behavior being changed.

For the Next.js/Fumadocs documentation website, use Node.js 24 and run `npm ci`, `npm run lint`, `npm run typecheck`, `npm test`, `npm run format:check`, and `npm run build` from `docs/web/`. Preview the exported site with `npm run preview`; check navigation, search, mobile layout, and both themes. See the [website guide](docs/web/README.md).

For corpus changes, from `corpus/`:

```powershell
uv sync --locked
uv run --locked python -m unittest discover -s tests
uv run --locked python -m unittest discover -s ../android/tools/tests
```

The default development group pins Ruff for Python lint and formatting and clang-format for authored C++. From `corpus/`, check both without changing files:

```powershell
uv run --locked ruff check ../android/tools .
uv run --locked ruff format --check ../android/tools .
uv run --locked clang-format --dry-run --Werror ../android/app/src/main/cpp/blobs.cpp ../android/app/src/main/cpp/chat_prompt.cpp ../android/app/src/main/cpp/chat_prompt.h ../android/app/src/main/cpp/chat_prompt_test.cpp
```

Use `ruff format` on the same Python paths to apply formatting; use `clang-format -i` on those four native files. Keep fetched third-party code and generated template includes unchanged. Review formatting separately from behavior changes. Ruff rules live in root `ruff.toml`; native style lives in `.clang-format`.

When changing signing or publishing, include the publisher group so the signing contract test runs:

```powershell
uv run --locked --group publisher python -m unittest discover -s ../android/tools/tests
```

From `android/`:

```powershell
uv run --locked --project ../corpus python tools/fetch_runtime.py
.\gradlew.bat verify
```

`verify` runs formatting checks, debug and release lint, JVM tests, both application builds, and instrumentation-test compilation. To apply Kotlin formatting, use `.\gradlew.bat ktlintFormat` and review the diff.

Dependency versions belong in `android/gradle/libs.versions.toml` or `corpus/pyproject.toml`, with the corresponding Gradle or uv lockfile. Android host-tool constraints are scoped to the plugin classpath, ktlint or lint configurations. Gradle also verifies artifact SHA-256 values against `android/gradle/verification-metadata.xml`. When intentionally updating dependencies, generate the new entries with `--write-verification-metadata sha256`, review them against the publishers' artifacts/checksums, and run normal verification again without the write flag. Generating metadata records the current artifacts; it does not independently establish their authenticity.

With an Android emulator or device connected, run the base application suite:

```powershell
.\gradlew.bat -Pandroid.testInstrumentationRunnerArguments.notClass=dev.blobs.app.MultiLibraryRetrievalScalingTest connectedDebugAndroidTest
```

The multi-library test requires staged, hash-pinned packages; staging commands and optional performance tools are in [the evaluation guide](corpus/evaluation/README.md). Tests marked `ManualCheck` are opt-in. Application verification checks flows, source contracts and performance; it does not require grading model-generated answers.

Add regression coverage for changes to validation, ordering, cancellation, recovery and data transitions. For refactoring, establish the existing behavior before moving code and run the same relevant checks afterward. Report which checks actually ran and any remaining limits.

## Reviews and source delivery

Keep commits focused and use the existing Conventional Commit style. Describe the concrete problem, resulting behavior, compatibility implications and validation in the [pull-request template](.github/pull_request_template.md). Keep structural moves separate from product behavior changes when they can be reviewed independently.

The repository owner reviews changes for correctness, offline behavior, data preservation, compatibility, scope and relevant verification before merging. Contributions to licensed source code and documentation use Apache-2.0; preserve existing third-party notices and identify any newly introduced material and its license. See [NOTICE](NOTICE) for artwork and other exclusions. Reviews and design discussions should be public unless they contain security or private information. No review deadline is promised.

Release source archives are assembled from tracked files in the Blobs Git checkout, using their current working copies. Add new public source files to Git before assembling a kit. Untracked notes, local assets and generated output are excluded; missing tracked source files cause packaging to fail. The source ZIP includes this guide and the shared editor configuration. See [the Android guide](android/README.md) for package preparation and [asset publishing](docs/architecture.md#trust-and-publishing) for catalog revisions and signing.
