# Rights review record — `howto-repair` edition `2026-10.4`

Status: **decided, 2026-10-24.** Review authority was delegated by the product owner
(`nonomnonom`) to the agent maintaining this corpus. The reviewer is an automated agent, not a
human rights lawyer; this is an engineering rights review recorded under that delegated authority,
and every accepted limitation is carried into the catalog profile rather than waived silently.
This edition supersedes `2026-10.3`, which is withdrawn because it is uninstallable.

## Source identity — unchanged from `2026-10.3`

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/howto-repair-en-2026-10.3.json` |
| Source lock SHA-256 | `45a46bf1997cd9179a48e84a8f0a6a88e646155d64d1a017186ff80cfd7fa193` |
| Lock verified date | 2026-10-24 |
| Publisher (archives) | openZIM, scraper sotoki v3.1.1, creator Stack Exchange, language `eng` |
| Underlying works | Five English Stack Exchange sites, `2026-10` archives |
| Declared licence | CC BY-SA 4.0 — `https://creativecommons.org/licenses/by-sa/4.0/` |
| Attribution basis | Each post's revision history URL, retained per record |

| Site | Archive | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| mechanics | `mechanics.stackexchange.com_en_all_2026-10.zim` | 341,672,133 | `3b8790dcde480f466d0d55fae23941cdb1f06ff464a77168f27110f98d8eb923` |
| bicycles | `bicycles.stackexchange.com_en_all_2026-10.zim` | 499,254,876 | `151c3966c4db62245e6b070d1e61a68501d205655bc3f9a56fe74ab59f0bd1d8` |
| aviation | `aviation.stackexchange.com_en_all_2026-10.zim` | 510,507,366 | `31e9c2c9bb1955a64158d2fbe4db20f1bb3b5ece5ccc30ff7a9c46e8bc6f64f3` |
| 3dprinting | `3dprinting.stackexchange.com_en_all_2026-10.zim` | 120,337,699 | `97be8fa0145f9940dc40da7faea17f99aa8f88fc9d0b7d675249aa83c9ded378` |
| woodworking | `woodworking.stackexchange.com_en_all_2026-10.zim` | 106,657,287 | `2ba925d1028f9442c153569701868b5b7abc6dbb2551c620680a92be9272b062` |

The same five pinned archives are re-extracted at the same digests; no new source, host, licence,
or attribution basis is introduced. The lock file keeps its `2026-10.3` name and `edition` field
because the *sources* did not change — only the reviewed record set did, so the lane edition moved
to `2026-10.4`.

## Why a successor edition exists

`2026-10.3` shipped 7 question bodies carrying U+FFFD inherited from the pinned archives, and
recorded that as an accepted limitation. The acceptance was invalid: `LibraryValidator` on Android
refuses to install any library whose `documents` columns contain U+FFFD, so those 7 posts made the
whole 256 MB archive uninstallable. A raw byte scan of the admitted package's `library.sqlite`
finds **214** occurrences of `EF BF BD`; the same scan of this successor finds **0**
(`corpus/work/scan-fffd-2026-10-24/results.txt`).

The root cause was in this repository, not upstream: `corpus/blobs_corpus/records.py::validate_document`
exempted `documents.body` from the no-U+FFFD rule the app applies to every column. The exemption is
removed, so extraction now rejects the offending records into its counted `rejected` stream.

Records excluded by that rule in this edition, named rather than hidden:

| Title | Archive entry path | Reason |
| --- | --- | --- |
| OctoPrint Unable to Connect to Lulzbot Mini | `questions/11889/octoprint-unable-to-connect-to-lulzbot-mini` | body carries U+FFFD inherited from the pinned archive |
| Access Temperature sensor data of 3D printer via Serial connection | `questions/1555/access-temperature-sensor-data-of-3d-printer-via-serial-connection` | body carries U+FFFD inherited from the pinned archive |
| Issues with OctoPi connecting to FlashForge Creator Pro via Serial Communication | `questions/3595/issues-with-octopi-connecting-to-flashforge-creator-pro-via-serial-communicati` | body carries U+FFFD inherited from the pinned archive |
| Where did the "K" identifier prefix for US airports come from? | `questions/16205/where-did-the-k-identifier-prefix-for-us-airports-come-from` | body carries U+FFFD inherited from the pinned archive |
| Why is the take off speed and distance reduced by head winds? | `questions/24586/why-is-the-take-off-speed-and-distance-reduced-by-head-winds` | body carries U+FFFD inherited from the pinned archive |
| What is the name of this vintage helicopter trainer? | `questions/56120/what-is-the-name-of-this-vintage-helicopter-trainer` | body carries U+FFFD inherited from the pinned archive |
| What's the penalty for exceeding 250 kts below 10,000 ft MSL without authorization? | `questions/90674/what-s-the-penalty-for-exceeding-250-kts-below-10000-ft-msl-without-authorizat` | body carries U+FFFD inherited from the pinned archive |

Three of the seven are aviation posts and one is a 3D-printing post; all seven are ordinary Q&A
content, not licence-relevant material. Their exclusion is a coverage loss, recorded below, and not
a rights change.

