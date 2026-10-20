# Build Blobs libraries

This Python workspace turns attributed source text into searchable `.blobs` packages. It runs on the development computer; Android only needs the resulting package. To download existing libraries in the app, use [the library guide](https://nonomnonom.github.io/blobs/docs/library/).

## Set up

From `corpus/`, using uv 0.12.19:

```powershell
uv sync --locked
uv run --locked python -m unittest discover -s tests
```

Python and dependency versions are pinned in `pyproject.toml` and `uv.lock`. Run the commands below from this directory.

## Fetch reviewed records

App builds and unit tests do not need large record shards. Follow the [source-only clone instructions](../android/README.md#build-and-verify). For a package rebuild, fetch only the edition you need from the repository root:

```powershell
git lfs pull --include="corpus/artifacts/records/nps-outdoor-safety-2026-10.2/**" --exclude=""
```

Package builders require hydrated files, not LFS pointers. Current travel, regional, and focused preparedness records are also available at immutable Hugging Face revisions in [published-records.json](published-records.json). It records paths, digests, and package identities, without requiring you to download a source archive again.

### Download reviewed travel records from Hugging Face

With the `hf` CLI installed:

```powershell
hf download nonomnonom/blobs-catalog --type dataset `
  --revision 698ab323d165b46128b000ecbe06367380906c10 `
  --include "records/wikivoyage-en-all-nopic-2026-09.3/*" `
  --local-dir work/hf-records

uv run --locked python -m blobs_corpus.export `
  work/hf-records/records/wikivoyage-en-all-nopic-2026-09.3/manifest.json `
  work/verified-records/wikivoyage-en-all-nopic-2026-09.3
```

Export validates shard sizes, SHA-256, and each document, then writes to a new directory. Use its `manifest.json` to build a package. Reproducing the byte-identical published travel archive requires builder commit `64fa8e0`; newer builders can produce a different database checksum from the same records.

## Prepare a source edition

Source locks in [`sources/`](sources/) pin archive bytes or individual web revisions and normalized text. Use a new edition when source content changes. The pipeline acquires, extracts, curates, exports, and packages records.

For the Top 1M Wikipedia introduction collection:

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
```

For a small web collection, use its page lock:

```powershell
uv run --locked python -m blobs_corpus.web_pages acquire `
  sources/nps-outdoor-safety-2026-10.json work/sources/nps-outdoor-safety
uv run --locked python -m blobs_corpus.web_pages extract `
  sources/nps-outdoor-safety-2026-10.json `
  work/sources/nps-outdoor-safety `
  work/reviewed/nps-outdoor-safety/documents.jsonl.gz
```

| Collection | Source lock | Preparation |
| --- | --- | --- |
| Wikipedia introductions | `wikipedia-en-top1m-mini-2026-04.json` | Archive pipeline above |
| English travel | `wikivoyage-en-all-nopic-2026-09.json` | Same archive pipeline with the travel paths |
| Outdoor safety | `nps-outdoor-safety-2026-10.json` | Web pages |
| Camping safety | `camping-safely-2026-10.json` | Web pages |
| Backcountry planning | `backcountry-basics-2026-10-v2.json` | Web pages |
| Emergency preparedness | `ready-emergency-preparedness-2026-10.json` | Web pages |
| Weather, earthquakes/tsunamis, emergency food | `weather-safety-2026-10.json`, `earthquake-tsunami-safety-2026-10.json`, `emergency-food-2026-10.json` | Web pages |
| Practical living | `practical-living-2026-10.json` | Web pages |
| Bali travel | `bali-travel-2026-09.json` | `python -m blobs_corpus.subset LOCK REVIEWED_MANIFEST OUTPUT` |

The [captured starter library](../android/corpus/README.md) has its own local build recipe. Run a module with `--help` for its arguments. Builders refuse existing outputs. Extraction can resume after interruption; remove a stale `.extract.lock` only after confirming its extractor is no longer running.

## Package reviewed text

Supply either a reviewed `manifest.json` or `documents.jsonl.gz`. For example, package the Top 1M records prepared above:

```powershell
uv run --locked python -m blobs_corpus.package library `
  artifacts/records/wikipedia-en-top1m-mini-2026-04/manifest.json `
  work/packages/wikipedia-top1m-mini.blobs `
  --schema 3 --id wikipedia-top1m-mini --version 2026-04.1 `
  --title "Wikipedia Top 1M introductions" `
  --license "CC BY-SA 4.0; Wikipedia contributors; text adapted by Blobs" `
  --origin "https://download.kiwix.org/zim/wikipedia/wikipedia_en_top1m_mini_2026-04.zim"
```

For another collection, use its own identity, license, and origin. Keep canonical URLs, source dates, revision/history links, and adaptation notices with every article. Images and linked third-party resources need separate rights review; see [data notices](THIRD_PARTY_NOTICES.md). SQLite/FTS integrity and streamed payload checks run before packaging. A failed build must not replace a published edition.

## Distribute a library

Add a new immutable package edition to the signed download list using [trust and publishing](../docs/architecture.md#trust-and-publishing). Source locks, attribution, package hashes, and retrieval cases accompany a new collection. Libraries share the existing importer and FTS5 format.

Keep raw archives, databases, generated packages, models, caches, and run reports out of Git. Use `work/` or `reports/` for local output. [Optional evaluation tools](evaluation/README.md) help investigate retrieval and performance; they are not required to build the app.
