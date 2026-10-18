# Blobs corpus engineering

This uv-managed Python project creates and verifies offline knowledge packages for Blobs. Python runs only on the development computer. Android consumes the resulting `.blobs` file and does not require Python, Git LFS, a server, or network access.

## Environment

```powershell
$env:UV_CACHE_DIR = "$PWD/.cache/uv"
uv sync --locked
uv run --locked python -m unittest discover -s tests
```

Python and dependency versions are pinned by `pyproject.toml` and `uv.lock`.

## Pipeline

The pipeline acquires a pinned source archive, extracts bounded records, normalizes article text and attribution, validates quality constraints, exports immutable record shards, builds a SQLite FTS5 payload, and packages that payload with an exact manifest.

Core commands are exposed through `python -m blobs_corpus.<module>`. Use `--help` on a module before running it. Builders refuse existing output editions and publish through a temporary path so a failed run cannot replace verified data.

### Rebuild the pinned Top 1M library

Run these commands from this directory. The source archive is about 3 GB; acquisition verifies its locked size and SHA-256, and extraction checkpoints deterministic 5,000-entry shards so an interrupted run can resume. Extraction and package construction can take a long time on a development computer.

```powershell
uv run --locked python -m blobs_corpus.acquire `
  sources/wikipedia-en-top1m-mini-2026-04.json work/sources

uv run --locked python -m blobs_corpus.extract `
  sources/wikipedia-en-top1m-mini-2026-04.json `
  work/sources/wikipedia_en_top1m_mini_2026-04.zim `
  work/extract/wikipedia-en-top1m-mini-2026-04

uv run --locked python -m blobs_corpus.curate `
  work/extract/wikipedia-en-top1m-mini-2026-04/manifest.json `
  work/reviewed/wikipedia-en-top1m-mini-2026-04

uv run --locked python -m blobs_corpus.export `
  work/reviewed/wikipedia-en-top1m-mini-2026-04/manifest.json `
  artifacts/records/wikipedia-en-top1m-mini-2026-04

uv run --locked python -m blobs_corpus.package library `
  artifacts/records/wikipedia-en-top1m-mini-2026-04/manifest.json `
  artifacts/wikipedia-en-top1m-mini-2026-04.blobs `
  --schema 3 `
  --id wikipedia-top1m-mini `
  --version 2026-04.1 `
  --title "Wikipedia Top 1M introductions" `
  --license "CC BY-SA 4.0; Wikipedia contributors; text adapted by Blobs" `
  --origin "https://download.kiwix.org/zim/wikipedia/wikipedia_en_top1m_mini_2026-04.zim"
```

The source lock captures snapshot, scope, license, origin, byte count, and digest. Review the generated curation report and package receipt before distributing an edition. Do not commit the raw archive, database, or `.blobs` package to Git; the repository's large reviewed record-shard policy uses Git LFS, while reproducible release packages are distributed separately.

### Build the travel library

The English travel library uses the pinned September 2026 Wikivoyage snapshot. Run from `corpus/`:

```powershell
uv run --locked python -m blobs_corpus.acquire `
  sources/wikivoyage-en-all-nopic-2026-09.json work/sources

uv run --locked python -m blobs_corpus.extract `
  sources/wikivoyage-en-all-nopic-2026-09.json `
  work/sources/wikivoyage_en_all_nopic_2026-09.zim `
  work/extract/wikivoyage-en-all-nopic-2026-09

uv run --locked python -m blobs_corpus.curate `
  work/extract/wikivoyage-en-all-nopic-2026-09/manifest.json `
  work/reviewed/wikivoyage-en-all-nopic-2026-09

uv run --locked python -m blobs_corpus.export `
  work/reviewed/wikivoyage-en-all-nopic-2026-09/manifest.json `
  artifacts/records/wikivoyage-en-all-nopic-2026-09

uv run --locked python -m blobs_corpus.package library `
  artifacts/records/wikivoyage-en-all-nopic-2026-09/manifest.json `
  work/packages/wikivoyage-en-2026-09-v2.blobs `
  --schema 3 `
  --id wikivoyage-en `
  --version 2026-09.2 `
  --title "Travel guides · English" `
  --license "CC BY-SA 4.0; Wikivoyage contributors; each guide links to its article history for author attribution" `
  --origin "https://en.wikivoyage.org/wiki/Wikivoyage:How_to_re-use_Wikivoyage_guides"
```

The resulting package is an optional library alongside the starter library. Its article-history links preserve contributor attribution. The snapshot date is shown to users because travel details change.

### Build the U.S. outdoor-safety library

This small survival-oriented edition contains four National Park Service pages on hiking preparation, outdoor emergencies, water treatment, and winter hiking. It is explicitly U.S. national-park guidance; it does not replace current park rules, weather alerts, or emergency services. The source lock pins each page's title, update date, canonical URL, and normalized article-text SHA-256. Ingest rejects a title or article-text change for review, while ignoring unrelated changes to page chrome. Source images and navigation are excluded.

```powershell
uv run --locked python -m blobs_corpus.web_pages acquire `
  sources/nps-outdoor-safety-2026-10.json work/sources/nps-outdoor-safety-2026-10

