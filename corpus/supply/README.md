# Knowledge supply registry

This directory owns the portfolio decision before a source becomes a Blobs
library. It is deliberately separate from `sources/`: a source lock identifies
one immutable input, while the registry decides whether a source family should
exist, what it must cover, and whether an older library may be released again.

`registry.json` has three uses:

1. It names the high-quality global source families that the rebuild will
   prepare next.
2. It records whether a legacy catalog package is retired, development-only, or
   blocked pending a new admission.
3. It supplies the release allow-list used by `android/tools/publish_hf_assets.py`.
   The list is empty during the rebuild. A publisher therefore cannot turn an
   old local package into a public package merely by adding it to the Android
   catalog.

## Status meanings

| Status | Meaning |
| --- | --- |
| `source-selection` | The source family is approved for research, but no immutable input is locked. |
| `rights-review` | Candidate source is known; redistribution and attribution are not yet approved. |
| `acquisition-planned` | The source and intended extraction are defined, but the raw snapshot is not retained. |
| `review-planned` | A pinned input is ready for content review, normalization, and exclusions. |
| `release-ready` | All source, record, package, case, rights, and catalog identities are present and agree. Only this status can enter `release_ready`. |
| `development-only` | Historical or local development material; it cannot be published. |
| `retired` | Deliberately excluded from future catalogs. Preserve only the evidence needed to explain that decision. |

## Promoting a family

Do not change a planned family in place into a package. First create a new
versioned source lock under `corpus/sources/`, retain the raw input outside Git,
complete rights review and record review, generate a normalized record digest,
and add versioned retrieval cases. After the archive has a verified digest, add
one `release_ready` entry with the exact package ID, version, source lock,
rights-review record, reviewed-record manifest, case set, archive receipt,
record digest, archive digest, and the exact `library_profile` to show in the
catalog.

For a complementary volume, the same entry must also name its immutable
`partition_plan` and `partition_id`. Publication recomputes the plan and source
lock digests, then compares them with the archive receipt's build metadata. A
package cannot claim to be volume `p000` merely through its package ID.

The retrieval case set is a versioned JSON object rather than an arbitrary
note. It has `format: 1`, a `library` object with the admitted package ID,
version, and `records_sha256`, plus one to 10,000 cases. Each case has a unique
lowercase-hyphenated ID, a bounded question, an `expectation` of `hit` or
`miss`, and `expected_document_ids`. A `hit` must name one or more bounded
document IDs; a `miss` names none. The publisher checks this identity and shape
before it uploads anything.

The publisher compares every library in the Android catalog with that entry.
If any identity is missing or differs, publication stops before it contacts
Hugging Face. It also reads the archive receipt: its library manifest identity,
record digest, and archive digest must agree with the admission. The catalog's
`library_profile` must be an exact match, so a release cannot silently change
the declared coverage, snapshot date, or limitations. This is a release gate,
not a replacement for later Android or retrieval validation.

The `record_count` is also a package invariant. The publisher compares it with
the archive receipt's library-build metadata, and Android compares the same
catalog value with the installed SQLite `build_metadata` record before it
activates the library. This checks the declared count without a second
device-wide document scan during installation.

Every public library archive has a hard 6 GiB (6,442,450,944-byte) payload
limit. Large source editions must be published as deterministic, complementary
volumes. Raw source snapshots and workstation staging artifacts are not
Android packages and do not count as libraries.
