# Rights review record — `wikivoyage-en` edition `2026-09.5`

Status: **decided, 2026-10-24.** Review authority was delegated by the product owner
(`nonomnonom`) to the agent maintaining this corpus. The reviewer is an automated agent, not a
human rights lawyer; this is an engineering rights review recorded under that delegated
authority, and every accepted limitation is carried into the catalog profile rather than waived
silently. This edition supersedes `2026-09.4`, which is withdrawn because it is uninstallable.

## Source identity — unchanged from `2026-09.4`

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/wikivoyage-en-all-nopic-2026-09.json` |
| Source file | `wikivoyage_en_all_nopic_2026-09.zim` |
| Source bytes | 272,495,142 |
| Source SHA-256 | `4305c50550f065d83e70f75fd1d2016a694f5d67aa1e0ef00fdf6bb74fc092ac` |
| Publisher (archive) | openZIM, scraper mwoffliner 2.0.0, flavour `nopic`, language `eng` |
| Underlying work | `en.wikivoyage.org` guide text, snapshot `2026-09-13` |
| Declared licence | CC BY-SA 4.0 — `https://creativecommons.org/licenses/by-sa/4.0/` |

The same pinned archive is re-acquired and re-extracted; no new source, host, licence, or
attribution basis is introduced by this edition. The extraction ran against the local copy at
the locked digest, so provenance is identical to the reviewed edition it replaces.

## Why a successor edition exists

`2026-09.4` recorded "five Unicode replacement characters are inherited from the pinned upstream
archive" as an accepted limitation. That acceptance was invalid: `LibraryValidator` on Android
refuses to install any library whose `documents` columns contain U+FFFD, so one affected guide
made the whole archive uninstallable — measured as `status=PREPARATION_FAILED` when installing
`wikivoyage-en-2026-09.4` on `sdk_gphone64_x86_64` (`corpus/evaluation/results/answer-offline-grounding-001.json`).
`corpus/blobs_corpus/records.py` had exempted `documents.body` from the no-U+FFFD rule while the
app applies it to every column; the exemption is removed, so extraction now rejects the offending
records into its counted `rejected` stream instead of shipping them.

Records excluded by that rule in this edition, named rather than hidden:

| Title | Document ID | Reason |
| --- | --- | --- |
| Fermo | `wiki-ca70875fe05de2fa1e22c19ffbb2ace8` | body carries U+FFFD inherited from the pinned archive |
| Southern Dominican Republic | `wiki-9a30a41430f974c9e033983da9bac504` | body carries U+FFFD inherited from the pinned archive |

Excluding two guides resolves the earlier count discrepancy at its real cause: this edition
carries 34,597 guides, the same count as the published `2026-09.3`, and it is the same record set
as `2026-09.4` minus exactly those two documents (0 added, verified id by id).

## Record-set and package identity

| Field | Value |
| --- | --- |
| Reviewed records | `corpus/artifacts/records/wikivoyage-en-all-nopic-2026-09.5/manifest.json` (SHA-256 `021d6e8818921951355f0563061be4587f91a6150a2c58292e64b28180595c9e`) |
| Documents / passages | 34,597 / 520,143 |
| `records_sha256` | `9d6c9b94c172b1b8d86ba2b93d876097fdebe74f96c88212ec67770ecf1d7c1b` |
| Installed payload | 622,776,320 bytes, SHA-256 `380896b48d45f1d2e586d7d0f793ef57c79f861083867d68058d9b1e92631226` |
| Archive | `wikivoyage-en-2026-09.5.blobs`, 290,811,362 bytes, SHA-256 `f44f582e2836db30f0091c647ac678411089a5059f390bdc0f4e9403fb90edcc` |
| Receipt | `corpus/work/packages/wikivoyage-en-2026-09.5.receipt.json` |
| Retrieval cases | `corpus/evaluation/cases/wikivoyage-en-retrieval-v3.json` (48 cases, pinned to the digests above) |
| Retrieval report | `corpus/work/evaluation/results/wikivoyage-en-2026-09.5-retrieval-v3.json` |

Distinctness: the superseded edition was moved to `corpus/retired/records/wikivoyage-en-all-nopic-2026-09`
(a reversible move, not a deletion), and `blobs_corpus.portfolio.assert_distinct` now reports
`repeated_supply: []` and `identity_slots_taken: []` for this edition, so it occupies the single
English Wikivoyage slot without overlapping another retained library.

## Measured evidence accepted with this decision

- Runtime contract: `blobs_corpus.runtime_contract.audit_library` returns 0 rejected documents and
  0 rejected passages over the built database, and the same package **installed and answered
  offline on device**: `installable-wv095-002` reached `COMPLETE`/`STOP` with three retrieved
  passages and `model_support=sourced` (first visible text 8,3 s, end to end 81,1 s, emulator,
  airplane mode, debug APK `4c99cf2e50ae624f1e7c8dd5697b798e6da472d7c74e6580cfe3e3f18d237bf5`).
  `grounding` stayed `UNVERIFIED` because that answer carried no citation marker.
- Retrieval: pinned-policy replay found 45 of 48 expected titles, 29 at rank 1, MRR 0.7431. The
  three misses are the carried-over `bali-scooter-permit`, `paris-first-stay`, and
  `tokyo-rail-network`. Running the withdrawn `2026-09.4` package through the same tool over the
  same 48 cases produced identical verdicts with no case changing rank, so the rebuild altered no
  retrieval behaviour. Figures from `package_report.py` are not comparable to the older
  `host_replay.py` summary (47 of 50) and are not quoted against it.

## Accepted limitations carried into the catalog profile

- Snapshot, not live: opening hours, fares, visas, entry rules, and safety advice change; users
  must verify time-sensitive details before travel.
- The two excluded guides are absent from this library; a question that only they answered returns
  no match rather than corrupted text.
- Upstream corruption was removed by exclusion, not by editing guide text; no source character was
  substituted, so every retained guide is byte-identical to the same guide in `2026-09.4`.
- Attribution rests on the pinned archive digest and each guide's revision-history URL because most
  guides carry no per-revision id.
- **No target-device acceptance.** Every timing above is an emulator observation
  (`sdk_gphone64_x86_64`, 4 cores, x86_64); no phone-class device was available, and the product
  owner confirmed on 2026-10-24 that none can be attached, so no phone latency, memory, thermal, or
  GrapheneOS claim is made or implied by this record.
