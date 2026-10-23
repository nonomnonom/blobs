# Rights review record — `howto-repair` edition `2026-10.2`

Status: **draft, unsigned.** This file records the evidence a reviewer must confirm.
It is not a rights approval and must not be cited as one until the reviewer identity,
date, and decision below are completed by a person other than the pipeline author.

## Source identity

Five pinned English Stack Exchange archives, all scraped by openZIM/sotoki and
declared `CC-BY-SA` in their archive metadata. Each lock records the archive URL,
publisher checksum location, and a verified SHA-256.

| Site | Archive bytes | Archive SHA-256 | Lock |
| --- | ---: | --- | --- |
| mechanics | 341,672,133 | `3b8790dcde480f466d0d55fae23941cdb1f06ff464a77168f27110f98d8eb923` | `corpus/sources/stackexchange-mechanics-en-all-2026-10.json` |
| bicycles | 499,254,876 | `151c3966c4db62245e6b070d1e61a68501d205655bc3f9a56fe74ab59f0bd1d8` | `corpus/sources/stackexchange-bicycles-en-all-2026-10.json` |
| aviation | 510,507,366 | `31e9c2c9bb1955a64158d2fbe4db20f1bb3b5ece5ccc30ff7a9c46e8bc6f64f3` | `corpus/sources/stackexchange-aviation-en-all-2026-10.json` |
| 3dprinting | 120,337,699 | `97be8fa0145f9940dc40da7faea17f99aa8f88fc9d0b7d675249aa83c9ded378` | `corpus/sources/stackexchange-3dprinting-en-all-2026-10.json` |
| woodworking | 106,657,287 | `2ba925d1028f9442c153569701868b5b7abc6dbb2551c620680a92be9272b062` | `corpus/sources/stackexchange-woodworking-en-all-2026-10.json` |

Underlying works: `mechanics.stackexchange.com`, `bicycles.stackexchange.com`,
`aviation.stackexchange.com`, `3dprinting.stackexchange.com`,
`woodworking.stackexchange.com`, snapshot `2026-10` of each site's question and
answer text. Declared licence: CC BY-SA 4.0 —
`https://creativecommons.org/licenses/by-sa/4.0/`.

## What this edition contains

The lane merges five per-archive editions into one record stream and keeps only
question pages (`questions/<id>/<slug>`). Site chrome is excluded: 71,594 member
profiles, 17,533 navigation and question-listing pages, and 108 tag directory pages
were removed by review. The `robotics` archive was dropped from the lane before this
edition: its question set is dominated by ROS, Gazebo, and firmware tooling, which is
programming documentation, not repair guidance.

| Field | Value |
| --- | --- |
| Documents | 88,383 questions (with their answer text) |
| Passages | 398,429 |
| Normalized text bytes | 390,422,824 |
| Records edition | `corpus/artifacts/records/howto-repair-2026-10.2` |
| `records_sha256` | `946a47e60e9390cd9539aea453d3b1fb9ea05ce38b3527db7cee935b19ddb45f` |
| Curation policy SHA-256 | `74db9a2cf107bdd71740ee933b3cd2ae8df0c5db3753970ea8890abb06899300` |
| Redirects retained as aliases | 253,383 |

## Package identity

| Field | Value |
| --- | --- |
| Archive | `corpus/work/packages/howto-repair-2026-10.2.blobs` |
| Archive bytes | 396,417,095 |
| Archive SHA-256 | `b1ab0e1991bb062589a53d113682be28581b407b6a40e76beeb11cbe0da82171` |
| Installed payload bytes | 924,274,688 (14.3% of the 6,442,450,944-byte limit) |
| Payload SHA-256 | `1fb1db975cf91e9230e883e8b0c2117ebc9bda68b3304b4e7722eb69de226e69` |
| Build receipt | `corpus/work/packages/howto-repair-2026-10.2.receipt.json` |

The package carries no images: the extraction keeps question and answer text only.
Licence strings on the package name Stack Exchange contributors and point to each
post's revision history.

## Conditions a reviewer must accept or reject

1. **Attribution.** Every record keeps its canonical question URL and a
   `history_url`. The pack, the in-app source reader, and the catalog profile must
   show the CC BY-SA 4.0 licence and per-post attribution path.
2. **Share-alike.** CC BY-SA 4.0 applies to the answer text inside the library. Library
   content must stay separately licensed from the Apache-2.0 application code, and any
   adapted text must be offered under the same licence.
3. **Indication of changes.** Text was extracted from the ZIM, stripped of site
   furniture and images, and split into passages. The licence string and notices must
   say so; the current manifest and lock strings already do.
4. **Author attribution granularity.** Records name the site and the post URL, not the
   individual authors of each question and answer. The archive is pinned by digest, so
   the underlying revision is reproducible, but a reviewer must decide whether
   per-post history links satisfy CC BY-SA 4.0 attribution for community authors.
5. **Mixed authorship and third-party content.** Answers may quote manuals, standards,
   or vendor documentation. Community moderation removes some copied material but does
   not guarantee licence clearance for every quotation.
6. **Advice risk.** Vehicle, aviation, electrical, and machinery answers carry injury
   and property-damage risk and can be wrong or outdated. The catalog `limitations`
   must say this plainly; the library must not be presented as professional or
   manufacturer guidance.
7. **Aviation content.** The aviation site includes operational and regulatory
   discussion. Shipping it as general reference is acceptable only with the limitations
   text above; it must never be framed as pilot or maintenance instruction.
8. **Upstream text defects.** A small number of records contain Unicode replacement
   characters inherited from the archive. A reviewer must decide whether to ship them
   or drop those records.
9. **Name and mark use.** "Stack Exchange" is used only as an accurate source
   description, not as an endorsement or affiliation.

## Review decision (to be completed by the reviewer)

```
Reviewer identity:
Review date:
Decision on conditions 1-9:
Conditions accepted with limitations recorded in the catalog profile: yes / no
```
