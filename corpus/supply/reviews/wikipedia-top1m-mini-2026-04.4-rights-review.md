# Rights review record — `wikipedia-top1m-mini` edition `2026-04.4`

Status: **decided, 2026-10-24.** Review authority was delegated by the product
owner (`nonomnonom`) to the agent maintaining this corpus. The reviewer is an
automated agent, not a human rights lawyer; the decision below is an engineering
rights review recorded under that delegated authority, and every accepted
limitation is carried into the catalog profile rather than waived silently.

## Source identity

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/wikipedia-en-top1m-mini-2026-04.json` |
| Source file | `wikipedia_en_top1m_mini_2026-04.zim` |
| Source bytes | 3,022,411,034 |
| Source SHA-256 | `56b963ffb5f5246da10cf87d1bdbaaad457b39e3b766d52844ccd1aab3a49a34` |
| Publisher (archive) | openZIM / Kiwix, ZIM snapshot `2026-04` |
| Underlying work | English Wikipedia article introductions, top one million selection |
| Declared licence | CC BY-SA 4.0 — `https://creativecommons.org/licenses/by-sa/4.0/` |
| Verified against | `https://download.kiwix.org/zim/wikipedia/wikipedia_en_top1m_mini_2026-04.zim.sha256` |

Scope is explicit in the lock: Kiwix English top-one-million article
introductions without images, snapshot `2026-04`; this is **not** all of English
Wikipedia.

## What this edition contains

The extraction used the archive's article entries and was built with extractor
`4`. Extractor `5` changed only Stack Exchange post handling and Stack Exchange
canonical URLs, so the Wikipedia body text is unaffected by that version bump;
the records record the extractor they were produced with.

Review removed 43 records whose body text exactly duplicated an earlier record
and 2 non-article entries (one MediaWiki support page, one archive navigation
page). No duplicate body survives.

| Field | Value |
| --- | --- |
| Documents | 997,717 |
| Passages | 2,126,749 |
| Normalized text characters | 1,504,900,578 |
| Records edition | `corpus/artifacts/records/wikipedia-en-top1m-mini-2026-04.4` |
| `records_sha256` | `7a43326ca2a341034e2510080f697e7638f2204a3a3682a31a2af42b7eb7707c` |
| Duplicate bodies removed | 43 |
| Non-article entries removed | 2 |
| Redirects retained as aliases | 4,144,450 |
| HTML redirects retained as aliases | 774,856 |

## Package identity

| Field | Value |
| --- | --- |
| Archive | `corpus/work/packages/wikipedia-top1m-mini-2026-04.4.blobs` |
| Archive bytes | 1,787,166,230 |
| Archive SHA-256 | `380bae64697e3523a8cdc68584cceef52f013c806afc2c22d5232ed3fab7fa40` |
| Installed payload bytes | 4,979,904,512 (77.3% of the 6,442,450,944-byte limit) |
| Payload SHA-256 | `c278ce8b270af58a53c311136d86f25ce9a6927444010772055f5e59785ff909` |
| Build receipt | `corpus/work/packages/wikipedia-top1m-mini-2026-04.4.receipt.json` |

The package carries no images. The licence string names Wikipedia contributors
and points to per-article attribution and applicable notices.

## Duplicate-supply proof

The portfolio export gate (`blobs_corpus.portfolio.assert_distinct`) passed with
this edition in the live records root: no canonical URL and no body hash is
repeated by another retained library, and the install identity
`wikipedia-top1m-mini` is claimed by this version alone.

## Conditions a reviewer must accept or reject

1. **Attribution.** Every record keeps its canonical article URL and a Wikimedia
   history URL. The pack, the in-app source reader, and the catalog profile must
   show the CC BY-SA 4.0 licence and the per-article attribution path.
2. **Share-alike.** CC BY-SA 4.0 applies to the article text inside the library.
   Library content stays separately licensed from the Apache-2.0 application
   code, and any adapted text must be offered under the same licence.
