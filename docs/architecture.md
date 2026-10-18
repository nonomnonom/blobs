# Blobs asset distribution architecture

Blobs is an offline-first Android app with an online, curated asset library. The app remains useful without a network connection after setup. Models and knowledge libraries are separately versioned assets that users can discover and install from inside Blobs.

## Responsibilities

- **GitHub** hosts the Android source, build tooling, tests, and app releases.
- **Hugging Face Hub** hosts public model and corpus artifacts, plus the small Blobs catalog that describes the assets supported by the app.
- **The Android app** diagnoses device capability, presents compatible assets, manages downloads and installed versions, and runs inference and retrieval locally.
- **Blobs maintainers** curate and publish catalog entries. The first version is a trusted directory, not an unrestricted index of every Hugging Face repository or a user-upload marketplace.

## First run and offline behavior

1. Blobs reads device memory and available storage locally and recommends compatible models. The recommendation is guidance; the user chooses what to install.
2. First-run setup requires a starter corpus and a model before entering chat. It explains the download size and required free space before the user starts each download.
3. Downloads begin only after an explicit user action. The app verifies the pinned asset size and SHA-256, validates the package format and compatibility, then activates it atomically.
4. Chat, inference, retrieval, citations, and reading use installed assets locally. They do not require a network connection.
5. Later, users can open Library to browse and install additional compatible models and corpora. They can remove assets to reclaim storage.

## Catalog and asset format

The catalog is a small, versioned JSON document in a dedicated public Hugging Face dataset repository owned by Blobs. Model and corpus payloads live in their own Hugging Face repositories. Catalog entries point to immutable full commit revisions, not moving `main` branches. Hugging Face supports downloading a file from a specific commit revision. [Download from a specific revision](https://huggingface.co/docs/huggingface_hub/en/guides/download)

Each catalog entry must provide:

- stable asset ID, version, display name, description, and asset kind (`model` or `corpus`);
- Hugging Face repository type, repository ID, full commit revision, file path, byte size, and SHA-256;
- license, attribution, source, language, and publisher/maintainer;
- minimum Blobs app and package schema versions;
- model runtime/format and device requirements, or corpus format, retrieval/index compatibility, and language metadata;
- whether the asset is required for first run, recommended, or optional.

Corpus repositories include a Hugging Face dataset card describing their contents and provenance. [Dataset cards](https://huggingface.co/docs/hub/main/datasets-cards)

The app fetches only the small catalog metadata when it checks for updates. It uses conditional HTTP requests where supported, caches the last valid catalog, and falls back to that cache when offline. A new catalog makes assets available in the UI; it never silently downloads a model or corpus. Catalog updates are signed with ECDSA P-256 and SHA-256 by the Blobs catalog release key, with the verification public key embedded in the app. The app rejects invalid signatures, unsupported schemas, malformed IDs, untrusted hosts, and invalid or incomplete asset metadata. `android/tools/sign_catalog.py` creates signed catalog envelopes; the private key is provided by the `BLOBS_CATALOG_SIGNING_KEY` environment variable or command-line option and must stay outside Git.

Downloads use pinned revisions, resume where supported, enforce declared size limits, verify SHA-256, and install into versioned app-private storage. The active version changes only after complete validation. A failed update leaves the previous working version active. The app must check free space before download and installation, including temporary and unpacked sizes.

## Compatibility and corpus updates

Model entries state the supported runtime format, quantization, required app/runtime version, and a conservative device recommendation. Device diagnosis runs locally. No user query, conversation, or stable device identifier is sent as part of catalog checks.

Corpus entries declare the corpus schema and retrieval/index compatibility they require. Corpus content must not silently assume an embedding model or index format the installed app does not provide. Blobs either uses a portable corpus representation it can index locally or explicitly declares a compatible prebuilt index. Changing chunking, embedding, or index formats requires a new corpus/package version and a migration or rebuild path.

Catalog checks may run when the Library is opened and at a bounded app-start interval while online. The app shows when the cached catalog was last checked. Network failure does not block chat or access to installed assets. There is no automatic background download of large assets.

## Security, licenses, and publishing

Only reviewed catalog entries are presented as Blobs-supported assets. Before publication, maintainers verify provenance, redistribution rights, license/attribution, archive safety, package compatibility, hashes, and the minimum app version. Model files and corpora are treated as untrusted input and pass format, path, size, and payload validation before installation. Catalog signing protects the curated metadata and asset references; SHA-256 detects payload mismatch against that signed metadata.

Asset versions are immutable. A correction or replacement publishes a new revision and catalog version. Removing an entry stops recommending it but does not silently uninstall a user's copy; where an asset must be withdrawn, the app can mark it unavailable and explain the reason.

## Current implementation and migration

The app now supports an embedded fallback catalog, signed remote catalog refresh, conditional requests, offline cache recovery, pinned hashes, resumable downloads, validation, and atomic package activation. It checks at most once per day at app start and when the user opens the model or library screens; the user can also check manually. The catalog client is configured for the future `blobs-app/blobs-catalog` dataset repository. That Hugging Face namespace and repository do not exist yet, so the remote endpoint currently falls back to the bundled/cache catalog. Current model entries point directly to pinned Hugging Face revisions, while current starter/additional corpus archives are still linked from GitHub Releases.

The migration path is:

1. Define and validate the catalog schema, signing format, and compatibility fields alongside the current package manifest.
2. Create the `blobs-app` Hugging Face namespace and publish the catalog repository and Blobs-owned corpus repositories; verify each artifact against the package builder output.
3. Publish the signed catalog with the private key stored outside Git, then verify app refresh and offline recovery against the live Hugging Face endpoint.
4. Move first-run and Library corpus downloads to pinned Hugging Face assets, preserving explicit confirmation, resumability, integrity checks, atomic activation, and rollback.
5. Remove old release-hosted asset references only after the new catalog and downloads have passed app and package verification.

Until the namespace is created and assets are published, the bundled-catalog fallback remains authoritative for users; the remote catalog path is implemented but has no live catalog to fetch yet.
