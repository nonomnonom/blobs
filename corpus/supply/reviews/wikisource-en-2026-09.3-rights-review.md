# Rights review record — `wikisource-en` edition `2026-09.3` (four volumes)

Status: **decided, 2026-10-24.** Review authority was delegated by the product owner
(`nonomnonom`) to the agent maintaining this corpus. The reviewer is an automated agent, not a
human rights lawyer; this is an engineering rights review recorded under that delegated authority,
and every accepted limitation is carried into the catalog profile rather than waived silently.
This edition supersedes `2026-09.2`, which is withdrawn because all four of its volumes are
uninstallable.

## Source identity — unchanged from `2026-09.2`

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/wikisource-en-all-nopic-2026-09.json` |
| Source lock identity SHA-256 | `4231ed3b6c4e38d4a236640c7d42ed332f6a1fd505785c21d4070318198db440` |
| Source file | `wikisource_en_all_nopic_2026-09.zim` |
| Source bytes | 3,905,588,830 |
| Source SHA-256 | `6180cd199142862fb0d276861ccc5b48a40f7bae427b8427b2b6a573796e2f98` |
| Publisher (archive) | openZIM, scraper mwoffliner 2.0.0, flavour `nopic`, language `eng` |
| Underlying work | `en.wikisource.org` transcription text, snapshot `2026-09-08` |
| Declared licence | Per work: public domain, or CC BY-SA 4.0 for contributions, translations and annotations |
| Licence policy | `https://en.wikisource.org/wiki/Wikisource:Copyright_policy` |
| Retained raw snapshot | `corpus/work/sources/wikisource_en_all_nopic_2026-09.zim` (retained locally, not published) |

The same pinned archive is re-extracted at the same digest; no new source, host, licence, or
attribution basis is introduced by this edition.

## Why a successor edition exists

`2026-09.2` counted **135 bodies containing U+FFFD** in its own attribution census and shipped them
anyway, recording the character as an inherited limitation. The acceptance was invalid:
`LibraryValidator` on Android refuses to install any library whose `documents` columns contain
U+FFFD, and because the edition is split into four hash partitions, the corruption reached every
volume. It also reached the span contract: `p000` and `p001` additionally failed
`validateSpanBackedPassages` (54 and 20 rejected passages) because `section` and `section_path`
values were derived from the same corrupted bodies.

A raw byte scan of each admitted volume's `library.sqlite` finds 2,393 (`p000`), 368 (`p001`),
1,735 (`p002`) and 1,993 (`p003`) occurrences of `EF BF BD`. The same scan of the four successor
volumes finds 0, 0, 0 and **1**. That single hit in `p003` was traced rather than waved away: a
SQL-level probe of the extracted database shows 0 live rows containing U+FFFD in `documents`,
`passage_spans`, `passages` and `document_names`, and locates the byte inside FTS5 shadow-table
binary index data (`passages_fts_data` / `passages_fts_idx`), where a coincidental `EF BF BD` byte
triple is data, not text. The columns `LibraryValidator` reads are clean
(`corpus/work/scan-fffd-2026-10-24/results.txt`).

The root cause was in this repository, not upstream: `corpus/blobs_corpus/records.py::validate_document`
exempted `documents.body` from the no-U+FFFD rule the app applies to every column. The exemption is
removed, so extraction now rejects the offending records into its counted `rejected` stream.

All 135 excluded records are named individually, with archive index, entry path and title, in
[`wikisource-en-2026-09.3-excluded-records.md`](wikisource-en-2026-09.3-excluded-records.md). They
are overwhelmingly encyclopaedia entries, official reports and index/plate pages whose transcribed
bodies carry upstream encoding damage — for example `2024 Elkhorn–Bennington–Blair Tornado
Finalized Report`, chapters of `A History of Chinese Literature`, `A Treatise on Electricity and
Magnetism` plates and chapters, `Basel Convention`, and several NIOSH analytical methods. The
replacement character occurs in the body text only: none of the 135 titles or archive entry paths
contains U+FFFD (measured), so the appendix reproduces both verbatim. None is excluded on licence
grounds.

## Record-set identity

