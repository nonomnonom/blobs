# Contributing to Blobs

Bug fixes, accessibility improvements, documentation, corpus corrections, and performance work are welcome. Check [existing issues](https://github.com/nonomnonom/blobs/issues) or open a [proposal](https://github.com/nonomnonom/blobs/issues/new/choose) before a large change. Include the affected version, reproduction steps, and expected behavior. Report vulnerabilities [privately](SECURITY.md) and follow the [code of conduct](CODE_OF_CONDUCT.md).

For setup questions, model choices, and help using Blobs, use [Discussions](https://github.com/nonomnonom/blobs/discussions). Use the source/citation issue form for incorrect library content, attribution, or unsupported references in an answer.

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

Blobs is a global product: source code, identifiers, comments, developer
documentation, release metadata, catalog metadata, package metadata, and new
default UI copy are written in English. Preserve the original language of
quoted source evidence, titles, URLs, licences, attribution, and user-provided
content. Do not scatter language-specific runtime branches; future
internationalisation needs an explicit locale and resource design.

Dependency versions belong in `android/gradle/libs.versions.toml` or `corpus/pyproject.toml`, with their lockfiles. Gradle locks resolved versions in `android/**/*.lockfile` and verifies dependency hashes in `android/gradle/verification-metadata.xml`, so a Gradle version bump needs both regenerated: from `android/`, run `.\gradlew.bat verify --write-locks --write-verification-metadata sha256`. Review publisher checksums for the entries that changed; generating verification metadata alone does not authenticate an artifact. Dependabot updates the catalog but neither file, so its Gradle pull requests fail verification until a maintainer adds both. A file generated on one host records only that host's platform artifacts, so add the other `aapt2` jars from Google Maven's published checksums.

Keep models, generated packages, signing keys, caches, experiment reports, and internal review notes out of Git. Reviewed record shards use Git LFS; source locks and authored test cases belong in the repository.

## Verification

Validation is active under [`goal.md`](goal.md). Builds, tests, benchmarks,
evaluators, and device acceptance may run when inputs and resources are
available. Preserve other running processes and data preparation, and record
blockers directly.

Run checks relevant to your change. From `android/`, the [build guide](android/README.md#build-and-verify) prepares dependencies and runs `.\gradlew.bat verify`: ktlint, Android lint, JVM tests, debug/release assembly, and instrumentation-test compilation. Apply Kotlin formatting with `.\gradlew.bat ktlintFormat`.

With a device or emulator connected, run the base Android suite from `android/`:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

`ManualCheck` tests are excluded by default. They are manual device-evidence
captures, never emulator-performance gates; [the device evidence guide](corpus/evaluation/device/README.md)
defines their explicit inputs and report requirements. Ordinary application
checks do not require grading model-generated answers.

For Python tooling, run from `corpus/`:

```powershell
uv sync --locked
uv run --locked python -m unittest discover -s tests
uv run --locked python -m unittest discover -s ../android/tools/tests
uv run --locked ruff check ../android/tools .
uv run --locked ruff format --check ../android/tools .
```

Signing/publishing changes also need `uv run --locked --group publisher python -m unittest discover -s ../android/tools/tests`. Check authored native files with `uv run --locked clang-format --dry-run --Werror ../android/app/src/main/cpp/blobs.cpp ../android/app/src/main/cpp/local_model_runtime.cpp ../android/app/src/main/cpp/local_model_runtime.h ../android/app/src/main/cpp/kv_cache.cpp ../android/app/src/main/cpp/kv_cache.h`. Leave fetched third-party code unchanged.

Website checks and preview instructions are in [the website guide](docs/web/README.md#check-and-export).

Release orchestration checks run from the repository root:

```powershell
npm ci
npm test
npm run release:check
```

## Submit a change

Use focused Conventional Commits and the [pull-request template](.github/pull_request_template.md). Explain the resulting behavior, checks actually run, and remaining limitations. Cover validation, ordering, cancellation, recovery, and data transitions when affected. Describe migration/rollback for data or runtime changes; include light, dark, and large-text screenshots for UI changes.

Any pull request that reaches a shipped component (`android/`, `corpus/`, or `docs/`) must carry a release note, and CI fails when it does not. Run `npx changeset` from the repository root, select the components the change affects, and commit the generated `.changeset/<name>.md`; choose patch, minor, or major the way you would choose the next version. Use `npx changeset add --empty` when the change reaches a shipped component but should not appear in [CHANGELOG.md](CHANGELOG.md). Do not edit CHANGELOG.md by hand; `npm run release:version` writes it.

Work on a branch and open a pull request against `main`. The required CI check runs Android or website verification when the affected files need it; community-only changes use the lightweight checks. Address review conversations before merging. Accepted pull requests use squash merge; merged source branches in this repository are deleted automatically.

CI runs when a pull request is opened or updated. Use **Update branch** to refresh an older pull request after CI configuration changes. Maintainers can run the **CI** workflow manually for a full check on a selected branch.

The repository owner, [@nonomnonom](https://github.com/nonomnonom), maintains releases, reviews contributions, and moderates project spaces. The owner decides whether a proposal fits the project and can request a smaller scope or more evidence. External contributions need maintainer review; the repository does not require a second maintainer's approval for the owner's own changes. There is no guaranteed review or support deadline.

Original code and documentation contributions use [Apache-2.0](LICENSE). Preserve third-party notices and identify the license of new material. Original artwork and branding have separate terms in [NOTICE](NOTICE).
