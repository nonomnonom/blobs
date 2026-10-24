# Rights review record — `howto-practical` edition `2026-10.3`

Status: **decided, 2026-10-24.** Review authority was delegated by the product
owner (`nonomnonom`) to the agent maintaining this corpus. The reviewer is an
automated agent, not a human rights lawyer; the decision below is an engineering
rights review recorded under that delegated authority, and every accepted
limitation is carried into the catalog profile rather than waived silently.

## Source identity

Four pinned English Stack Exchange archives, scraped by openZIM/sotoki and
declared `CC-BY-SA` in their archive metadata. Each lock records the archive URL,
publisher checksum location, and a verified SHA-256.

| Site | Archive | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| cooking | `cooking.stackexchange.com_en_all_2026-10.zim` | 238,098,128 | `b5dc3a79b681be1afea32dd65e837c604e7dd7072f51622414a7a1d61fc52541` |
| money | `money.stackexchange.com_en_all_2026-10.zim` | 254,194,306 | `6a4a4b96b7856e46f3c1d368ca44b5e8036497b7fab64efc2ed3e90a81e80b94` |
| travel | `travel.stackexchange.com_en_all_2026-10.zim` | 462,009,141 | `55c364aac731f67070eba59bc68033a32e57ba04280348a466843999174e8697` |
| diy | `diy.stackexchange.com_en_all_2026-10.zim` | 2,086,024,326 | `df795a094698282164ddee422814f88bf84841a2413c89ac23401efd71f22731` |

Locks: `corpus/sources/stackexchange-<site>-en-all-2026-10.json`. Underlying
works: the four sites' question and answer text, snapshot `2026-10`. Declared
licence: CC BY-SA 4.0 — `https://creativecommons.org/licenses/by-sa/4.0/`.

## What this edition contains

Only question pages (`questions/<id>/<slug>`) become records. Site chrome —
member profiles, tag directories, paged question listings, bundled support
assets — is excluded. A question page with zero answers records a problem rather
than an answer, so 23,917 unanswered questions are excluded from the reviewed
edition. Bodies are normalized once and deduplicated by exact `body_sha256`
within the edition; no duplicate body survived.

| Field | Value |
| --- | --- |
| Documents | 189,297 question pages with answer text |
| Passages | 672,886 |
| Normalized text characters | 482,756,964 |
| Records edition | `corpus/artifacts/records/howto-practical-2026-10.3` |
| `records_sha256` | `8d9c303cf3f003c04329043167be7faca274f8de69132fdf57b7163fd09385a1` |
| Curation policy SHA-256 | `b4045a0b11a215dab8d18bcfdceb22a0cd4d95d8e753442a4ca5f92165f9dd1b` |
| Unanswered questions excluded | 23,917 |
| Duplicate bodies | 0 |
| Redirects retained as aliases | 591,247 |

## Package identity

| Field | Value |
| --- | --- |
| Archive | `corpus/work/packages/howto-practical-2026-10.3.blobs` |
| Archive bytes | 567,225,476 |
| Archive SHA-256 | `f5202b6fc9e89016c2f435fc11ea9c6c8f729a9519b23dae0ab607088f388f10` |
| Installed payload bytes | 1,454,694,400 (22.6% of the 6,442,450,944-byte limit) |
| Payload SHA-256 | `1a8c864ab788057e0c30a8edd0b485ea8febdd0a899867a793d755298a0e3894` |
| Build receipt | `corpus/work/packages/howto-practical-2026-10.3.receipt.json` |

The package carries no images: extraction keeps question and answer text only.
The licence string names Stack Exchange contributors and points to each post's
revision history.

## Duplicate-supply proof against `howto-repair` `2026-10.3`

The two lanes were compared record by record after curation: 0 shared canonical
URLs and 0 shared body hashes across 189,297 + 78,633 records. No supply is
repeated between the lanes.

## Conditions a reviewer must accept or reject

1. **Attribution.** Every record keeps its canonical question URL, author display
   names, answer author names, and a `history_url` per post. The pack, the in-app
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
5. **Mixed authorship and third-party content.** Answers may quote recipes,
   product manuals, financial guidance, or government rules. Community moderation
   removes some copied material but does not guarantee licence clearance for
   every quotation.
6. **Advice risk.** Cooking safety, personal finance, travel rules, and household
   electrical or plumbing answers carry financial, legal, and injury risk and can
   be wrong or outdated. The catalog `limitations` must say this plainly; the
   library must not be presented as professional financial, legal, medical, or
   trade guidance.
7. **Jurisdiction and staleness.** Travel and money answers are jurisdiction- and
   date-specific. The catalog `limitations` must forbid live-data claims and tell
   the reader to verify current rules with the issuing authority.
8. **Upstream text defects.** A small number of records contain Unicode
   replacement characters inherited from the archive. They are disclosed as an
   upstream defect rather than silently rewritten.
9. **Name and mark use.** "Stack Exchange" is used only as an accurate source
   description, not as an endorsement or affiliation.

## Measured retrieval on the admitted package

`corpus/work/evaluation/results/howto-practical-2026-10.3-retrieval-v3.json`
replays 36 cases against this exact archive with the committed Android retrieval
policy (`LibraryStore.kt`
`6c44e154b496ebe2f601c74583fd2f3f3224650dff77c60a64eab4acf122354a`,
`RetrievalQuery.kt` `c5a6f0428e8391ef172720bd39a623165cf7759f0c436cf02cb5365132ebbb68`).
The replay was corrected on 2026-10-24 to mirror the committed policy exactly
(no extra candidate query, the app's 24-candidate depth, the app's ordering and
budgets); earlier replay numbers are superseded.

| Measure | Value |
| --- | --- |
| Cases | 36 |
| Expected documents found | 30 (83.3%) |
| Found at rank 1 | 23 |
| Mean reciprocal rank | 0.703 |
| Host median | 294.3 ms |
| Host p95 | 608.3 ms |

Misses: `tomato-sauce-acidity`, `debt-or-bankruptcy`, `expired-passport-flight`,
`luggage-theft`, `breaker-damage`, `pipe-leaks-winter`. This measures host
document retrieval only; it does not score generated answers and does not predict
Android latency.

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
     as professional financial, legal, medical, or trade guidance and warns that
     answers can be wrong or outdated.
  7. Jurisdiction and staleness — ACCEPT. The profile states that travel and money
     answers are jurisdiction- and date-specific, forbids live-data claims, and
     directs the reader to the issuing authority.
  8. Upstream defects — ACCEPT. Replacement characters are inherited from the
     pinned archives and disclosed rather than rewritten.
  9. Name and mark use — ACCEPT. "Stack Exchange" appears only as an accurate
     source description, never as affiliation or endorsement.
Conditions accepted with limitations recorded in the catalog profile: yes
```