| Field | Value |
| --- | --- |
| Reviewed records | `corpus/artifacts/records/wikisource-en-all-nopic-2026-09.3/manifest.json` (SHA-256 `2de9b8f4caf3e95e9c3b71dea18cdef9263cde3623c62cb5c4a1b61a619e71ee`) |
| Extraction complete | `true` — 869,914 of 869,914 archive entries, 174 shards / 522 files |
| Curation policy | `Kiwix article-only review v3`, policy SHA-256 `6c5e1897294e8d586c9a703f816385e51dbbc399bcd393ce0963f4ec3e3f6766` |
| Reviewed documents | 622,014 |
| Text characters / UTF-8 bytes | 6,362,968,404 / 6,425,153,383 |
| Long documents over 100,000 characters | 6,188 |
| Documents with headings | 67,210 |
| Retained redirect aliases | 150,671 plus 6,223 HTML redirects |
| Rejected records | 11,840 (11,705 in the predecessor, plus the 135 named above) |

Curation exclusions, unchanged in policy from the predecessor:

| Exclusion | Records |
| --- | --- |
| Author metadata pages | 47,277 |
| Version index pages ("Versions of … include:") | 7,369 |
| Disambiguation pages ("… may refer to:") | 6,024 |
| Subject portal navigation | 4,702 |
| Exact duplicate body text of an earlier record | 1,414 |
| Archive navigation page | 1 |
| Bundled support asset | 1 |

Coverage reconciliation, measured not assumed: 622,149 → 622,014 is exactly the 135 excluded
records, and the rejection histogram changes by `+135 Invalid body: contains a Unicode replacement
character` and by nothing else. `long_over_100000_chars` fell from 6,208 to 6,188, i.e. 20 of the
135 were long documents — consistent, not a second unexplained change.

## Revision and attribution census

Measured by full iteration of all 622,014 retained records, not by sampling
(`corpus/work/scan-fffd-2026-10-24/census-wikisource-3.txt`):

| Measure | Value |
| --- | --- |
| Documents with a MediaWiki `revision_id` | 619,117 (99.5%) |
| Documents without a revision id | 2,897 |
| Documents with a `history_url` | 622,014 (100%) |
| Records failing `blobs_corpus.records.validate_document` | 0 |
| Retained bodies containing U+FFFD | 0 |

The 2,897 records without a revision id are unchanged from the predecessor, so every one of the 135
exclusions had a revision id.

## Partition plan and volume identity

A rebuilt edition needs a new plan: `blobs_corpus.partition._read_plan` binds a plan to the reviewed
edition twice, by `source_lock_sha256` **and** `source_manifest_sha256`, so the `2026-09.2` plan
refuses every `2026-09.3` volume.

| Field | Value |
| --- | --- |
| Plan | `corpus/supply/plans/wikisource-en-all-nopic-2026-09.3.json` |
| Plan SHA-256 | `b03261c30b5847f92e6bdf6bc3408cabfc2357d21775db972702bbd80409d02a` |
| Algorithm | `sha256-document-id-modulo-v1`, 4 partitions |
| Binds | lock `4231ed3b…` and reviewed manifest `2de9b8f4…` |

| Volume | Documents | Passages | Installed payload bytes | Archive bytes | Payload SHA-256 | Archive SHA-256 | `records_sha256` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `wikisource-en-history-p000` | 155,527 | 1,430,232 | 3,127,779,328 | 1,323,097,330 | `4d986fb42ce83a1b535b002dced995cca0b7b5084667070b6cf6de4acfb8f706` | `6cf1ffb2ff7d97cc92fc2a95f5f601e51cb4be2d3f8ff981f53cff3e5267d2bc` | `4669ab78053928f2436d10c9d8c85fddd5b0d9fdefa4376a844f238d621605cf` |
| `wikisource-en-history-p001` | 155,739 | 1,425,914 | 3,123,478,528 | 1,322,088,523 | `5d94af5e93ddfa4978b7971d0405ae665a16ecda2501f71c4ac91b548c723394` | `370df30d8f81ef25ed75a04106e84651803a1b28cb7da3be699fe30123c4e035` | `2bcddef364b637f476f7d5d3455c2782b243a7167343f3a27b53fa2de5ddc87a` |
| `wikisource-en-history-p002` | 155,378 | 1,438,450 | 3,150,995,456 | 1,335,443,794 | `18a5cbcacb5a30e8ae597f9e6beacacf1341779ccd60b77cf7a0dad9ca6751ef` | `2a9973ca9ef6f1fb3f1e851c88634eb30182bebdf021492c4980139e56fec96c` | `f2be3cf891884cd16f3f901d74d8a0e903ca04a74c492af2c56bb9c3dcf610b1` |
| `wikisource-en-history-p003` | 155,370 | 1,431,552 | 3,126,341,632 | 1,323,294,448 | `289f4e6545651215958d7179a5167544402c68cd5ed8701930a500a099c78368` | `0176bb4a3ad8c5b8104a39c60620d83ea5b40a43013b50fe399a3a0987e60843` | `3da5938b9f1e7a3ca662317c99dc61de6809c6ed0b6e49698b76bd3a44a748dc` |

