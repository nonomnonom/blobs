# Blobs knowledge-supply portfolio

**Working strategy, 2026-10-24.** This is a product and evidence plan, not a
rights opinion, release admission, bounty result, or claim that the current
catalog is complete.

## Product position

Blobs should be the Android-first offline research app that combines useful
local reference libraries with one local retrieval-and-completion path and
passage-level citations. The durable advantage is not the largest download or
the number of packs: it is a coherent set of broad, complementary, reproducible
libraries whose exact editions can be installed, searched, cited, and updated
without silently replacing user data.

The repository's bounty goal does not identify a public bounty name, judging
rubric, submission deadline, or competitor list. Do not claim that a supply
choice wins a bounty until those rules are supplied and mapped to reproducible
acceptance evidence.

## Competitor evidence and implications

This comparison uses public project documentation inspected on 2026-10-24.
It compares product models, not product quality scores.

| Product | Observed supply or research model | Implication for Blobs |
| --- | --- | --- |
| [Kiwix Android](https://github.com/kiwix/kiwix-android) and [Kiwix ZIM catalog](https://download.kiwix.org/zim/wikipedia/) | Offline reading of dated ZIM editions; the live catalog contains content at substantially different sizes, and Android documents storage constraints. | Make edition, language, actual download and installed sizes, storage impact, and update choice explicit. Keep Blobs' 6 GiB limit per library and 48 GB aggregate safeguard distinct. |
| [Zim Pro](https://github.com/magicstack-llp/zim-pro) | Uses a user-provided folder of ZIM books for local search and cited AI; its README describes local models and optional hosted providers. | Blobs can differentiate with first-party Android package install/verification, durable turn evidence, and no remote research or answer path after installation. Do not claim source coverage that has not been admitted. |
| [Internet-in-a-Box](https://github.com/iiab/iiab) | Curates a broad offline learning/library system with operator-selected multi-gigabyte content packs and removable-media workflows. | Borrow the principle of user-selected content rather than forcing a maximum install. Blobs remains a focused mobile research product, not a school-server or map-delivery bundle. |
| [Zeal](https://github.com/zealdocs/zeal) | Users select searchable offline documentation docsets. | Offer optional, purpose-specific packs only when they have a distinct audience and verified source rights; avoid shipping every source by default. |
| [DevDocs](https://github.com/freeCodeCamp/devdocs) | Combines versioned third-party API documentation, selectable downloads, and a documented monthly image build cadence. | Version and date knowledge explicitly. A source's public availability or the app's own licence is not a content redistribution grant. |

These sources do not establish a blanket content licence or Blobs' 6 GiB
complementary-volume scheme. No competitor performance, answer-quality, or
bounty comparison was measured.

## Portfolio, in priority order

| Lane | Candidate and role | Include when | Do not claim |
| --- | --- | --- | --- |
| Broad reference | Wikipedia Top 1M mini: broad English lead coverage | Rebuilt from the retained reviewed records; actual payload below 6 GiB; fixed retrieval cases pass an agreed threshold; rights and attribution reviewed. This is the sole planned Wikipedia reference install. The five-volume Top 1M full-text successor was cancelled by the owner on 2026-10-24 because its 17,134,155,148-byte archive and payload were too large for the product. | Full articles, every Wikipedia topic, live facts, or all languages. |
| Travel | Complete English Wikivoyage snapshot | Corrected package identity, measured retrieval, clear source date and volatile-information warnings, and source-specific rights review. | Current visa rules, prices, opening hours, safety, transport schedules, or complete global coverage in every language. |
| Domain references | Jurisdiction-specific official guidance (health, emergencies, law, public services) | Each publisher and document has explicit redistribution terms, jurisdiction/date metadata, review, and narrowly scoped cases. | One worldwide authority or current professional/emergency advice. |
| Structured snapshots | Bounded Wikidata or OpenStreetMap regional releases | Exact source snapshot/query, licence obligations, reviewer sign-off, field provenance, and explicit unsupported-claim limits are recorded. | Live, complete, nearby, open-now, route, popularity, safety, or recommendation claims. |
| User-owned | User-imported local sources | Import validation, local-only ownership, and visible unverified provenance remain intact. | Catalog-reviewed or trusted source status. |

Reviewed full-library records and completed rights reviews now exist for six
lanes and nine editions: Top 1M, Wikivoyage, practical how-to, repair and
fixing, the four-volume Wikisource history and primary-texts set, and the first
OpenStreetMap places shard. The
retired Top 50K records edition was removed on 2026-10-24 after measurement showed
49,970 of its 49,999 canonical article URLs already exist in the Top 1M edition; its
source lock and pinned smoke report remain as the explanation. Domain and
structured-data packs are future strategy lanes, not permission to generate
unreviewed packs. A narrow, official-source pack is valuable only after its reuse
rights are known; the existing small safety collections are not automatically a
portfolio.

## Admitted local evidence

Nine editions have completed rights reviews, built packages and `release_ready`
admissions in `registry.json`. Each admission was checked against its immutable
records, partition plan and package digests. This is host-side evidence only; no
target-device acceptance or signed catalog revision exists yet.

| Admitted library | Edition | Documents / passages | SQLite payload | `.blobs` archive | Host retrieval replay |
| --- | --- | ---: | ---: | ---: | --- |
| Wikipedia Top 1M mini | `2026-04.5` | 997,714 / 2,126,742 | 4,980,006,912 bytes | 1,787,025,308 bytes | 7/12 (58.3%), all at rank 1, MRR 0.583 (measured on `2026-04.4`) |
| Wikivoyage English | `2026-09.5` | 34,597 / 520,143 | 622,776,320 bytes | 290,811,362 bytes | 47/50 (94.0%), 30 rank-1, MRR 0.743 (measured on the same records) |
| Practical how-to | `2026-10.4` | 189,294 / 672,866 | 1,454,772,224 bytes | 567,686,098 bytes | 30/36 (83.3%), 23 rank-1, MRR 0.703 (measured on `2026-10.3`) |
| Repair and fixing | `2026-10.4` | 78,626 / 300,571 | 647,237,632 bytes | 256,576,414 bytes | 44/48 (91.7%), 36 rank-1, MRR 0.812 (measured on `2026-10.3`) |
| Wikisource history, vol. 1 | `2026-09.3` | 155,527 / 1,430,232 | 3,127,779,328 bytes | 1,323,097,330 bytes | 9/10 (90.0%), 7 rank-1, MRR 0.783 (measured on `2026-09.2`) |
| Wikisource history, vol. 2 | `2026-09.3` | 155,739 / 1,425,914 | 3,123,478,528 bytes | 1,322,088,523 bytes | 9/10 (90.0%), 7 rank-1, MRR 0.783 (measured on `2026-09.2`) |
| Wikisource history, vol. 3 | `2026-09.3` | 155,378 / 1,438,450 | 3,150,995,456 bytes | 1,335,443,794 bytes | 8/10 (80.0%), 6 rank-1, MRR 0.645 (measured on `2026-09.2`) |
| Wikisource history, vol. 4 | `2026-09.3` | 155,370 / 1,431,552 | 3,126,341,632 bytes | 1,323,294,448 bytes | 9/10 (90.0%), 7 rank-1, MRR 0.783 (measured on `2026-09.2`) |
| Nairobi health places | `2026-10.1` | 200 / 200 | 503,808 bytes | 86,458 bytes | 10/10 (100%), 10 rank-1, MRR 1.0, 3.4 ms median |

Combined SQLite payload is 20,233,891,840 bytes, below the separate
48,000,000,000-byte aggregate safeguard; each payload is below the 6 GiB
per-library limit. The successor editions differ from the replayed predecessors
only by rejecting documents whose bodies carried upstream U+FFFD, so the replay
figures above are inherited from that measurement until each successor is
replayed. An exact cross-library audit of the original four editions
found zero shared canonical URLs and zero shared normalized body hashes
(`corpus/work/evaluation/results/cross-library-duplicates-2026-10-24.json`), and
the 622,149-document Wikisource edition passed the same portfolio gate against
every retained records set with empty `repeated_supply` and
`identity_slots_taken` lists. The Wikipedia source itself has exact-body groups
across distinct canonical URLs; those stay as distinct source articles, not
duplicated library editions.

The Wikipedia replay is the weakest admitted result: five expected articles
exist in the package but rank below the app's 24-candidate depth. The admission
records that as a ranking and candidate-depth limitation, not a content gap, so
the reference lane still needs a better route or a denser top slice before it
can be called dependable. Every lane still needs target-device acceptance and a
signed catalog revision before users can install it.

## Global supply tranches, measured 2026-10-24

The installed payload, not the download, consumes the 6 GiB per-library limit.
Archive bytes do not predict payload: measured archive-to-payload runs from 0.31
for the 3.9 GB Wikisource archive to 2.48 for the money site. Size every lane
from normalized text bytes instead, where the measured factor is 1.95 to 2.70,
then confirm on the built edition.

| Lane | Edition and measured size | State |
| --- | --- | --- |
| Reference, English | `wikipedia-top1m-mini` 2026-04.5: 1,787,025,308-byte archive, 4,980,006,912-byte payload, 997,714 documents | admitted; 7 of 12 replay recall is the lane's measured limitation |
| Travel, English | `wikivoyage-en-all-nopic` 2026-09.5: 290,811,362-byte archive, 622,776,320-byte payload, 34,597 guides | admitted; 47 of 50 on the corrected v4 suite |
| How-to, practical | `howto-practical` 2026-10.4: cooking, money, travel, and home-improvement ZIMs merged into one edition, 189,294 questions, 672,866 passages, 1,454,772,224-byte payload | admitted; question pages only, 30 of 36 replay recall |
| Repair, English | `howto-repair` 2026-10.4: mechanics, bicycles, aviation, 3D printing, and woodworking merged, 78,626 questions, 300,571 passages, 647,237,632-byte payload (10.0% of one volume) | admitted; question pages only, 44 of 48 replay recall; robotics stays out as programming tooling |
| History and primary texts | `wikisource-en-all-nopic` 2026-09.3: 5,303,924,095 bytes of archives, 622,014 reviewed documents, four deterministic volumes of 155,370-155,739 documents and 3,123,478,528-3,150,995,456-byte payload each | admitted; non-text pages excluded, 8-9 of 10 replay recall per volume on `2026-09.2`, rights review decided |
| Dictionary | Wiktionary nopic archives: English 8.5 GB, French 4.6 GB, Russian 2.0 GB, German 1.5 GB, Portuguese 0.34 GB | English needs deterministic volumes; smaller languages fit one |
| Places | `nairobi-health-places` 2026-10.1: one reviewed Overpass snapshot of 200 named clinics, hospitals, and pharmacies in a 0.06 x 0.06 degree box; 503,808-byte payload, 86,458-byte archive | admitted; 3 unnamed elements excluded, 10 of 10 replay cases at rank 1; one shard is not a worldwide directory |
| Public guidance | `web_pages` caps a lock at 40 sources and 5,000,000 bytes each | a real health or civics lane needs archive-scale acquisition first |

`stackoverflow.com` is 107 GB and `wikipedia_en_all_nopic` is 52,690,706,555
bytes: workstation inputs, not libraries. The Top 1M full-text successor was
cancelled by the owner on 2026-10-24 because its 17,134,155,148-byte archive and
five-volume payload were too large for the product; its capacity plan is retained
only as the record of that decision. The full all-articles archive would need
about 18 volumes and stays out of scope on this machine.

The library set is only half of an offline research answer. Before the product can be
described as answering across this portfolio, the runtime needs the matching work: a
bounded per-question library route instead of one global scan, stemming and per-locale
stop-word and non-article section data instead of the English lists now hard-coded in
`RetrievalQuery.kt` and `LibraryStore.kt`, a tokenizer that can serve scripts without
spaces, and a model measured in each supplied language. Host retrieval on the
622 MB travel library already measures 334.9 ms median and 2,140.6 ms p95, so fan-out
across several libraries is a design decision, not a free default.

## Curation and install policy

- Keep exactly one English Wikipedia reference package in the installable
  portfolio. The Top 50K full-text package is retired as a separate download
  because its topical universe overlaps the broad Top 1M source, measured at 99.9%
  shared canonical URLs. Its records edition is deleted rather than kept as a
  diagnostic copy: a retired identity that cannot be reproduced from a package or
  receipt should not cost a clone one gigabyte. A future full-text English reference
  replaces the single reference identity instead of accompanying it.
- Wikivoyage remains a separate travel source: it has a distinct guide corpus,
  purpose, provenance, and limitations. Overlapping place names are not
  duplicate documents when the sources contain distinct works. Before
  publication, compare canonical source URLs and normalized body hashes across
  every pair of proposed libraries; report exact duplicates and resolve them
  rather than silently counting them as extra coverage.
- Avoid separate tiny packs that duplicate the same general topics. Retire
  them from active recommendations and future catalogs, but retain source
  locks, receipts, and reviewed records needed to explain historical releases
  or reproduce prior results.
- The current bundled catalog contains the 100-record Wikipedia starter plus
  seven fragmented U.S. safety/preparedness packs and a four-record
  practical-living pack. The registry now retires those as separate portfolio
  entries; a future U.S. preparedness collection would need a single reviewed,
  coherent new edition. Do not hand-edit the signed catalog or publish a
  replacement until the two active candidates pass admission and a matching
  catalog revision can be signed.
- State plainly that every library is English. Do not imply that one English
  library is multilingual, region-neutral, or globally comprehensive.
- The owner fixed the supply language on 2026-10-24: Blobs publishes English
  knowledge only. Do not prepare a per-language or per-region edition of one
  project, and do not open a locale track. Portfolio growth is by subject
  category, and the exporter refuses any edition whose archive language is not
  English, so this is an engine rule rather than a preference in a document.
- Show archive bytes and installed payload bytes separately, plus edition,
  language, scope, snapshot date, publisher, licence/attribution, update
  policy, known limitations, and resulting aggregate storage.
- Never let an edition update overwrite a good installed edition. An update is
  a new immutable identity with a reversible activation path.
- Stay below 6 GiB for every downloadable library payload. Larger raw sources
  are workstation-only. If a source needs volumes, derive deterministic,
  complementary partitions from the complete reviewed source manifest and
  verify cross-volume coverage. Keep the separate 48 GB limit for aggregate
  installed assets.
- Do not include map tiles, media-heavy archives, remote search, live data, or
  app-specific mirrors merely to inflate catalog scale.

## Acquisition stop conditions

Measured staging cost, from the editions on this machine: normalized record shards
are 0.27 to 0.67 times the archive (Top 1M 0.27, Wikivoyage 0.51, Wikisource 0.67),
a review step writes a second copy of that size, export hardlinks the
shards, and packaging needs one installed payload plus its archive per volume while
earlier volumes stay on disk.

Installed payload predicts from text, not from the archive: the merged cooking-plus-
money lane installed 1,048,535,040 bytes over 388,625,311 bytes of normalized text,
which is 2.698 times, while Wikisource volume 1 installed 3,131,916,288 bytes over
roughly 1.6 GB of text, about 1.95 times. Size every lane from measured text bytes
of its extracted editions and use 1.95 to 3.27 as the planning range.

The full staging math and reclamation inventory are in
[the capacity plan](plans/capacity-plan-2026-10-24.md); the top-1M full-text gate
described there was cancelled by the owner on 2026-10-24, so it is decision
evidence rather than a plan to execute.

| Lane | Archive | Peak staging estimate | Verdict at 32.71 GiB free (2026-10-24) |
| --- | ---: | ---: | --- |
| Big reference, top-1M full text | 15.96 GiB | measured: 25.6-38.3 GiB to extract and curate one volume | Cancelled by the owner on 2026-10-24: the archive and five-volume payload are too large for this product. No acquisition. |
| History and primary texts, Wikisource | 3.64 GiB | built: extract 2.51, review 2.42, four payloads 11.68, four archives 4.95 | Complete and admitted as four volumes; measured peak 25.20 GiB against a 24.5 GB estimate (+2.4%). |
| Practical how-to lane | 2.83 GB | built: payload 1.45 GB, archive 0.57 GB | Admitted as one volume. |
| Repair lane | 1.70 GB | built: payload 0.65 GB, archive 0.26 GB | Admitted as one volume. |
| Home and diy | 2.09 GB | 2.09 + 1.2 + 1.2 + 5.2 + 1.4 = 11.1 GB | Not admitted: the diy site already ships inside the practical lane, so a separate lane would repeat supply. |

Never delete a good local edition to make room. A lane that does not fit waits for a
second staging volume or a smaller selection, and every multi-volume family still needs
cross-volume retrieval cases before it can enter `release_ready`. The full English
Wikipedia archive (49 GB, 18 volumes) stays out of scope on this machine.

## Evidence gates before release

For each edition, preserve the exact source lock and input revision/digest,
rights decision, normalization and exclusion policy, reviewed-record manifest,
per-record attribution/provenance, `records_sha256`, deterministic build
receipt, package manifest/archive digest, exact catalog `library_profile`,
and a versioned retrieval suite bound to that package.

Retrieval evaluation must include broad natural-language questions, exact and
ambiguous title cases, cross-topic questions, known misses, and cases that
exercise scope and passage citations. Review source identity/recall as well as
answer claims against the retrieved passage. Keep the held-out set separate
from iteration cases. A host replay is not a phone latency or answer-quality
claim; release acceptance also needs the exact APK/model/device/offline run.

Only a completed, reviewed entry in `registry.json`'s `release_ready` list can
be published. Do not fill that list with placeholders, infer legal approval,
or use a successful package build as a substitute for retrieval and device
evidence.

## Current local state

The [registry](registry.json) tracks local candidates and current dispositions.
As of this strategy revision:

- Nine schema-3 packages have completed rights reviews and `release_ready`
  admissions: Wikipedia Top 1M mini `2026-04.4`, Wikivoyage `2026-09.4`,
  practical how-to `2026-10.3`, repair and fixing `2026-10.3`, the four
  Wikisource volumes `2026-09.2`, and Nairobi health places `2026-10.1`. Each
  admission binds the source lock, reviewed records, `records_sha256`, archive
  receipt, archive digest, retrieval case set and catalog profile, and
  `require_release_admissions` passes against the real receipts.
- Top 50K full text remains retired: it found 7 of 12 expected titles before
  corrections, and its records overlap the Top 1M edition. Its package is gone;
  the smoke report stays as historical diagnostic evidence.
- The corrected replay policy lowered every earlier number. Wikipedia now
  reports 7 of 12 (58.3%), Wikivoyage 47 of 50 (94.0%), practical how-to 30 of
  36 (83.3%), and repair 44 of 48 (91.7%). The earlier higher figures came from
  a harness that promoted incidental ranked titles and ran an extra query.
- Wikisource `2026-09.2` is complete: 622,149 reviewed articles, 6,433,764,727
  text bytes, and four partition volumes p000-p003, each with its own receipt,
  retrieval cases, rights review, and `release_ready` admission.
- The places lane has one admitted shard: 200 named Nairobi health places from a
  reviewed Overpass snapshot, packaged at 503,808 payload bytes, 10 of 10 host
  replay cases at rank 1. It proves the pipeline; it is not global coverage.
- The big reference is cancelled, not pending. The owner withdrew the top-1M
  full-text lane on 2026-10-24 because the real edition
  `wikipedia_en_top1m_nopic_2026-04.zim` (17,134,155,148 bytes, SHA-256
  `f0ad000b...82662`) and its five-volume payload are too large for the product.
  The capacity plan is retained only as decision evidence; do not acquire the
  archive or resume the lane unless the owner reopens it. The admitted Top 1M
  mini remains the sole Wikipedia reference install.
- No HF upload, no signed catalog revision, and no phone installation evidence
  exist yet. The catalog signing key is not on this machine, so publication
  stays owner-gated even after the local evidence is complete.
