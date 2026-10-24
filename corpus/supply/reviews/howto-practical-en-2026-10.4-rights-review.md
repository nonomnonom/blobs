# Rights review record — `howto-practical` edition `2026-10.4`

Status: **decided, 2026-10-24.** Review authority was delegated by the product owner
(`nonomnonom`) to the agent maintaining this corpus. The reviewer is an automated agent, not a
human rights lawyer; this is an engineering rights review recorded under that delegated authority,
and every accepted limitation is carried into the catalog profile rather than waived silently.
This edition supersedes `2026-10.3`, which is withdrawn because it is uninstallable.

## Source identity — unchanged from `2026-10.3`

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/howto-practical-en-2026-10.3.json` |
| Source lock SHA-256 | `1c90ff2114b75d5f8d7bf3f8718671271920b800f8a87d5de42710425411025f` |
| Lock verified date | 2026-10-24 |
| Publisher (archives) | openZIM, scraper sotoki v3.1.1, creator Stack Exchange, language `eng` |
| Underlying works | Four English Stack Exchange sites, `2026-10` archives |
| Declared licence | CC BY-SA 4.0 — `https://creativecommons.org/licenses/by-sa/4.0/` |
| Attribution basis | Each post's revision history URL, retained per record |

| Site | Archive | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| cooking | `cooking.stackexchange.com_en_all_2026-10.zim` | 238,098,128 | `b5dc3a79b681be1afea32dd65e837c604e7dd7072f51622414a7a1d61fc52541` |
| money | `money.stackexchange.com_en_all_2026-10.zim` | 254,194,306 | `6a4a4b96b7856e46f3c1d368ca44b5e8036497b7fab64efc2ed3e90a81e80b94` |
| travel | `travel.stackexchange.com_en_all_2026-10.zim` | 462,009,141 | `55c364aac731f67070eba59bc68033a32e57ba04280348a466843999174e8697` |
| diy | `diy.stackexchange.com_en_all_2026-10.zim` | 2,086,024,326 | `df795a094698282164ddee422814f88bf84841a2413c89ac23401efd71f22731` |

The same four pinned archives are re-extracted at the same digests; no new source, host, licence, or
attribution basis is introduced. The lock keeps its `2026-10.3` name and `edition` field because the
*sources* did not change — only the reviewed record set did, so the lane edition moved to
`2026-10.4`.

## Why a successor edition exists

`2026-10.3` shipped 3 question bodies carrying U+FFFD inherited from the pinned archives and
recorded that as an accepted limitation. The acceptance was invalid: `LibraryValidator` on Android
refuses to install any library whose `documents` columns contain U+FFFD, so those 3 posts made the
whole 567 MB archive uninstallable. A raw byte scan of the admitted package's `library.sqlite` finds
**6** occurrences of `EF BF BD`; the same scan of this successor finds **0**
(`corpus/work/scan-fffd-2026-10-24/results.txt`).

The root cause was in this repository, not upstream: `corpus/blobs_corpus/records.py::validate_document`
exempted `documents.body` from the no-U+FFFD rule the app applies to every column. The exemption is
removed, so extraction now rejects the offending records into its counted `rejected` stream.

Records excluded by that rule in this edition, named rather than hidden:

| Title | Archive entry path | Reason |
| --- | --- | --- |
| Turkey Dinners For A Group of 140 People | `questions/96494/turkey-dinners-for-a-group-of-140-people` | body carries U+FFFD inherited from the pinned archive |
| Is it possible to trace the source of bills that I've collected in USA by serial number? | `questions/81983/is-it-possible-to-trace-the-source-of-bills-that-i-ve-collected-in-usa-by-seri` | body carries U+FFFD inherited from the pinned archive |
| How do I carry parrots on an Indian Railway train? | `questions/47478/how-do-i-carry-parrots-on-an-indian-railway-train` | body carries U+FFFD inherited from the pinned archive |

None of the three is licence-relevant material; their exclusion is a coverage loss, recorded below,
and not a rights change.

## Record-set and package identity