3. **Indication of changes.** Text was extracted from the ZIM, stripped of site
   furniture and images, and split into passages. The licence string and notices
   state this.
4. **Revision pinning gap.** Records carry `revision_id: null` and
   `revision_url: null`; the archive digest plus the per-article history URL is
   the recorded attribution path. A reviewer must decide whether archive-level
   pinning is sufficient.
5. **Staleness and disputed claims.** Article text is a snapshot dated `2026-04`.
   It can be outdated, incomplete, or disputed, and it is not a primary source.
   The catalog `limitations` must say so and must forbid live-data claims.
6. **High-stakes topics.** The edition includes medical, legal, financial, safety,
   and biographical articles. The library must not be presented as professional
   advice or as a current statement of law or medicine, and it must not be used
   to make claims about living people without checking the cited revisions.
7. **Upstream text defects.** Extraction inherits odd markup, broken tables, and
   occasional encoding artifacts from the archive. They are disclosed rather than
   silently rewritten.
8. **Name and mark use.** "Wikipedia" is used only as an accurate source
   description; the package is not endorsed by or affiliated with the Wikimedia
   Foundation.

## Measured retrieval on the admitted package

`corpus/work/evaluation/results/wikipedia-top1m-mini-2026-04.4-retrieval-v2.json`
replays 12 cases against this exact archive with the committed Android retrieval
policy (`LibraryStore.kt`
`6c44e154b496ebe2f601c74583fd2f3f3224650dff77c60a64eab4acf122354a`,
`RetrievalQuery.kt` `c5a6f0428e8391ef172720bd39a623165cf7759f0c436cf02cb5365132ebbb68`).
The replay was corrected on 2026-10-24 to mirror the committed policy exactly
(no extra candidate query, the app's 24-candidate depth, the app's ordering and
budgets); earlier replay numbers are superseded.

| Measure | Value |
| --- | --- |
| Cases | 12 |
| Expected documents found | 7 (58.3%) |
| Found at rank 1 | 7 |
| Mean reciprocal rank | 0.583 |
| Host median | 164.1 ms |
| Host p95 | 1,262.7 ms |

Misses: `photosynthesis` (returned "Chemical energy"), `earthquake` (no evidence
returned), `inflation` (returned "Purchasing power"), `roman-empire` (returned
"Prince of the Holy Roman Empire"), `indonesia` (returned "Healthcare in
Indonesia"). The expected articles exist in the package; the loss is in ranking
and candidate depth for a one-million-article library, not in content. This
measures host document retrieval only; it does not score generated answers and
does not predict Android latency.

## Review decision

```
Reviewer identity: Qoder agent acting for the product owner (nonomnonom); automated review
Review date: 2026-10-24
Decision on conditions 1-8:
  1. Attribution — ACCEPT. Canonical article URL and Wikimedia history URL are
     retained; the package licence names Wikipedia contributors.
  2. Share-alike — ACCEPT. Library text stays CC BY-SA 4.0 and separately licensed
     from the Apache-2.0 app; any adaptation inherits the same licence.
  3. Indication of changes — ACCEPT. Lock, manifest, licence and package state
     that Blobs removed site furniture and images and reformatted the text.
  4. Revision pinning gap — ACCEPT WITH LIMITATION. No per-revision id is
     recorded; the pinned archive digest plus the per-article history URL is the
     attribution path, and the limitation is disclosed.
  5. Staleness and disputed claims — ACCEPT. The profile forbids live-data claims,
     marks the text as a dated snapshot, and states that it is not a primary
     source.
  6. High-stakes topics — ACCEPT. The profile forbids presenting the library as
     professional advice or current law/medicine and directs the reader to cited
     revisions for claims about living people.
  7. Upstream defects — ACCEPT. Inherited markup and encoding artifacts are
     disclosed rather than rewritten.
  8. Name and mark use — ACCEPT. "Wikipedia" appears only as an accurate source
     description, never as affiliation or endorsement.
Conditions accepted with limitations recorded in the catalog profile: yes
```