## Record-set and package identity

| Field | Value |
| --- | --- |
| Reviewed records | `corpus/artifacts/records/howto-repair-2026-10.4/manifest.json` (SHA-256 `0cda50f5ea69b04df2f81b2c6a1433e41d92cd1fc66860d311109aca9db93e28`) |
| Extraction complete | `true` — 499,791 of 499,791 archive entries, 103 shards / 309 files |
| Curation policy | `Kiwix article-only review v3`, policy SHA-256 `6c5e1897294e8d586c9a703f816385e51dbbc399bcd393ce0963f4ec3e3f6766` |
| Documents / passages | 78,626 / 300,571 |
| Unanswered questions excluded | 9,749 (each listed in the manifest's `curation.excluded_documents`) |
| Duplicate bodies | 0 |
| Redirects retained as aliases | 253,383 |
| Normalized text | 225,765,482 characters / 227,044,503 UTF-8 bytes |
| `records_sha256` | `091df84e82a5dc332c1caad5be3dc86e51ba73b48e7032215b2f200abb2f9b2c` |
| Installed payload | 647,237,632 bytes (10.0% of the 6,442,450,944-byte limit), SHA-256 `06eadac68eb498fac2789e71b56a2c4205a3f5a2776923237307013038e37287` |
| Archive | `howto-repair-2026-10.4.blobs`, 256,576,414 bytes, SHA-256 `0b395e6eade09525f87ff61acd6a76e107fb13688be9a0a99c1b730c8e089a5c` |
| Receipt | `corpus/supply/receipts/howto-repair-2026-10.4.receipt.json` |
| Builder sources | `records.py 554a4182…`, `library.py 6407e406…`, `names.py a137dac8…` (schema 3, SQLite 3.50.4) |
| Retrieval cases | `corpus/evaluation/cases/howto-repair-retrieval-v3.json` (48 cases, pinned to the digests above) |

Coverage reconciliation, measured not assumed: `2026-10.3` carried 78,633 documents and this
edition carries 78,626 — exactly the 7 named above, with the rejection histogram changing by
`+7 Invalid body: contains a Unicode replacement character` and by nothing else.

## Measured evidence accepted with this decision

- Runtime contract: `blobs_corpus.runtime_contract.audit_library` returns 0 rejected documents,
  0 rejected passages and 0 duplicate document ids over the built database, and packaging now
  refuses to emit an archive that fails it (`package.py::assert_installable`).
- Text scan: 0 occurrences of `EF BF BD` in the packaged `library.sqlite`, against 214 in the
  edition it replaces.
- Retrieval cases: all 48 expected document ids were re-verified to resolve in this edition and to
  stay in the same library, so **0 cases were dropped** when the case file was re-pinned.
- Retrieval was **not re-replayed against this successor package.** The figures quoted for the lane
  (44 of 48 expected titles, 36 at rank 1, MRR 0.812, 110.5 ms median, 284.5 ms p95 on the
  committed host policy) were measured on the withdrawn `2026-10.3` package and are attributed to
  it. Because the successor record set is that edition minus 7 named documents and none of the 48
  cases targets them, the measurement is expected to hold, but that expectation is not evidence and
  is not recorded as one.

## Accepted limitations carried into the catalog profile

- Snapshot, not live: repair procedures, part numbers, tool specifications and safety warnings
  change; posts reflect the `2026-10` archive date.
- Community answers, not manufacturer documentation. Safety-critical work (brakes, steering,
  electrical, aviation, pressure vessels) requires current official documentation; nothing here is
  a substitute, and aviation posts in particular are not flight-manual authority.
- The 7 excluded posts are absent; a question only they answered returns no match rather than
  corrupted text.
- Upstream corruption was removed by exclusion, not by editing post text; no source character was
  substituted, so every retained post is byte-identical to the same post in `2026-10.3`.
- Unanswered questions (9,749) are excluded, so a question whose only content is the question
  itself is not retrievable.
- CC BY-SA 4.0 obligations travel with the text: attribution to Stack Exchange contributors and
  share-alike on any derivative. Per-post revision-history URLs are retained for that purpose.
- **No target-device acceptance.** No install, latency, memory or thermal measurement on a
  phone-class device backs this record; the product owner confirmed on 2026-10-24 that none can be
  attached. Emulator observations elsewhere in this repository are labelled as such.

## Duplicate supply

This lane occupies the single English "repair and fixing" slot. `howto-practical` draws on four
different Stack Exchange sites (cooking, money, travel, diy) with no archive in common, and
`blobs_corpus.portfolio.assert_distinct` is re-run after the predecessor's records edition is moved
to `corpus/retired/records/howto-repair-2026-10.3` in the same operation as this admission; its
output is recorded in `corpus/work/scan-fffd-2026-10-24/admission-log.txt`.

## Review decision

Admit `howto-repair@2026-10.4` as `release-ready` and withdraw `2026-10.3`. The licence basis is
unchanged and was already reviewed; what changed is that this edition can actually be installed by
the app that ships it.

Decided by: an automated agent under delegated review authority for the product owner
(`nonomnonom`), 2026-10-24. Not a legal opinion.
