# Rights review record — `howto-repair` edition `2026-10.3`

Status: **decided, 2026-10-24.** Review authority was delegated by the product
owner (`nonomnonom`) to the agent maintaining this corpus. The reviewer is an
automated agent, not a human rights lawyer; the decision below is an engineering
rights review recorded under that delegated authority, and every accepted
limitation is carried into the catalog profile rather than waived silently.

## Source identity

Five pinned English Stack Exchange archives, scraped by openZIM/sotoki and
declared `CC-BY-SA` in their archive metadata. Each lock records the archive URL,
publisher checksum location, and a verified SHA-256.

| Site | Archive | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| mechanics | `mechanics.stackexchange.com_en_all_2026-10.zim` | 341,672,133 | `3b8790dcde480f466d0d55fae23941cdb1f06ff464a77168f27110f98d8eb923` |
| bicycles | `bicycles.stackexchange.com_en_all_2026-10.zim` | 499,254,876 | `151c3966c4db62245e6b070d1e61a68501d205655bc3f9a56fe74ab59f0bd1d8` |
| aviation | `aviation.stackexchange.com_en_all_2026-10.zim` | 510,507,366 | `31e9c2c9bb1955a64158d2fbe4db20f1bb3b5ece5ccc30ff7a9c46e8bc6f64f3` |
| 3dprinting | `3dprinting.stackexchange.com_en_all_2026-10.zim` | 120,337,699 | `97be8fa0145f9940dc40da7faea17f99aa8f88fc9d0b7d675249aa83c9ded378` |
| woodworking | `woodworking.stackexchange.com_en_all_2026-10.zim` | 106,657,287 | `2ba925d1028f9442c153569701868b5b7abc6dbb2551c620680a92be9272b062` |

Locks: `corpus/sources/stackexchange-<site>-en-all-2026-10.json`. Underlying
works: the five sites' question and answer text, snapshot `2026-10`. Declared
licence: CC BY-SA 4.0 — `https://creativecommons.org/licenses/by-sa/4.0/`.

## What this edition contains

Only question pages (`questions/<id>/<slug>`) become records. Site chrome —
member profiles, tag directories, paged question listings, bundled support
assets — is excluded. A question page with zero answers records a problem rather
than an answer, so 9,749 unanswered questions are excluded from the reviewed
edition. Bodies are normalized once and deduplicated by exact `body_sha256`
within the edition; no duplicate body survived.

| Field | Value |
| --- | --- |
| Documents | 78,633 question pages with answer text |
| Passages | 300,594 |
| Normalized text characters | 225,782,678 |
| Records edition | `corpus/artifacts/records/howto-repair-2026-10.3` |
| `records_sha256` | `cabf49750034f9ca6e401199a1b150d99f8b945c721547686a0700e008486c2d` |
| Curation policy SHA-256 | `b4045a0b11a215dab8d18bcfdceb22a0cd4d95d8e753442a4ca5f92165f9dd1b` |
| Unanswered questions excluded | 9,749 |
| Duplicate bodies | 0 |
| Redirects retained as aliases | 253,383 |

## Package identity

| Field | Value |
| --- | --- |
| Archive | `corpus/work/packages/howto-repair-2026-10.3.blobs` |
| Archive bytes | 256,609,973 |
| Archive SHA-256 | `ed54104c0de04022e86495a2c0f293cf6eb9ab8d652bafbebf7f4a72d0b87ade` |
| Installed payload bytes | 647,344,128 (10.0% of the 6,442,450,944-byte limit) |
| Payload SHA-256 | `8fd8297be9e98c963e4af47da04529e8e3d17e17ec563d8a1a67cc5cd0c09b41` |
| Build receipt | `corpus/work/packages/howto-repair-2026-10.3.receipt.json` |

The package carries no images: extraction keeps question and answer text only.
The licence string names Stack Exchange contributors and points to each post's
revision history.

## Duplicate-supply proof against `howto-practical` `2026-10.3`

The two lanes were compared record by record after curation: 0 shared canonical
URLs and 0 shared body hashes across 78,633 + 189,297 records. No supply is
repeated between the lanes.

## Conditions a reviewer must accept or reject

1. **Attribution.** Every record keeps its canonical question URL, author display
   name, answer author names, and a `history_url` per post. The pack, the in-app
   source reader, and the catalog profile must show the CC BY-SA 4.0 licence and
   the per-post attribution path.
