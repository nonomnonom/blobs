# Rights review record — `wikipedia-top1m-mini` edition `2026-04.5`

Status: **decided, 2026-10-24.** Review authority was delegated by the product owner
(`nonomnonom`) to the agent maintaining this corpus. The reviewer is an automated agent, not a
human rights lawyer; this is an engineering rights review recorded under that delegated authority,
and every accepted limitation is carried into the catalog profile rather than waived silently.
This edition supersedes `2026-04.4`, which is withdrawn because it is uninstallable.

## Source identity — unchanged from `2026-04.4`

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/wikipedia-en-top1m-mini-2026-04.json` |
| Source lock SHA-256 | `408972bfd4365ad834765b0f5b47b8ebe37795020a8ee3c155c2e6cd866311cd` |
| Source file | `wikipedia_en_top1m_mini_2026-04.zim` |
| Source bytes | 3,022,411,034 |
| Source SHA-256 | `56b963ffb5f5246da10cf87d1bdbaaad457b39e3b766d52844ccd1aab3a49a34` |
| Verified against | `https://download.kiwix.org/zim/wikipedia/wikipedia_en_top1m_mini_2026-04.zim.sha256` (lock verified 2026-10-17) |
| Publisher (archive) | openZIM / Kiwix, scraper mwoffliner 1.17.5, flavour `mini`, language `eng`, snapshot `2026-04-28` |
| Underlying work | English Wikipedia article introductions, top one million selection |
| Declared licence | CC BY-SA 4.0 — `https://creativecommons.org/licenses/by-sa/4.0/` |

The same pinned archive is re-extracted at the same digest; no new source, host, licence, or
attribution basis is introduced by this edition.

## Why a successor edition exists

`2026-04.4` shipped 3 article bodies carrying U+FFFD and recorded upstream corruption as an accepted
limitation. The acceptance was invalid: `LibraryValidator` on Android refuses to install any library
whose `documents` columns contain U+FFFD, so those 3 articles made the whole 1.79 GB archive
uninstallable. A raw byte scan of the admitted package's `library.sqlite` finds **3** occurrences of
`EF BF BD`; the same scan of this successor finds **0**
(`corpus/work/scan-fffd-2026-10-24/results.txt`).

The root cause was in this repository, not upstream: `corpus/blobs_corpus/records.py::validate_document`
exempted `documents.body` from the no-U+FFFD rule the app applies to every column. The exemption is
removed, so extraction now rejects the offending records into its counted `rejected` stream.

Records excluded by that rule in this edition, named rather than hidden:

| Title | Archive entry path | Archive index | Reason |
| --- | --- | ---: | --- |
| Mojibake | `Mojibake` | 3,553,801 | body carries U+FFFD, here as legitimate article content |
| Samsung Galaxy SL | `Samsung_Galaxy_SL` | 4,577,451 | body carries U+FFFD inherited from the pinned archive |
| Specials (Unicode block) | `Specials_(Unicode_block)` | 4,852,832 | body carries U+FFFD, here as legitimate article content |

Two of the three are articles *about* character encoding, whose bodies quote the replacement
character as their subject matter. That is a genuine conflict between the runtime text contract and
legitimate content, not an upstream defect: under the current `LibraryValidator` rule no library can
ever ship a body containing U+FFFD, even verbatim. Recorded here as a product limitation and as a
candidate for a future escaped-representation design; it is not solved by this edition.

## Record-set and package identity

| Field | Value |
| --- | --- |
| Reviewed records | `corpus/artifacts/records/wikipedia-en-top1m-mini-2026-04.5/manifest.json` (SHA-256 `99ca4cfc1eec41ddc0f916a9d4da2144cd380129b5c4a3a407f9efe95db60c9a`) |
| Extraction complete | `true` — 5,930,070 of 5,930,070 archive entries, 1,187 shards / 3,561 files |
| Curation policy | `Kiwix article-only review v3`, policy SHA-256 `6c5e1897294e8d586c9a703f816385e51dbbc399bcd393ce0963f4ec3e3f6766` |
| Documents / passages | 997,714 / 2,126,742 |
| Duplicate bodies removed | 43 |
| Non-article entries removed | 2 (1 MediaWiki support content, 1 archive navigation page) |
| Redirects retained as aliases | 4,144,450 plus 774,856 HTML redirects |
| Documents with headings | 811,365 |
| Normalized text | 1,504,894,991 characters / 1,524,846,169 UTF-8 bytes |
| `records_sha256` | `2459f398597c3f6dd617badd58833ab99d8dfea41e40d6d8288fd97e383edf3e` |
| Installed payload | 4,980,006,912 bytes (77.3% of the 6,442,450,944-byte limit), SHA-256 `f850f7d0b78196309caefc4b6add5459321fa13f7a363f7a0744de20807a0243` |
| Archive | `wikipedia-top1m-mini-2026-04.5.blobs`, 1,787,025,308 bytes, SHA-256 `a751510e64b1c845016d293e9d9af00bc393f87cdbeb01bec22f3268a5972528` |
| Receipt | `corpus/supply/receipts/wikipedia-top1m-mini-2026-04.5.receipt.json` |
| Builder sources | `records.py 554a4182…`, `library.py 6407e406…`, `names.py a137dac8…` (schema 3, SQLite 3.50.4) |
| Retrieval cases | `corpus/evaluation/cases/wikipedia-top1m-mini-retrieval-v3.json` (12 cases, pinned to the digests above) |

