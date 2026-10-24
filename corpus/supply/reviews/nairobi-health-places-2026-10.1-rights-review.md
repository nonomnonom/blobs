# Rights review record — `nairobi-health-places` edition `2026-10.1`

Status: **decided, 2026-10-24.** Review authority was delegated by the product
owner (`nonomnonom`) to the agent maintaining this corpus. The reviewer is an
automated agent, not a lawyer; this is an engineering rights review recorded
under that delegated authority, and every accepted limitation is carried into
the catalog profile rather than waived silently.

## Source identity

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/nairobi-health-places-2026-10.json` |
| Capture receipt | `corpus/work/places/nairobi-health-places-2026-10.json.capture.json` |
| Snapshot | `corpus/work/places/nairobi-health-places-2026-10.json` (retained locally, not committed) |
| Snapshot bytes | 84,264 |
| Snapshot SHA-256 | `8dcb739f1f1f424c3a8172a4679124cde4dbbf16765d9a306c2b47307bf8264b` |
| Query | `corpus/sources/nairobi-health-places-2026-10.overpassql` |
| Query SHA-256 | `2f532c8dc621bc8a055113529be9df80bf878b81b0007b0944967c45e89cd10c` |
| Endpoint | `https://maps.mail.ru/osm/tools/overpass/api/interpreter` (Overpass JSON, global data) |
| OpenStreetMap base time | `2026-10-24T03:15:54Z` |
| Elements / matching | 203 / 200 |
| Publisher | OpenStreetMap contributors |
| Licence | ODbL 1.0 — `https://www.openstreetmap.org/copyright` |

The query is the reviewed 0.06 × 0.06 degree box around the Nairobi central
business area, restricted to `amenity=pharmacy|hospital|clinic`. The endpoint
choice is an implementation detail of one capture; the lock pins the exact
query and the exact response bytes, so a different endpoint cannot silently
change the edition.

## Record-set identity

The extractor wrote a complete reviewed edition with a manifest that names the
lock digest and records the exclusion report:

| Field | Value |
| --- | --- |
| Records | 200 |
| Excluded | 3 unnamed elements |
| Duplicate bodies | 0 |
| Reviewed records | `corpus/artifacts/records/nairobi-health-places-2026-10/manifest.json` |
| `records_sha256` | `adeee442844debd734b9cc164637d7c7d01ff30158437a58c581168d0ba8c234` |

Each record carries the OpenStreetMap object URL, the snapshot digest and base
time, the query digest, the raw selection indicator (for example
`amenity=pharmacy`), the coordinates, and the ODbL attribution string. No tag
is translated, inferred, or merged.

## Package identity

| Field | Value |
| --- | --- |
| Archive | `corpus/work/packages/nairobi-health-places-2026-10.1.blobs` |
| Archive bytes | 86,458 (well below 6,442,450,944) |
| Archive SHA-256 | `2e99d33710ffb39502da6632ac230a7554e271d4f60dfab4b09258633fb79036` |
| Installed payload bytes | 503,808 |
| Payload SHA-256 | `b4dcb40d70e7a8e88bd3df0e004fa804e5a268739ad03ebbb90605adf7c8438a` |
| Build receipt | `corpus/supply/receipts/nairobi-health-places-2026-10.1.receipt.json` |

## Duplicate-supply proof

`blobs_corpus.portfolio.findings` over every retained reviewed edition reports:

```
identity: ["nairobi", "eng"]
documents: 200
repeated_supply: []
identity_slots_taken: []
```

No retained canonical URL or body hash is repeated, and no other edition claims
the `(nairobi, eng)` slot. This shard is a new regional identity, not a second
copy of an existing place or travel library.

## Conditions a reviewer must accept or reject

1. **Attribution.** ODbL requires visible attribution to OpenStreetMap
   contributors. Every record repeats the licence and attribution string, and
   the catalog profile requires the pack and the in-app reader to show it.
2. **Share-alike for the database.** The extracted records are a derivative
   database. The library must remain available under ODbL, separately from the
   Apache-2.0 application code.
3. **Indication of changes.** Blobs selected 200 named amenities from a larger
   snapshot, excluded three unnamed elements, and rendered a fixed text record
   per place. The manifest records this exclusion and the extractor identity.
4. **Whole-way geometry.** Ways are returned whole by Overpass, so a way whose
   body crosses the box can have its computed centre a few metres outside the
   declared box. Coordinates are copied, never snapped or corrected.
5. **Source tag inconsistency.** Several objects use `amenity` values that do
   not match their name or role (for example a clinic tagged as a hospital).
   Records preserve the raw tag and make no correction.
6. **No inference.** The shard does not claim open-now state, distance, route,
   quality, safety, popularity, or recommendation, and does not rank places.
7. **Staleness.** Place geometry and tags are a snapshot and can be stale,
   incomplete, or inconsistent locally. The catalog limitation must say so.
8. **Coverage.** This is one reviewed box around central Nairobi, not Kenya,
   not East Africa, and not a worldwide place directory.
9. **Names.** Place names are source strings and may be in English, Swahili, or
   a mix. The app-facing record text is English; no name is translated.
10. **Marks.** "OpenStreetMap" is used only as an accurate source description,
    never as an endorsement by the OpenStreetMap Foundation.

## Measured retrieval on the admitted package

`corpus/work/evaluation/results/nairobi-health-places-2026-10.1-retrieval-v1.json`
replays 10 versioned cases against this exact archive with the Android retrieval
policy as committed (`LibraryStore.kt`
`6c44e154b496ebe2f601c74583fd2f3f3224650dff77c60a64eab4acf122354a`,
`RetrievalQuery.kt` `d2beec91da15db229894e0aabdf944678f03e60d1a6b3cdf3e156f37c7296625`).

| Measure | Value |
| --- | --- |
| Cases | 10 |
| Expected titles found | 10 (100%) |
| Found at rank 1 | 10 |
| Mean reciprocal rank | 1.0 |
| Host median | 3.44 ms |
| Host p95 | 4.36 ms |

This measures host document retrieval only. It does not score generated answers
and does not predict Android latency or a target-device acceptance run.

## Review decision

```
Reviewer identity: Qoder agent acting for the product owner (nonomnonom); automated review
Review date: 2026-10-24
Decision on conditions 1-10:
  1. Attribution — ACCEPT. Every record carries the ODbL attribution string and
     the catalog profile requires visible OpenStreetMap attribution.
  2. Share-alike — ACCEPT. The record set stays ODbL and separately licensed
     from the Apache-2.0 app; the catalog profile states the obligation.
  3. Indication of changes — ACCEPT. The manifest records the selection,
     exclusions, extractor identity, and the exact snapshot and query digests.
  4. Whole-way geometry — ACCEPT WITH LIMITATION. Centres can fall a few metres
     outside the declared box; coordinates are copied unchanged and the
     limitation is disclosed.
  5. Tag inconsistency — ACCEPT. Raw tags are preserved; no correction is made
     or implied.
  6. No inference — ACCEPT. Records contain only source tags, coordinates, and
     provenance; no ranking or live-status claim is made.
  7. Staleness — ACCEPT. The profile limitation forbids treating the snapshot
     as current and tells the reader to verify locally.
  8. Coverage — ACCEPT. The profile names the exact Nairobi box and disclaims
     wider coverage.
  9. Names — ACCEPT. Names are source strings; the app-facing text is English
     and no name is translated.
  10. Marks — ACCEPT. OpenStreetMap is named only as the source.
Conditions accepted with limitations recorded in the catalog profile: yes
```