2. **Share-alike.** CC BY-SA 4.0 applies to the answer text inside the library.
   Library content stays separately licensed from the Apache-2.0 application
   code, and any adapted text must be offered under the same licence.
3. **Indication of changes.** Text was extracted from the ZIM, stripped of site
   furniture and images, normalized, and split into passages. The licence string
   and notices state this.
4. **Author attribution granularity.** Records name the site, each question and
   answer author, and the post revision URL, but not a stable author profile
   identifier. The archive is pinned by digest, so the underlying revision is
   reproducible; per-post history links are the recorded attribution path.
5. **Mixed authorship and third-party content.** Answers may quote manuals,
   standards, or vendor documentation. Community moderation removes some copied
   material but does not guarantee licence clearance for every quotation.
6. **Advice risk.** Vehicle, aviation, electrical, and machinery answers carry
   injury and property-damage risk and can be wrong or outdated. The catalog
   `limitations` must say this plainly; the library must not be presented as
   professional or manufacturer guidance.
7. **Aviation content.** The aviation site includes operational and regulatory
   discussion. Shipping it as general reference is acceptable only with the
   limitations text above; it must never be framed as pilot or maintenance
   instruction.
8. **Upstream text defects.** A small number of records contain Unicode
   replacement characters inherited from the archive. They are disclosed as an
   upstream defect rather than silently rewritten.
9. **Name and mark use.** "Stack Exchange" is used only as an accurate source
   description, not as an endorsement or affiliation.

## Measured retrieval on the admitted package

`corpus/work/evaluation/results/howto-repair-2026-10.3-retrieval-v3.json` replays
48 cases against this exact archive with the committed Android retrieval policy
(`LibraryStore.kt` `6c44e154b496ebe2f601c74583fd2f3f3224650dff77c60a64eab4acf122354a`,
`RetrievalQuery.kt` `c5a6f0428e8391ef172720bd39a623165cf7759f0c436cf02cb5365132ebbb68`).
The replay was corrected on 2026-10-24 to mirror the committed policy exactly
(no extra candidate query, the app's 24-candidate depth, the app's ordering and
budgets); earlier replay numbers are superseded.

| Measure | Value |
| --- | --- |
| Cases | 48 |
| Expected documents found | 44 (91.7%) |
| Found at rank 1 | 36 |
| Mean reciprocal rank | 0.812 |
| Host median | 110.5 ms |
| Host p95 | 284.5 ms |

Misses: `battery-two-volts`, `check-engine-light`, `clean-chain`,
`bottles-to-filament`. This measures host document retrieval only; it does not
score generated answers and does not predict Android latency.

## Review decision

```
Reviewer identity: Qoder agent acting for the product owner (nonomnonom); automated review
Review date: 2026-10-24
Decision on conditions 1-9:
  1. Attribution — ACCEPT. Canonical URL, per-post author names and revision URL
     are retained; the package licence names Stack Exchange contributors.
  2. Share-alike — ACCEPT. Library text stays CC BY-SA 4.0 and separately licensed
     from the Apache-2.0 app; any adaptation inherits the same licence.
  3. Indication of changes — ACCEPT. Lock, manifest, licence and package state
     that Blobs removed site furniture and images and reformatted the text.
  4. Author attribution granularity — ACCEPT WITH LIMITATION. The archive digest
     plus per-post revision URL is the attribution path; no stable per-author
     identifier is recorded, and the limitation is disclosed.
  5. Mixed authorship — ACCEPT WITH LIMITATION. Quotations inside answers are not
     individually cleared; community moderation and per-post history links are
     the evidence path.
  6. Advice risk — ACCEPT. The profile limitation forbids presenting the library
     as professional or manufacturer guidance and warns that answers can be
     wrong or outdated.
  7. Aviation content — ACCEPT. The profile states aviation material is general
     reference, never pilot or maintenance instruction.
  8. Upstream defects — ACCEPT. Replacement characters are inherited from the
     pinned archives and disclosed rather than rewritten.
  9. Name and mark use — ACCEPT. "Stack Exchange" appears only as an accurate
     source description, never as affiliation or endorsement.
Conditions accepted with limitations recorded in the catalog profile: yes
```