Coverage reconciliation, measured not assumed: `2026-04.4` carried 997,717 documents and this
edition carries 997,714 — exactly the 3 named above, with the rejection histogram changing by
`+3 Invalid body: contains a Unicode replacement character` and by nothing else.

## Measured evidence accepted with this decision

- Runtime contract: `blobs_corpus.runtime_contract.audit_library` returns 0 rejected documents,
  0 rejected passages and 0 duplicate document ids over the built database, and packaging now
  refuses to emit an archive that fails it (`package.py::assert_installable`).
- Text scan: 0 occurrences of `EF BF BD` in the packaged `library.sqlite`, against 3 in the edition
  it replaces.
- Retrieval cases: all 12 expected document ids were re-verified to resolve in this edition, so
  **0 cases were dropped** when the case file was re-pinned.
- Retrieval was **not re-replayed against this successor package.** The figures quoted for the lane
  (7 of 12 expected titles, all at rank 1, MRR 0.583, 164.1 ms median, 1,262.7 ms p95 on the
  committed host policy, missing `photosynthesis`, `earthquake`, `inflation`, `roman-empire` and
  `indonesia`) were measured on the withdrawn `2026-04.4` package and are attributed to it. None of
  the five known misses is among the 3 excluded articles, so the rebuild is not expected to change
  them — an expectation, not evidence.

## Accepted limitations carried into the catalog profile

- Snapshot, not live: this is the April 2026 top-one-million selection, and article text changes
  daily on Wikipedia.
- Introductions only: the `mini` flavour carries lead sections, so depth on any topic is limited and
  a question whose answer sits below the lead returns weak or no evidence.
- Not complete Wikipedia: roughly one million selected articles, not the whole English project.
- Per-article revision ids are not retained, so an exact revision cannot be pinned; attribution
  rests on the pinned archive digest and each article's attribution notices.
- The 3 excluded articles are absent. Two of them (`Mojibake`, `Specials (Unicode block)`) are
  articles whose subject matter includes the replacement character, so questions about them are
  unanswerable from this library under the current runtime text contract.
- Upstream corruption was removed by exclusion, not by editing article text; no source character was
  substituted, so every retained article is byte-identical to the same article in `2026-04.4`.
- Recall on this lane is the weakest measured in the portfolio (7 of 12 on the pinned host policy);
  short lead sections and title-alias gaps are the recorded causes and remain open.
- CC BY-SA 4.0 obligations travel with the text: attribution to Wikipedia contributors and
  share-alike on any derivative.
- **No target-device acceptance.** At 4.98 GB installed this is the largest library in the portfolio
  and the one whose device memory behaviour matters most, yet no phone-class install, latency,
  memory or thermal measurement backs this record; the product owner confirmed on 2026-10-24 that no
  such device can be attached. Emulator observations elsewhere in this repository are labelled as
  such.

## Duplicate supply

This lane occupies the single English "general reference" slot. `blobs_corpus.portfolio.assert_distinct`
is re-run after the predecessor's records edition is moved to
`corpus/retired/records/wikipedia-en-top1m-mini-2026-04.4` in the same operation as this admission;
its output is recorded in `corpus/work/scan-fffd-2026-10-24/admission-log.txt`.

## Review decision

Admit `wikipedia-top1m-mini@2026-04.5` as `release-ready` and withdraw `2026-04.4`. The licence
basis is unchanged and was already reviewed; what changed is that this edition can actually be
installed by the app that ships it.

Decided by: an automated agent under delegated review authority for the product owner
(`nonomnonom`), 2026-10-24. Not a legal opinion.
