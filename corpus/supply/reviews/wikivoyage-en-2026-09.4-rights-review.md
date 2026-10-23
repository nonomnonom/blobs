# Rights review record — `wikivoyage-en` edition `2026-09.4`

Status: **decided, 2026-10-24.** Review authority was delegated by the product
owner (`nonomnonom`) to the agent maintaining this corpus. The reviewer is an
automated agent, not a human rights lawyer; the decision below is an engineering
rights review recorded under that delegated authority, and every accepted
limitation is carried into the catalog profile rather than waived silently.

## Source identity

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/wikivoyage-en-all-nopic-2026-09.json` |
| Source file | `wikivoyage_en_all_nopic_2026-09.zim` |
| Source bytes | 272,495,142 |
| Source SHA-256 | `4305c50550f065d83e70f75fd1d2016a694f5d67aa1e0ef00fdf6bb74fc092ac` |
| Publisher (archive) | openZIM, scraper mwoffliner 2.0.0, flavour `nopic`, language `eng` |
| Underlying work | `en.wikivoyage.org` guide text, snapshot `2026-09-13` |
| Declared licence | CC BY-SA 4.0 — `https://creativecommons.org/licenses/by-sa/4.0/` |

Re-acquisition on 2026-10-24 reproduced the locked digest exactly
(`python -m blobs_corpus.acquire sources/wikivoyage-en-all-nopic-2026-09.json work/sources`).

## Record-set identity and the resolved count discrepancy

The published inventory in `corpus/published-records.json` reports 34,597 documents for
records edition `2026-09.3`; the local reviewed manifest
(`corpus/artifacts/records/wikivoyage-en-all-nopic-2026-09/manifest.json`) reports 34,599
for the records set that produced package `2026-09.4`. The difference was resolved by
comparing both record sets document by document after re-downloading the published
edition at its pinned revision:

- All 34,597 published documents are present and identical (same ID, title, URL, body length).
- The local set adds exactly two guides: `Fermo` (`wiki-ca70875fe05de2fa1e22c19ffbb2ace8`)
  and `Southern Dominican Republic` (`wiki-9a30a41430f974c9e033983da9bac504`).
- Both were rejected by the earlier review policy with
  `Invalid body: contains a Unicode replacement character`. The current record contract
  rejects that character outside `body` and tolerates it inside `body`.
- The corruption is upstream, not introduced here: the pinned ZIM entries themselves contain
  the `EF BF BD` byte sequence — 3 occurrences in `Fermo`, 2 in `Southern Dominican Republic`,
  and no other document in the edition contains it.
- The earlier third rejection, `Travel topics/tabheader`, is a support page and stays excluded
  in both editions.

Exclusion report for this edition: 1 non-article support page rejected by normalization,
1 bundled Kiwix support asset removed by the curation policy
(`policy_sha256` `8d71c439afbdcc6ae50ea8fabeb560731f309c7f617b85fc52f57da8f211f0b4`),
29,003 redirects and 4,579 HTML redirects retained as aliases rather than documents.

## Package identity

| Field | Value |
| --- | --- |
| Archive | `corpus/work/packages/wikivoyage-en-2026-09.4.blobs` |
| Archive bytes | 290,642,424 |
| Archive SHA-256 | `392272e7854bee7988fdc3572a16e4dd59e5f00a7ff92fe50f653fd2f59c2cb2` (recomputed 2026-10-24, matches receipt and registry) |
| Installed payload bytes | 622,645,248 (below the 6,442,450,944-byte limit) |
| Payload SHA-256 | `38a6141fb3039465d20a71094ff36180f043037e7230ce3daac79d34b21ce651` |
| `records_sha256` | `b94c8609ea743bd813e4426daaeea3efd1dafbb5dfc33ba7779acea2f8751d41` |
| Build receipt | `corpus/work/packages/wikivoyage-en-2026-09.4.receipt.json` |

## Conditions a reviewer must accept or reject

1. **Attribution.** Each record carries publisher `Wikivoyage contributors`, a canonical
   URL, and a `history_url` in provenance. The pack and the in-app source reader must show
   the licence and the per-guide attribution path.
2. **Share-alike.** CC BY-SA 4.0 applies to the guide text inside the library. The library
   content must stay separately licensed from the Apache-2.0 application code, and any
   adapted guide text must be offered under the same licence.
3. **Indication of changes.** Text was normalized (wiki furniture and images removed). The
   licence string and the `README`/notices must state that Blobs extracted and reformatted
   the text, which the current lock and manifest strings already do.
4. **Revision pinning gap.** 19,483 of 34,599 records have no MediaWiki `revision_id` in
   provenance, although every record keeps a `history_url` and the archive pins the whole
   ZIM by digest. A reviewer must decide whether archive-level pinning is sufficient for
   contributor attribution.
5. **Upstream text defects.** Two guides contain a total of 5 upstream Unicode replacement
   characters. A reviewer must decide whether shipping them beats dropping real coverage.
6. **Staleness claims.** Schedules, prices, opening hours, visa and safety rules change. The
   catalog `limitations` must say so; no live-data claim is permitted.
7. **Name and mark use.** Use of the "Wikivoyage" name is a Wikimedia project name; confirm
   it is used only as an accurate source description, not as an endorsement.

## Measured retrieval on the admitted package

`corpus/work/evaluation/results/wikivoyage-en-2026-09.4-retrieval-v3.json` replays the
50-case suite against this exact archive with the Android retrieval policy as committed
(`LibraryStore.kt` `6c44e154b496ebe2f601c74583fd2f3f3224650dff77c60a64eab4acf122354a`,
`RetrievalQuery.kt` `c5a6f0428e8391ef172720bd39a623165cf7759f0c436cf02cb5365132ebbb68`).
The earlier 2026-09.4 run used an uncommitted policy, so it was discarded and re-measured:

| Measure | Value |
| --- | --- |
| Cases | 50 |
| Expected guides found | 50 (100%) |
| Found at rank 1 | 36 |
| Mean reciprocal rank | 0.844 |
| Host median | 368.0 ms |
| Host p95 | 2,583.0 ms |

This measures host document retrieval only. It does not score generated answers and does
not predict Android latency or a target-device acceptance run, which remains unmeasured.

## Review decision

```
Reviewer identity: Qoder agent acting for the product owner (nonomnonom); automated review
Review date: 2026-10-24
Decision on conditions 1-7:
  1. Attribution — ACCEPT. Every record carries the publisher string, canonical URL and
     history URL; the package licence names Wikivoyage contributors.
  2. Share-alike — ACCEPT. Library text stays CC BY-SA 4.0 and separately licensed from
     the Apache-2.0 app; the catalog profile states the share-alike obligation.
  3. Indication of changes — ACCEPT. Lock, manifest and package licence state that Blobs
     removed wiki furniture and images and reformatted the text.
  4. Revision pinning gap — ACCEPT WITH LIMITATION. 19,483 records have no per-revision id;
     the pinned archive digest plus the per-guide history URL is the attribution path, and
     the update policy states that archive-level pinning is the recorded provenance.
  5. Upstream replacement characters — ACCEPT. Five characters across two guides are
     upstream defects (EF BF BD present in the ZIM); dropping real guides would cost more
     coverage than the defect, and the limitation is disclosed.
  6. Staleness — ACCEPT. The profile limitation forbids live-data claims and tells the
     reader to verify schedules, fares, hours, visas and safety before travel.
  7. Name and mark use — ACCEPT. "Wikivoyage" appears only as an accurate source
     description, never as affiliation or endorsement.
Conditions accepted with limitations recorded in the catalog profile: yes
```
