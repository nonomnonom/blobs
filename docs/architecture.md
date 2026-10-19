# Blobs asset distribution

Blobs is an offline-first Android research app with an online, curated asset library. After setup, model inference, retrieval, citations, and reading use files stored on the device. Network access is limited to explicit package downloads and signed download-list checks.

## Where files live

- GitHub hosts the app source, build and verification tools, and Android releases.
- The public Hugging Face dataset [`nonomnonom/blobs-catalog`](https://huggingface.co/datasets/nonomnonom/blobs-catalog) hosts the signed package list, Blobs-formatted Qwen model package, starter library, and optional library. Files are pinned by repository revision, byte count, and SHA-256.
- Gemma model weights are downloaded from their upstream Hugging Face repositories at pinned revisions.
- Installed models, libraries, conversations, and settings remain in app-private storage.

The list is curated by Blobs maintainers. It is not an unrestricted index of Hugging Face, and it never triggers an asset download by itself.

## First run and updates

1. Blobs reads available memory and storage locally and recommends a compatible model. The user chooses whether to accept the recommendation or select another listed model.
2. Setup downloads the selected model and required starter library only after the user taps the download action. The size and available-space guidance appear before each download.
3. The app verifies the signed package metadata, pinned byte count, SHA-256, and package format before validating and installing files atomically.
4. Optional libraries can be downloaded from the in-app Library screen. Users can remove installed assets to reclaim space.
5. Blobs never checks for updates in the background or just because a screen opens. The user can tap “Check for updates” in Library or Models; that fetches the signed download list and caches the verified result. Blobs uses the cached or bundled list offline.

Users can also add their own TXT, Markdown, CSV, JSON, and text-based PDF files from the phone. Blobs extracts text locally, builds a searchable SQLite FTS5 library, and installs it through the same integrity-checked package path as downloaded libraries. Import is bounded to 50 files, 25 MB total input, 10 MB per ordinary file, 200 PDF pages, and 5 million Unicode code points per collection. Scanned-image PDFs, OCR, password-protected PDFs, and certificate-encrypted PDFs are not supported. Each saved collection can be switched off for retrieval, exported as a portable JSON pack, or removed. These collections stay in app-private storage unless the user explicitly exports them.

No catalog request or model/library download starts in the background. A failed or interrupted install leaves the previous working asset intact. Once setup is complete, network access is unnecessary for chat and research; online catalog checks and downloads start only after the user asks for them.

## Trust and publishing

Catalog envelopes use ECDSA P-256 with SHA-256. The app embeds the trusted public keys and accepts the current and previous key during rotation. The signing key is kept outside Git and release artifacts. Each package reference points to an immutable Hugging Face revision; package size and SHA-256 are checked before installation. Model and library payloads also pass format, path, storage, and database validation.

Before publishing an asset, maintainers verify its origin, redistribution rights, license and attribution, package compatibility, size, and digest. A corrected asset is published as a new immutable package version and catalog entry; removing an entry does not silently remove a user's installed files.

The repeatable Hugging Face publisher is `android/tools/publish_hf_assets.py`. It checks staged assets against the app's package list, publishes only the allow-listed README and package files, pins URLs to the resulting revision, signs the refreshed list, and updates the app's bundled fallback list. GitHub remains the source and APK distribution channel.