| Field | Value |
| --- | --- |
| Reviewed records | `corpus/artifacts/records/howto-practical-2026-10.4/manifest.json` (SHA-256 `dc9cd320cd6e5b484d57655fc974c4d05aa757bef800f0fdf9769239ae772281`) |
| Extraction complete | `true` — 1,140,270 of 1,140,270 archive entries, 231 shards / 693 files |
| Curation policy | `Kiwix article-only review v3`, policy SHA-256 `6c5e1897294e8d586c9a703f816385e51dbbc399bcd393ce0963f4ec3e3f6766` |
| Documents / passages | 189,294 / 672,866 |
| Unanswered questions excluded | 23,917 (each listed in the manifest's `curation.excluded_documents`) |
| Duplicate bodies | 0 |
| Redirects retained as aliases | 591,247 |
| Normalized text | 482,740,666 characters / 485,557,613 UTF-8 bytes |
| `records_sha256` | `23877c5b9107b35f838f25f8903404265a5d42b40534b324fae8eda20e5b223b` |
| Installed payload | 1,454,772,224 bytes (22.6% of the 6,442,450,944-byte limit), SHA-256 `9efdf2ee28db79d05ff8c2e933073fbf6c7a9bbbac20b304c8e985af0d68d481` |
| Archive | `howto-practical-2026-10.4.blobs`, 567,686,098 bytes, SHA-256 `2c5d4867931a14fd9220990090fb21375e0fdc9255670abaeee78d0c1167d028` |
| Receipt | `corpus/work/packages/howto-practical-2026-10.4.receipt.json` |
| Builder sources | `records.py 554a4182…`, `library.py 6407e406…`, `names.py a137dac8…` (schema 3, SQLite 3.50.4) |
| Retrieval cases | `corpus/evaluation/cases/howto-practical-retrieval-v3.json` (36 cases, pinned to the digests above) |

Coverage reconciliation, measured not assumed: `2026-10.3` carried 189,297 documents and this
edition carries 189,294 — exactly the 3 named above, with the rejection histogram changing by
`+3 Invalid body: contains a Unicode replacement character` and by nothing else.

## Measured evidence accepted with this decision

- Runtime contract: `blobs_corpus.runtime_contract.audit_library` returns 0 rejected documents,
  0 rejected passages and 0 duplicate document ids over the built database, and packaging now
  refuses to emit an archive that fails it (`package.py::assert_installable`).
- Text scan: 0 occurrences of `EF BF BD` in the packaged `library.sqlite`, against 6 in the edition
  it replaces.
- Retrieval cases: all 36 expected document ids were re-verified to resolve in this edition, so
  **0 cases were dropped** when the case file was re-pinned.
- Retrieval was **not re-replayed against this successor package.** The figures quoted for the lane
  (30 of 36 expected titles, 23 at rank 1, MRR 0.703, 294.3 ms median, 608.3 ms p95 on the
  committed host policy) were measured on the withdrawn `2026-10.3` package and are attributed to
  it. Because the successor record set is that edition minus 3 named documents and none of the 36
  cases targets them, the measurement is expected to hold, but that expectation is not evidence and
  is not recorded as one.

## Accepted limitations carried into the catalog profile

- Snapshot, not live: prices, tax rules, visa and entry requirements, food-safety guidance and
  building codes change; posts reflect the `2026-10` archive date and the jurisdiction of whoever
  answered.
- Community answers are not professional advice. Money questions are not financial advice, food
  questions are not food-safety authority, travel questions are not visa or legal authority, and
  DIY answers can describe work that a licensed tradesperson must do or that a local code forbids.
- Jurisdiction is unstated in most posts: a US answer can look universal and is not.
- The 3 excluded posts are absent; a question only they answered returns no match rather than
  corrupted text.
- Upstream corruption was removed by exclusion, not by editing post text; no source character was
  substituted, so every retained post is byte-identical to the same post in `2026-10.3`.
- Unanswered questions (23,917) are excluded, so a question whose only content is the question
  itself is not retrievable.
- CC BY-SA 4.0 obligations travel with the text: attribution to Stack Exchange contributors and
  share-alike on any derivative. Per-post revision-history URLs are retained for that purpose.
- **No target-device acceptance.** No install, latency, memory or thermal measurement on a
  phone-class device backs this record; the product owner confirmed on 2026-10-24 that none can be
  attached. Emulator observations elsewhere in this repository are labelled as such.

## Duplicate supply

This lane occupies the single English "practical how-to" slot. `howto-repair` draws on five
different Stack Exchange sites (mechanics, bicycles, aviation, 3dprinting, woodworking) with no
archive in common, and `blobs_corpus.portfolio.assert_distinct` is re-run after the predecessor's
records edition is moved to `corpus/retired/records/howto-practical-2026-10.3` in the same operation
as this admission; its output is recorded in
`corpus/work/scan-fffd-2026-10-24/admission-log.txt`.

## Review decision

Admit `howto-practical@2026-10.4` as `release-ready` and withdraw `2026-10.3`. The licence basis is
unchanged and was already reviewed; what changed is that this edition can actually be installed by
the app that ships it.

Decided by: an automated agent under delegated review authority for the product owner
(`nonomnonom`), 2026-10-24. Not a legal opinion.