The four per-volume document counts sum to exactly 622,014. Each volume's receipt also carries the
plan SHA-256 above and its own `document_ids_sha256`, and all four were built by
`records.py 554a4182…`, `library.py 6407e406…`, `names.py a137dac8…` (schema 3, SQLite 3.50.4).
Receipts: `corpus/work/packages/wikisource-en-history-p00{0,1,2,3}-2026-09.3.receipt.json`.

## Measured evidence accepted with this decision

- Runtime contract: `blobs_corpus.runtime_contract.audit_library` returns 0 rejected documents,
  0 rejected passages and 0 duplicate document ids for each of the four volumes — including the
  span violations that `p000` and `p001` carried in the predecessor. Packaging now refuses to emit
  an archive that fails the audit (`package.py::assert_installable`).
- Record contract: all 622,014 retained records pass `validate_document` on full iteration.
- Text scan: 0 `EF BF BD` occurrences in three volumes and 1 in `p003`, localized to FTS5 binary
  index data with 0 live text rows affected.
- Retrieval cases: `wikisource-en-history-p000..p003-retrieval-v3.json`, 10 cases each. Every
  expected document id was re-verified to resolve in this edition **and to stay in the same volume**
  under the new plan, so **0 cases were dropped** in any of the four files.
- Retrieval was **not re-replayed against the successor volumes.** The figure recorded for the lane
  (`p000`: 9 of 10 expected titles, 7 at rank 1, MRR 0.783, 490.0 ms median, 1,191.0 ms p95 on the
  committed host policy, with the Magna Carta case retrieving the Encyclopaedia article instead of
  the Davis translation) was measured on the withdrawn `2026-09.2` volume and is attributed to it.
  Because the successor record set is that edition minus 135 named records and none of the 40 cases
  targets them, the measurement is expected to hold — an expectation, not evidence.

## Accepted limitations carried into the catalog profile

- Transcriptions are not critical editions: they can preserve source errors, historical spelling,
  scanning artefacts and omissions.
- Per-work licensing varies. Most texts are public domain, but translations, annotations and
  editorial contributions can be CC BY-SA 4.0, and each document keeps its own licence statement and
  revision history. Nothing here clears a specific reuse; the per-work statement does.
- Court opinions, constitutions, treaties and statutes may be amended, repealed or superseded. They
  are historical texts, not legal, medical or professional advice; verify the current official
  source.
- The 135 excluded records are absent from all four volumes; a question only they answered returns
  no match rather than corrupted text.
- Upstream corruption was removed by exclusion, not by editing transcription text; no source
  character was substituted, so every retained record is byte-identical to the same record in
  `2026-09.2`.
- The split is deterministic by document-id hash, not topical: a single work can be split across
  volumes, and one volume is not a coherent collection. The reviewed edition is complete only with
  all four volumes.
- 2,897 records carry a history URL but no revision id, so an exact revision cannot be pinned for
  those.
- **No target-device acceptance.** Each volume installs a payload near 3.1 GB, and no phone-class
  install, latency, memory or thermal measurement backs this record; the product owner confirmed on
  2026-10-24 that no such device can be attached. Emulator observations elsewhere in this repository
  are labelled as such.

## Duplicate supply

These four volumes are one reviewed edition split for size, occupying the single English "history
and primary texts" slot; they are not four libraries and must not be counted as overlapping supply.
`blobs_corpus.portfolio.assert_distinct` is re-run after the predecessor's records edition is moved
to `corpus/retired/records/wikisource-en-all-nopic-2026-09.2` in the same operation as this
admission; its output is recorded in `corpus/work/scan-fffd-2026-10-24/admission-log.txt`.

## Review decision

Admit `wikisource-en-history-p000..p003@2026-09.3` as `release-ready` and withdraw `2026-09.2`. The
licence basis is unchanged and was already reviewed; what changed is that these four volumes can
actually be installed by the app that ships them.

Decided by: an automated agent under delegated review authority for the product owner
(`nonomnonom`), 2026-10-24. Not a legal opinion.
