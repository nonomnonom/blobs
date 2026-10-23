# Data catalog inventory

This is an engineering inventory for the rebuild, not a release claim. It
records which local artifact supports each current catalog profile and which
conditions still block public publication. A package can appear in the bundled
development catalog while still failing the release admission contract in
[architecture.md](architecture.md).

## Evidence classes

| Class | Meaning |
| --- | --- |
| Source lock | A versioned source definition that identifies the source text, edition, rights, and review date. It does not by itself prove the bytes inside a distributed archive. |
| Record inventory | A checked-in normalized-record manifest or source record file that provides a count and, when present, a record-stream digest. |
| Build receipt | A local record of an earlier package build. It is historical unless the exact archive, builder inputs, and digest can be reproduced. |
| Archive proof | An exact `.blobs` archive whose manifest declares the normalized `records_sha256` and whose digest is independently verified. This is required for a new public library release. |

## Current library inventory

| Library | Profile count | Local evidence | Source snapshot | Publication state |
| --- | ---: | --- | --- | --- |
| `wikipedia-starter` | 100 | `android/corpus/starter.documents.jsonl` line count | 2026-10-17 | Development inventory; archive still needs an explicit record-stream digest for a new public edition. |
| `wikipedia-top1m-mini` | 997,761 | `corpus/artifacts/wikipedia-en-top1m-mini-2026-04.receipt.json` build metadata | 2026-10-17 | Historical build receipt records `records_sha256`; verify the distributed archive manifest before republishing. |
| `wikivoyage-en` | 34,597 | `corpus/published-records.json` | 2026-10-19 | Blocked: the local extraction manifest reports 34,599 documents, while the published-record inventory reports 34,597. Rebuild or inspect the exact archive, resolve the count, and publish `records_sha256`. |
| `nps-outdoor-safety` | 5 | `corpus/sources/nps-outdoor-safety-2026-10.json` | 2026-10-19 | Source lock is present; package needs archive proof for a new public edition. |
| `ready-emergency-preparedness` | 4 | `corpus/sources/ready-emergency-preparedness-2026-10.json` | 2026-10-19 | Source lock is present; package needs archive proof for a new public edition. |
| `camping-safety` | 4 | `corpus/sources/camping-safely-2026-10.json` | 2026-10-19 | Source lock is present; package needs archive proof for a new public edition. |
| `backcountry-basics` | 3 | `corpus/sources/backcountry-basics-2026-10-v2.json` | 2026-10-19 | Blocked: the current catalog names edition `2026-10.3`, but the checked-in three-record source lock is `2026-10.2`. Reconcile the edition before archive proof or publication. |
| `weather-safety` | 4 | `corpus/published-records.json` and `corpus/sources/weather-safety-2026-10.json` | 2026-10-19 | Record inventory and source lock are present; verify the archive manifest before republishing. |
| `earthquake-tsunami-safety` | 2 | `corpus/published-records.json` and `corpus/sources/earthquake-tsunami-safety-2026-10.json` | 2026-10-19 | Record inventory and source lock are present; verify the archive manifest before republishing. |
| `emergency-food` | 1 | `corpus/published-records.json` and `corpus/sources/emergency-food-2026-10.json` | 2026-10-19 | Record inventory and source lock are present; verify the archive manifest before republishing. |
| `practical-living` | 4 | `corpus/published-records.json` and `corpus/sources/practical-living-2026-10.json` | 2026-10-20 | Record inventory and source lock are present; verify the archive manifest before republishing. |
## Release decision

Do not publish the current inventory as a new public catalog revision yet. A
release owner must first do all of the following for every included library:

1. Reproduce or independently inspect the exact archive selected for release.
2. Resolve every count discrepancy against its normalized record stream.
3. Ensure the archive manifest declares its `records_sha256`.
4. Verify archive size, archive SHA-256, manifest, provenance, rights, and
   retrieval cases against the exact release inputs.
5. Publish a new immutable asset revision and a strictly newer signed catalog
   revision; never reuse a catalog revision for changed payload bytes.

The release tools are designed to reject a newly staged library archive that
lacks `records_sha256`. That gate is intentionally stronger than this historical
inventory.

## Retired catalog entry

`bali-travel` is no longer offered in the bundled catalog. It was a 30-record
subset of the broader English Wikivoyage library, so installing both did not
create new coverage. Its source lock and published-record entry remain
historical audit evidence; they are not a publication candidate. A future
Indonesia or Bali library needs an independently reviewed source family,
explicit regional coverage, a new immutable source snapshot, and its own
retrieval cases. It must not be recreated by relabelling this subset.

The remaining inventory is also not an approved public supply. The executable
release gate reads `corpus/supply/registry.json` and accepts a library only
when a matching, release-ready admission record exists.
