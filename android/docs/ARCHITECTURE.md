# Android architecture

## Product boundary

Blobs is an offline Android application with a deliberately small setup-only network boundary. First-run setup can download pinned model and library packages after an explicit user tap. The downloader accepts HTTPS from the fixed GitHub release and its asset hosts, resumes only when the entity tag matches, checks exact archive length and SHA-256, and then hands the package to `PackageInstaller` for the normal manifest, payload, storage, and format checks. Chat, retrieval, inference, citations, and reading do not use the network. Local package import remains available for air-gapped setup.

```mermaid
flowchart LR
    Catalog[Pinned package catalog] --> Downloader[User-started HTTPS download]
    Downloader --> Installer[Package installer]
    Picker[Android document picker] --> Installer
    Installer --> Store[App-private package store]
    User[Question] --> Retrieval[SQLite FTS5 retrieval]
    Store --> Retrieval
    Retrieval --> Work[WorkController]
    Store --> Runtime[llama.cpp runtime]
    Work --> Runtime
    Runtime --> Answer[Streamed answer]
    Retrieval --> Evidence[Immutable source evidence]
    Evidence --> Answer
```

## State and ownership

Compose screens render immutable UI state and send actions to feature or product ViewModels. ViewModels own screen state and user intent. Repositories own persistence. `WorkController` serializes generation and package/data mutations so cancellation and late callbacks cannot write into a replaced conversation or package state.

Room stores conversations, turns, drafts, selected package metadata, and install jobs. DataStore holds lightweight user preferences. Knowledge packages remain separate read-only SQLite databases so large libraries do not participate in Room migrations.

## Package installation

Imports use Android `content://` URIs and stream into a job-owned staging area. The installer enforces format version, package kind, bounded metadata, supported schema/runtime, declared length, CRC, SHA-256, free-space headroom, and safe entry names. Only a completely verified payload is atomically activated. Cancellation or failure removes staging data and preserves the active edition.

The source package and installed payload can coexist during import, so the UI reports peak storage instead of only final storage. An installed package is never inferred from a filename.

## Retrieval and citations

The library database uses SQLite FTS5 for lexical retrieval. Results carry stable source IDs, document metadata, section information, and bounded passage text. The generation prompt assigns `[S1]`, `[S2]`, and subsequent identifiers. Output parsing keeps only complete identifiers that refer to supplied evidence. The saved turn stores the evidence used at generation time, so later library changes cannot rewrite a past answer's sources.

A citation opens the stored passage and local article reader. External source addresses are shown as attribution and are never opened as an implicit side effect.

## Native inference

The JNI layer is intentionally narrow: load/unload a model, stream tokens, and cancel work. Native handles remain outside UI code. Runtime source revisions are pinned and fetched by `tools/fetch_runtime.py`; generated third-party source trees are ignored by Git. CPU inference supports ARM64 and x86_64 builds.

## Security and privacy invariants

- No account, analytics, Google Play Services, or remote API. Network access is limited to explicitly started first-run package downloads; the research flow itself remains offline.
- Retrieved content and model output are untrusted text.
- SQL is parameterized and package paths are validated.
- Package checksums detect corruption but do not establish publisher identity.
- Signing keys and credentials never enter source archives.
- Destructive Room fallback is disabled; schema changes require migrations.
