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
| Broad reference | Wikipedia Top 1M mini: broad English lead coverage | Rebuilt from the retained reviewed records; actual payload below 6 GiB; fixed retrieval cases pass an agreed threshold; rights and attribution reviewed. This is the sole planned Wikipedia reference install. | Full articles, every Wikipedia topic, live facts, or all languages. |
| Travel | Complete English Wikivoyage snapshot | Corrected package identity, measured retrieval, clear source date and volatile-information warnings, and source-specific rights review. | Current visa rules, prices, opening hours, safety, transport schedules, or complete global coverage in every language. |
| Domain references | Jurisdiction-specific official guidance (health, emergencies, law, public services) | Each publisher and document has explicit redistribution terms, jurisdiction/date metadata, review, and narrowly scoped cases. | One worldwide authority or current professional/emergency advice. |
| Structured snapshots | Bounded Wikidata or OpenStreetMap regional releases | Exact source snapshot/query, licence obligations, reviewer sign-off, field provenance, and explicit unsupported-claim limits are recorded. | Live, complete, nearby, open-now, route, popularity, safety, or recommendation claims. |
| User-owned | User-imported local sources | Import validation, local-only ownership, and visible unverified provenance remain intact. | Catalog-reviewed or trusted source status. |

Reviewed full-library records currently exist for the Top 1M and Wikivoyage. The
retired Top 50K records edition was removed on 2026-10-24 after measurement showed
49,970 of its 49,999 canonical article URLs already exist in the Top 1M edition; its
source lock and pinned smoke report remain as the explanation. Only Top 1M and
Wikivoyage remain active package candidates. Domain and structured-data packs are
future strategy
lanes, not permission to generate unreviewed packs. A narrow, official-source
pack is valuable only after its reuse rights are known; the existing small
safety collections are not automatically a portfolio.

## Local candidate evidence

The two current package candidates have been built and checked against their
immutable package and records digests. This is host-side evidence only; it is
not release admission or Android validation.

| Candidate | Documents / passages | SQLite payload | `.blobs` archive | Host retrieval smoke |
| --- | ---: | ---: | ---: | --- |
| Wikipedia Top 1M mini `2026-04.2` | 997,761 / 2,126,824 | 4,980,555,776 bytes | 1,786,362,860 bytes | 10/12 expected titles; gaps for Photosynthesis and Inflation |
| Wikivoyage English `2026-09.4` | 34,599 / 520,170 | 622,645,248 bytes | 290,642,424 bytes | 6/6 expected titles |

Combined SQLite payload is 5,603,201,024 bytes, below the separate
48,000,000,000-byte aggregate safeguard; each payload is below the 6 GiB
per-library limit. An exact cross-library audit found zero shared normalized
body hashes and zero shared canonical URLs. The Wikipedia source itself has
24 exact-body groups (43 excess records) across distinct canonical URLs; these
are retained as distinct source articles, not duplicated library editions.
Wikivoyage has no exact duplicate bodies in its own package. The cross-library
and Wikipedia content audits are recorded in `corpus/work/evaluation/results/`.

Do not mark either candidate release-ready: the Top 1M retrieval misses remain
unresolved, neither candidate has target-device acceptance, and a package
digest does not replace the required source-specific rights review.

## Global supply tranches, measured 2026-10-24

The installed payload, not the download, consumes the 6 GiB per-library limit. Two
measured ratios bound every plan: payload is 2.0 to 2.1 times normalized text bytes,
and 1.65 to 3.0 times the source archive bytes. Budget at most about 2.2 GiB of source
archive per volume, then confirm on the built edition.

