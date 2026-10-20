# Contributing to Blobs

Bug fixes, accessibility improvements, documentation, corpus corrections, and performance work are welcome. Check [existing issues](https://github.com/nonomnonom/blobs/issues) or open a [proposal](https://github.com/nonomnonom/blobs/issues/new/choose) before a large change. Include the affected version, reproduction steps, and expected behavior. Report vulnerabilities [privately](SECURITY.md) and follow the [code of conduct](CODE_OF_CONDUCT.md).

## Find your starting point

| Work | Guide | Source |
| --- | --- | --- |
| Android app | [Build and run](android/README.md) | `android/app/src/` |
| Knowledge libraries | [Prepare a corpus](corpus/README.md) | `corpus/blobs_corpus/` |
| Documentation website | [Develop the site](docs/web/README.md) | `docs/web/` |
| Signing and releases | [Prepare a release](android/docs/RELEASE.md) | `android/tools/` |

The [architecture guide](docs/architecture.md) explains app ownership and data flow. Put changes in the layer that owns the behavior. Preserve offline chat, user-initiated downloads, pinned hashes, cancellation, atomic installation, and explicit Room migrations. Keep installed data usable when an update fails.

## Style and dependencies

Use UTF-8, LF, and four-space indentation; XML, JSON, and YAML use two spaces. Follow `android/.editorconfig`, existing Kotlin/Python names, and the package directory layout. Open Python text files with an explicit encoding.

Dependency versions belong in `android/gradle/libs.versions.toml` or `corpus/pyproject.toml`, with their lockfiles. Gradle verifies dependency hashes in `android/gradle/verification-metadata.xml`. Review publisher checksums when updating those entries; generating verification metadata alone does not authenticate an artifact.

Keep models, generated packages, signing keys, caches, experiment reports, and internal review notes out of Git. Reviewed record shards use Git LFS; source locks and authored test cases belong in the repository.

## Verification

Run checks relevant to your change. From `android/`, the [build guide](android/README.md#build-and-verify) prepares dependencies and runs `.\gradlew.bat verify`: ktlint, Android lint, JVM tests, debug/release assembly, and instrumentation-test compilation. Apply Kotlin formatting with `.\gradlew.bat ktlintFormat`.

With a device or emulator connected, run the base Android suite from `android/`:

```powershell
.\gradlew.bat -Pandroid.testInstrumentationRunnerArguments.notClass=dev.blobs.app.MultiLibraryRetrievalScalingTest connectedDebugAndroidTest
```

`ManualCheck` tests are excluded by default. The multi-library test needs staged packages; [optional evaluation tools](corpus/evaluation/README.md) explain how. Ordinary application checks do not require grading model-generated answers.

For Python tooling, run from `corpus/`:

```powershell
uv sync --locked
uv run --locked python -m unittest discover -s tests
uv run --locked python -m unittest discover -s ../android/tools/tests
uv run --locked ruff check ../android/tools .
uv run --locked ruff format --check ../android/tools .
```

Signing/publishing changes also need `uv run --locked --group publisher python -m unittest discover -s ../android/tools/tests`. Check authored native files with `uv run --locked clang-format --dry-run --Werror ../android/app/src/main/cpp/blobs.cpp ../android/app/src/main/cpp/chat_prompt.cpp ../android/app/src/main/cpp/chat_prompt.h ../android/app/src/main/cpp/chat_prompt_test.cpp`. Leave fetched third-party code unchanged.

Website checks and preview instructions are in [the website guide](docs/web/README.md#check-and-export).

## Submit a change

Use focused Conventional Commits and the [pull-request template](.github/pull_request_template.md). Explain the resulting behavior, checks actually run, and remaining limitations. Cover validation, ordering, cancellation, recovery, and data transitions when affected. Describe migration/rollback for data or runtime changes; include light, dark, and large-text screenshots for UI changes.

Original code and documentation contributions use [Apache-2.0](LICENSE). Preserve third-party notices and identify the license of new material. Original artwork and branding have separate terms in [NOTICE](NOTICE).
