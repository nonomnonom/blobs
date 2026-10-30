# Build Blobs libraries

This Python workspace turns attributed source text into searchable `.blobs`
packages. It runs on the development computer; Android only needs the resulting
package. The pipeline has four owners: source acquisition and review, bounded
records, SQLite FTS library construction, then immutable archive creation. To
download existing libraries in the app, use [the library guide](https://nonomnonom.github.io/blobs/docs/library/).

## Global catalog standard

Blobs is an offline research product, so large libraries are deliberate product
assets rather than exceptional build output. Build the catalog from reviewed
source families: broad reference libraries, primary-domain libraries, regional
libraries, structured local snapshots, and user-owned imports. Do not publish a
library merely because it is easy to acquire or small enough for a demo.

Before a public catalog entry is created, its maintainer records the source
edition, redistribution rights, language, geographic scope when relevant,
publisher, update policy, record count, archive size, known limitations,
attribution, source lock, normalized record digest, package digest, and
versioned retrieval cases. Each published edition is immutable. A revision is a
new edition, not a silent overwrite of an installed library.

[`supply/registry.json`](supply/registry.json) is the authoritative queue for
new library families and legacy dispositions. It distinguishes a planned source
family from a locked, reviewed, release-ready library. A planned entry is never
a download, a package, or a claim of corpus quality.

Source evidence remains in its original language. English is required for code,
developer documentation, package-field names, and default application copy; it
does not permit translation or rewriting of source text, titles, licences, or
attribution.

## Set up

From `corpus/`, using uv 0.12.19:

```powershell
uv sync --locked
```

Python and dependency versions are pinned in `pyproject.toml` and `uv.lock`.
The active production-validation phase permits focused tests and package builds.
Run the smallest relevant checks for the change; a package build is candidate
evidence only until source, rights, retrieval, and release-admission gates pass.

## Fetch reviewed records

App builds and unit tests do not need large record shards. Follow the [source-only clone instructions](../android/README.md#build-and-verify). Reviewed records are not stored in this repository: they live in the `nonomnomon/blobs-catalog` dataset, and [published-records.json](published-records.json) records each edition's path, manifest digest, record digest, and the immutable Hugging Face revision that hosts it.

### Download reviewed records from Hugging Face

With the `hf` CLI installed, take the edition's `records_revision` and `records_path` from [published-records.json](published-records.json):

```powershell
hf download nonomnonom/blobs-catalog --type dataset `
  --revision <records_revision> `
  --include "<records_path>/*" `
  --local-dir work/hf-records
```

Export validates shard sizes, SHA-256, and each document, then writes to a new directory. Use its `manifest.json` to build a package:

```powershell
uv run --locked python -m blobs_corpus.export `
  work/hf-records/<records_path>/manifest.json `
  work/verified-records/<edition>
```

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

### Prepare a reviewed place snapshot

City-place data is a separate source type. Capture the raw JSON once into
`work/`; the command also writes a neighbouring `*.capture.json` receipt with
the exact query, endpoint, query hash, byte digest, OpenStreetMap base
timestamp, total elements, and matching place count. The receipt has state
`captured-unreviewed`: it is evidence of acquisition, not permission to make a
library or catalog entry.

The reviewed lock must carry `source.endpoint`, the exact `source.query`, its
`query_sha256`, `snapshot_bytes`, `snapshot_sha256`, `osm_base`, `elements`,
and `matching_elements`, alongside normal collection, publisher, ODbL, origin,
saved-date metadata, plus reviewer, review date, and review notes. This makes
a changed query, changed raw response, or changed selection rule a new data
edition rather than a silent replacement.

```powershell
uv run --locked python -m blobs_corpus.places capture `
  https://overpass-api.de/api/interpreter `
  sources/ubud-vegan-places-2026-10.1.overpassql `
  work/sources/ubud-vegan-places.snapshot.json
```

Review the raw JSON for query scope, tags, duplicate or unsuitable entries,
licence and attribution. A named reviewer then creates the immutable lock from
the receipt and exact snapshot. `make-lock` verifies raw bytes against the
receipt before it writes a lock, and requires review identity, date, and notes.
It cannot turn a changed snapshot into an approved lock.

```powershell
uv run --locked python -m blobs_corpus.places make-lock `
  work/sources/ubud-vegan-places.snapshot.json.capture.json `
  work/sources/ubud-vegan-places.snapshot.json `
  sources/ubud-vegan-places-2026-10.json `
  --id ubud-vegan-places --edition 2026-10.1 `
  --publisher "OpenStreetMap contributors" `
  --license "ODbL 1.0; © OpenStreetMap contributors" `
  --collection "Ubud named places tagged vegan" `
  --origin "https://www.openstreetmap.org/copyright" `
  --saved 2026-10-23 --reviewer "reviewer identity" `
  --reviewed 2026-10-23 `
  --notes "Reviewed scope, vegan tags, attribution and unsupported claims."
```

Convert only the locked snapshot:

```powershell
uv run --locked python -m blobs_corpus.places extract `
  sources/REVIEWED-PLACE-LOCK.json `
  work/sources/REVIEWED-PLACE-SNAPSHOT.json `
  work/reviewed/REVIEWED-PLACE-RECORDS.jsonl.gz
```

The extractor selects named restaurants, cafes, and fast-food places only when
their OSM tags explicitly say `diet:vegan=yes`, `diet:vegan=only`, or include
`vegan` in `cuisine`. It writes the raw indicator, coordinates, object URL,
and source snapshot time into each record. It does not infer that a place is
open, fully vegan, nearby, highly rated, or “best”. OpenStreetMap data is ODbL;
the pack and application must display the required OpenStreetMap attribution
and licence information. Do not commit raw snapshots or capture receipts. A
reviewed lock is source-control metadata, while the raw snapshot remains in
`work/` for reproducible rebuilding by its authorized maintainer.

| Collection | Source lock | Preparation |
| --- | --- | --- |
| Wikipedia introductions | `wikipedia-en-top1m-mini-2026-04.json` | Archive pipeline above |
| Wikipedia full text (planned core reference) | `wikipedia-en-all-nopic-2026-06.json` | Lock only; do not acquire or package during rebuild without an approved storage and shard plan |
| English travel | `wikivoyage-en-all-nopic-2026-09.json` | Same archive pipeline with the travel paths |
| Outdoor safety | `nps-outdoor-safety-2026-10.json` | Web pages |
| Camping safety | `camping-safely-2026-10.json` | Web pages |
| Backcountry planning | `backcountry-basics-2026-10-v2.json` | Web pages |
| Emergency preparedness | `ready-emergency-preparedness-2026-10.json` | Web pages |
| Weather, earthquakes/tsunamis, emergency food | `weather-safety-2026-10.json`, `earthquake-tsunami-safety-2026-10.json`, `emergency-food-2026-10.json` | Web pages |
| Practical living | `practical-living-2026-10.json` | Web pages |

The [captured starter library](../android/corpus/README.md) has its own local build recipe. Run a module with `--help` for its arguments. Builders refuse existing outputs. Extraction can resume after interruption; remove a stale `.extract.lock` only after confirming its extractor is no longer running.

The former `bali-travel` subset is retired from the product catalog because its
30 records duplicate the larger English Wikivoyage collection. Its checked-in
lock remains historical audit evidence only; do not use it as a new-release
input.

### Split a large reviewed library into complementary volumes

The full Wikipedia source is too large to treat as one assumed Android
download. After a complete review, create one immutable partition plan from the
reviewed manifest, then build a separate package for each plan ID. The plan
assigns every document by a stable SHA-256 document-ID bucket. It records each
volume's document count and ID digest, and the builder verifies both before it
can publish SQLite output. All volumes retain the same source lock and each has
its own record-set digest and archive digest.

```powershell
uv run --locked python -m blobs_corpus.partition `
  work/reviewed/wikipedia-en-all-nopic-2026-06/manifest.json `
  supply/plans/wikipedia-en-reference-2026-06.json --count 16

uv run --locked python -m blobs_corpus.package library `
  work/reviewed/wikipedia-en-all-nopic-2026-06/manifest.json `
  work/packages/wikipedia-en-reference-p000.blobs `
  --partition-plan supply/plans/wikipedia-en-reference-2026-06.json `
  --partition p000 --schema 3 --id wikipedia-en-reference-p000 `
  --version 2026-06.1 --title "Wikipedia reference · volume 1" `
  --license "CC BY-SA 4.0; Wikipedia contributors; text adapted by Blobs" `
  --origin "https://download.kiwix.org/zim/wikipedia/wikipedia_en_all_nopic_2026-06.zim"
```

Choose the volume count
only after a storage plan and representative archive-size evidence exist; the
archive contract rejects a library payload over 6 GiB (6,442,450,944 bytes).
The example count is only a planning starting point, not size evidence. The
selection is deterministic, not topical: cross-volume retrieval cases are
required before the family enters `release_ready`.

The 6 GiB limit applies separately to each installed library payload and its
catalog download. It is independent of the Android-wide installed-payload
budget, which is a separate safeguard across all user-installed models and
libraries. A raw source archive can be much larger than either number because
it is a workstation input and is never an Android library package.

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

For another collection, use its own identity, license, and origin. Keep
canonical URLs, source dates, revision/history links, and adaptation notices
with every article. Images and linked third-party resources need separate rights
review; see [data notices](THIRD_PARTY_NOTICES.md). `records.py` owns the record
contract, `library.py` owns SQLite/FTS construction, and `archive.py` owns the
immutable `.blobs` envelope. A failed build must not replace a published
edition. `library.py` also rejects an overlong serialized section path before
publishing, so a corpus package cannot defer that Android import failure to the
user's device. Records and redirect aliases reject NUL and Unicode replacement
characters, and package metadata is rejected rather than truncated when it
exceeds the Android field contract, contains either character, or has invalid
Unicode. The completed archive is synced before its immutable output path is
linked. Library packaging copies the normalized `records_sha256` calculated by
the builder into the archive manifest; it lets Android record which source
record set produced a selected library without hashing a multi-gigabyte pack at
question time.

## Distribute a library

Add a new immutable package edition to the signed download list using
[package distribution and installation](../docs/architecture.md#package-distribution-and-installation).
Source locks, attribution, package hashes, and retrieval cases accompany a new
collection. Libraries share the existing importer and FTS5 format.

Keep raw archives, databases, generated packages, models, caches, and run reports out of Git. Use `work/` or `reports/` for local output. [Evaluation evidence](evaluation/README.md) covers retrieval diagnosis and manual Android product records; it is not required to build the app.