| Lane | Edition and measured size | State |
| --- | --- | --- |
| Reference, English | `wikipedia-en-top1m-mini` 3,022,411,034-byte archive, 4,980,555,776-byte payload, 997,761 documents | one candidate, 10 of 12 recall unresolved |
| Travel, English | `wikivoyage-en-all-nopic` 272,495,142-byte archive, 622,645,248-byte payload, 34,599 guides | candidate, 50 of 50 on the new suite |
| How-to, practical | `howto-practical` 2026-10.2: cooking, money, travel, and home-improvement sites, 3,040,325,901 archive bytes, 213,217 questions, 850,638,763 normalized text bytes, about 2.3 GiB of installed payload projected at 2.70x | review keeps question pages only; the first draft shipped 212,090 member profiles, tag directories, and paged listings, which doubled the lane with pages that are not answers. Each site is its own ZIM, so a lane merges verified per-archive editions into one reviewed stream; a pack per site would repeat the retired-fragment mistake |
| Repair, English | `howto-repair` 2026-10.2: mechanics, bicycles, aviation, 3D printing, and woodworking, 88,383 questions, 390,422,824 normalized text bytes, 924,274,688 installed payload (14.3% of one volume) | measured package; robotics was dropped because its questions are mostly ROS and firmware tooling, which is programming documentation. Rights review and retrieval suite are drafted but unmeasured |
| Dictionary | Wiktionary nopic archives: English 8.5 GB, French 4.6 GB, Russian 2.0 GB, German 1.5 GB, Portuguese 0.34 GB | English needs deterministic volumes; smaller languages fit one |
| Places | Overpass snapshots bounded to 5,000,000 bytes and 1,000 elements per reviewed lock | pipeline exists, no reviewed shard yet |
| Public guidance | `web_pages` caps a lock at 40 sources and 5,000,000 bytes each | a real health or civics lane needs archive-scale acquisition first |

`stackoverflow.com` is 107 GB and `wikipedia_en_all_nopic` is 48 GB: workstation
inputs, not libraries, and both stay out of the portfolio until capacity and volume
plans exist.

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
are 0.28 to 0.51 times the archive (Wikivoyage 0.51, Top 1M 0.28, Stack Exchange
cooking 0.31), a review step writes a second copy of that size, export hardlinks the
shards, and packaging needs one installed payload plus its archive per volume while
earlier volumes stay on disk.

Installed payload predicts from text, not from the archive: the merged cooking-plus-
money lane installed 1,048,535,040 bytes over 388,625,311 bytes of normalized text,
which is 2.698 times. Archive-to-payload swings between 1.76 and 2.48 across two
sites of the same project, so size every lane from measured text bytes of its
extracted editions and treat 2.70 as the planning factor.

| Lane | Archive | Peak staging estimate | Verdict at 59.5 GB free |
| --- | ---: | ---: | --- |
| Big reference, top-1M full text | 16 GB | 16 + 8.8 + 8.8 + 6.0 + 2.2 + 11.0 (five prior volume archives) = 52.8 GB | Do not start while the reference rebuild holds about 12 GB of intermediates. Re-measure after it finishes, or give `--work-directory` a second volume. |
| History and primary texts, Wikisource | 11 GB | 11 + 6.1 + 6.1 + 6.0 + 2.2 + 4.4 = 35.8 GB | Possible alone with at least 40 GB free. Not before the rebuild completes. |
| Practical how-to lane | 2.83 GB | 2.83 + 1.6 + 1.6 + 7.1 + 2.0 = 15.1 GB | Clear now, but the installed payload would pass 6 GiB, so this selection ships as complementary volumes or as a narrower first edition. |
| Repair lane | 1.70 GB | 1.70 + 0.95 + 0.95 + 4.25 + 1.2 = 9.1 GB | Clear now and fits one volume at the 2.5 planning bound. |
| Home and diy | 2.09 GB | 2.09 + 1.2 + 1.2 + 5.2 + 1.4 = 11.1 GB | Clear now; one volume only as its own lane, not added to the repair lane. |

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

- Top 50K full text was built and archive-verified locally, but its pinned
  host retrieval smoke found only 7 of 12 expected titles (58.3%). It is
  retired as a separate distributable library, both because retrieval is weak
  and because the selected Top 1M candidate avoids a second overlapping
  Wikipedia download. Its generated package and receipt have been removed;
  the smoke report is retained as historical diagnostic evidence, not a
  reproducible baseline.
- Top 1M schema-3 package (`wikipedia-top1m-mini-2026-04.2.blobs`, 1.78 GB,
  997,761 documents) was built and verified locally. Pinned host retrieval
  smoke recall was 10 of 12 expected titles (83.3%), with 8 at rank 1 and
  541.6ms median host latency.
- Wikivoyage schema-3 package (`wikivoyage-en-2026-09.4.blobs`, 290 MB,
  34,599 guides) was built and verified locally. Pinned host retrieval
  smoke recall was 6 of 6 expected titles (100%) at rank 1 with 184.0ms
  median host latency.
- There are no signed-off per-pack rights reviews, no release admissions, no
  phone installation evidence for these candidate editions, and no HF upload
  in this work.