uv run --locked python -m blobs_corpus.web_pages extract `
  sources/nps-outdoor-safety-2026-10.json `
  work/sources/nps-outdoor-safety-2026-10 `
  artifacts/records/nps-outdoor-safety-2026-10/documents.jsonl.gz

uv run --locked python -m blobs_corpus.package library `
  artifacts/records/nps-outdoor-safety-2026-10/documents.jsonl.gz `
  work/packages/nps-outdoor-safety-2026-10-v1.blobs `
  --schema 3 `
  --id nps-outdoor-safety `
  --version 2026-10.1 `
  --title "Outdoor safety · U.S. national parks" `
  --license "U.S. Government work · National Park Service (public domain unless marked)" `
  --origin "https://www.nps.gov/aboutus/disclaimer.htm" `
  --work-directory work/build/nps-outdoor-safety-2026-10
```

The [NPS reuse notice](https://www.nps.gov/aboutus/disclaimer.htm) says government-created NPS material is generally public domain unless otherwise marked and warns that some page material can have third-party restrictions. This edition contains text-only extracts from the four locked NPS pages, no images, and no NPS marks. It is a small set of U.S. national-park safety guides, not a comprehensive survival manual. The user-facing package description must state that local conditions and rules vary, advise following current ranger and emergency-service instructions, and clarify that Blobs is not an NPS product or endorsement. Review source rights and content again before making a new edition.

### Build the U.S. emergency-preparedness library

This household-focused edition uses four dated Ready.gov pages covering family planning, emergency kits, evacuation, and power outages. It is a U.S. federal preparedness reference, not live emergency information; users should follow current local instructions. Source text is extracted without images, agency marks, or linked third-party materials. FEMA says most FEMA.gov material may be reused, while page-specific third-party restrictions can apply; review those notices before preparing every edition.

```powershell
uv run --locked python -m blobs_corpus.web_pages acquire `
  sources/ready-emergency-preparedness-2026-10.json work/sources/ready-emergency-preparedness-2026-10

uv run --locked python -m blobs_corpus.web_pages extract `
  sources/ready-emergency-preparedness-2026-10.json `
  work/sources/ready-emergency-preparedness-2026-10 `
  artifacts/records/ready-emergency-preparedness-2026-10/documents.jsonl.gz

uv run --locked python -m blobs_corpus.package library `
  artifacts/records/ready-emergency-preparedness-2026-10/documents.jsonl.gz `
  work/packages/ready-emergency-preparedness-2026-10-v1.blobs `
  --schema 3 `
  --id ready-emergency-preparedness `
  --version 2026-10.1 `
  --title "Emergency preparedness · U.S. households" `
  --license "FEMA/Ready.gov text; mostly reusable subject to page-specific third-party restrictions" `
  --origin "https://www.fema.gov/about/website-information" `
  --work-directory work/build/ready-emergency-preparedness-2026-10
```

FEMA's [website information and reuse policy](https://www.fema.gov/about/website-information) says most FEMA.gov material may be copied and distributed, but warns that some material is licensed or restricted. FEMA lists Ready.gov among the .gov domains it owns and manages. The source lock pins each Ready.gov page's title, update date, canonical URL, and normalized text digest; the text-only extraction omits visual assets. The user-facing listing must identify the U.S. scope, show that the snapshot can become outdated, and advise users to follow current emergency officials. Blobs is independent and not endorsed by FEMA.

Extraction checkpoints are safe to resume after a normal interrupt. If the process is forcibly terminated, it can leave `work/extract/EDITION/.extract.lock`; confirm no extractor is still running before removing that stale file and resuming. Never remove the lock while an extraction process is active.

Example package build from normalized records:

```powershell
uv run --locked python -m blobs_corpus.package library \
  artifacts/records/EDITION/manifest.json \
  artifacts/EDITION.blobs \
  --schema 3 \
  --id EDITION \
  --version VERSION \
  --title "Offline knowledge library" \
  --license "CC BY-SA 4.0" \
  --origin "SOURCE_URL"
```

Inspect the module help for the current schema and required metadata. Generated `.blobs` files and build workspaces are ignored by Git.

### Add topic libraries

Keep the starter, travel, outdoor-safety, emergency-preparedness, and future topic libraries as separate packages. Each library gets its own pinned source locks, attribution and license review, curation snapshot, package receipt, retrieval cases, and catalog entry. Publishing a new signed catalog revision makes an additional library downloadable without replacing the user's model, starter library, or app. Android shares the same importer and SQLite FTS5 format across libraries, so adding a topic does not require a new retrieval implementation. Keep a topic bundle together only when its sources and intended use form one coherent edition.

## Data integrity

- Inputs are pinned by origin, revision, byte length, and SHA-256.
- Normalized records retain canonical URL, contributor/history URL, source archive identity, and available revision identity.
- Extraction and passage lengths are bounded.
- SQLite integrity and FTS integrity checks run before publication.
- Package verification streams the complete payload and checks declared length, CRC, and SHA-256.
- Redirect and alternate-name resolution retains unresolved and ambiguous counts instead of guessing.

Large reviewed record shards under `artifacts/records/` use Git LFS. Model weights, raw downloads, extracted databases, generated packages, caches, and reports stay outside Git.

Optional host-side retrieval replay and inference-performance measurements are documented in [`evaluation/README.md`](evaluation/README.md). They do not score generated-answer quality and are not part of the Android runtime or required to build the app.

See [data notices](THIRD_PARTY_NOTICES.md) for Wikipedia attribution and host-side dependency licenses.
