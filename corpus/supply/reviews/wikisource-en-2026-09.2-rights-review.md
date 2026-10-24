# Rights review record — `wikisource-en` edition `2026-09.2` (four volumes)

Status: **decided, 2026-10-24.** Review authority was delegated by the product
owner (`nonomnonom`) to the agent maintaining this corpus. The reviewer is an
automated agent, not a human rights lawyer; the decision below is an engineering
rights review recorded under that delegated authority, and every accepted
limitation is carried into the catalog profile rather than waived silently.

This review covers one source edition published as four complementary library
volumes. It decides all four admissions together because they share one source
lock, one reviewed records manifest, one curation policy and one partition plan.

## Source identity

| Field | Value |
| --- | --- |
| Source lock | `corpus/sources/wikisource-en-all-nopic-2026-09.json` |
| Source file | `wikisource_en_all_nopic_2026-09.zim` |
| Source bytes | 3,905,588,830 |
| Source SHA-256 | `6180cd199142862fb0d276861ccc5b48a40f7bae427b8427b2b6a573796e2f98` |
| Publisher (archive) | openZIM, scraper mwoffliner 2.0.0, flavour `nopic`, language `eng` |
| Underlying work | `en.wikisource.org` transcription text, snapshot `2026-09-08` |
| Declared licence | Per work: public domain, or CC BY-SA 4.0 for contributions, translations and annotations; Wikisource copyright policy at `https://en.wikisource.org/wiki/Wikisource:Copyright_policy` |
| Retained raw snapshot | `corpus/work/sources/wikisource_en_all_nopic_2026-09.zim` (retained locally, not published) |
| Source lock identity SHA-256 | `4231ed3b6c4e38d4a236640c7d42ed332f6a1fd505785c21d4070318198db440` |

The raw snapshot was retained before extraction and its digest re-verified
against the lock on 2026-10-24. The lock checksum was taken from the publisher's
signed `.meta4` descriptor, not from a mirror listing.

## What this edition contains

The complete English Wikisource `nopic` snapshot, normalized into one document
per transcluded page, then split deterministically into four volumes:

| Measure | Value |
| --- | --- |
| Reviewed documents | 622,149 |
| Text characters | 6,371,461,443 |
| Text UTF-8 bytes | 6,433,764,727 |
| Long documents over 100,000 characters | 6,208 |
| Documents with headings | 67,256 |
| Retained redirect aliases | 150,671 plus 6,223 HTML redirects |
| Rejected records | 11,705 non-article assets and malformed pages |
| Reviewed record shards | 522 files, 2,671,671,140 bytes |
| Reviewed manifest | `corpus/artifacts/records/wikisource-en-all-nopic-2026-09.2/manifest.json` |

The raw archive has 688,937 articles. Curation policy `Kiwix article-only
review v3` (`policy_sha256` `6c5e1897294e8d586c9a703f816385e51dbbc399bcd393ce0963f4ec3e3f6766`)
removed 66,788 records because they are not primary texts:

| Exclusion | Records |
| --- | --- |
| Author metadata pages | 47,277 |
| Version index pages ("Versions of … include:") | 7,369 |
| Disambiguation pages ("… may refer to:") | 6,024 |
| Subject portal navigation | 4,702 |
| Exact duplicate body text of an earlier record | 1,414 |
| Archive navigation page | 1 |
| Bundled support asset | 1 |

Every exclusion is listed by ID, title, entry path and reason in the reviewed
manifest's `curation.excluded_documents` array.

## Revision and attribution census

| Measure | Value |
| --- | --- |
| Documents with a MediaWiki `revision_id` and revision URL | 619,252 (99.5%) |
| Documents without a revision id | 2,897 |
| Documents with a `history_url` | 622,149 (100%) |
| Bodies containing the Unicode replacement character (U+FFFD) | 135 |

The revision census is recorded in `corpus/work/wikisource-v3-census.log`. A
spot check of the ZIM entry `2024_Elkhorn_tornado` found exactly three `EF BF
BD` byte sequences in the upstream archive, so the replacement characters are an
upstream extraction defect, not an artifact of this pipeline.

## Package identity

Partition plan: `corpus/supply/plans/wikisource-en-all-nopic-2026-09.2.json`,
SHA-256 `9dcc61644abba18caa28b2617d4d8d0b4155bf74fca88c616edcb445fe916a40`,
algorithm `sha256-document-id-modulo-v1`. The split is deterministic and not
topical: installing one volume gives a random quarter of the edition, and only
the four volumes together give the complete reviewed set.

| Volume | Documents | Passages | Installed payload bytes | Archive bytes | Payload SHA-256 | Archive SHA-256 | `records_sha256` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `wikisource-en-history-p000` | 155,569 | 1,432,332 | 3,131,916,288 | 1,326,111,432 | `db592c9e0d80c595f97abf73bf8cd5cc9ca2eeccf89ccda5c6ebe018aaaa3efb` | `23fc7e31eb8e9bd26041ee87a00bc795a2dc66c9a8cf9455c9776adf55ff8105` | `5f062b528ef257abc496e59f64fd22fb0d09ffe4803132313fda609fd30d859d` |
| `wikisource-en-history-p001` | 155,765 | 1,427,120 | 3,125,862,400 | 1,323,811,991 | `9c5a9f8b133b188d001f8c89e566547513496accc6bb1ec7fb3c33facf393453` | `a654d6d2dc93d4d586544b2b88b0cd38f35fd4908de21a7545404520b3009833` | `eb1edc2e4c155350387e83d6cced0cf6c7d38890aa56527052d3ba28daafc3b2` |
| `wikisource-en-history-p002` | 155,410 | 1,440,217 | 3,154,440,192 | 1,336,109,643 | `0f7c97b2e0023ae524c6007945085ceae5f5440439ce22facba230cfcc3ef202` | `186e1fcf796153bb1d317bb25d36133a697d36b26820abe78f695fbb8bc79642` | `e23c4cfb4db1517be8582aafc7b4cebbcec07c2d1237ad09f0a5e1ba43abf83c` |
| `wikisource-en-history-p003` | 155,405 | 1,433,801 | 3,130,884,096 | 1,325,391,677 | `3216ee1df01ec53aca871a40bea11f9cb6fad855d74563ad88f722d505e82a0c` | `a130b0142890ebe58519f1646678473337cbc394744aa37a6922a405900275f8` | `0d50e297c423e58d0fd6e0dabe0138edf8bc66322e82b3afe8d495caa96ec2e8` |

Each receipt is written beside its archive in `corpus/work/packages/`. Every
volume reports `exact_duplicate_bodies: 0`, and each receipt carries the same
`source_manifest_sha256` `ca1c7c65fd8eea966c8137d544876d8ab766d04a50a7183e5b6738bda532a8fc`
and the same `source_lock_sha256`.

## Capacity

| Measure | Value |
| --- | --- |
| Largest volume payload | 3,154,440,192 bytes (48.96% of the 6,442,450,944-byte package limit) |
| Four-volume installed payload | 12,543,102,976 bytes |
| Existing admitted portfolio payload | 7,704,588,288 bytes |
| Portfolio after these four admissions | 20,247,691,264 bytes (42.2% of the 48,000,000,000-byte aggregate safeguard) |

## Duplicate-supply proof

The portfolio gate (`python -m blobs_corpus.portfolio
artifacts/records/wikisource-en-all-nopic-2026-09.2/manifest.json`) returned:

```
{
  "identity": ["wikisource", "eng"],
  "documents": 622149,
  "repeated_supply": [],
  "identity_slots_taken": []
}
```

No retained canonical URL or body hash is repeated across the admitted
portfolio, and no other admitted edition occupies the (`wikisource`, `eng`)
identity slot. The 1,414 duplicate bodies inside the source edition were removed
before export, and every package receipt reports zero exact duplicate bodies.
The superseded `2026-09.1` editions were retired to `corpus/retired/` before
this edition was exported, so no duplicate supply remains installable.

## Measured retrieval on the admitted packages

Each volume was measured independently with its own 10-case set on the exact
packaged database, using the committed Android retrieval policy
(`LibraryStore.kt` `6c44e154b496ebe2f601c74583fd2f3f3224650dff77c60a64eab4acf122354a`,
`RetrievalQuery.kt` `d2beec91da15db229894e0aabdf944678f03e60d1a6b3cdf3e156f37c7296625`).
Reports are in `corpus/work/evaluation/results/wikisource-en-history-p00N-2026-09.2-retrieval-v2.json`.

| Volume | Cases | Expected found | Rank 1 | MRR | Median host ms | p95 host ms |
| --- | --- | --- | --- | --- | --- | --- |
| p000 | 10 | 9 (90%) | 7 | 0.783 | 490.0 | 1,191.0 |
| p001 | 10 | 9 (90%) | 7 | 0.783 | 478.8 | 1,268.0 |
| p002 | 10 | 8 (80%) | 6 | 0.645 | 533.8 | 2,233.9 |
| p003 | 10 | 9 (90%) | 7 | 0.783 | 347.7 | 725.4 |

Misses, recorded rather than tuned away:

- p000 `magna-carta-limits`: the Encyclopaedia article about Magna Carta outranks
  `Magna Carta (trans. Davis)`; `dred-scott-citizenship` returned the opinion
  subpages before the case page (rank 3), and `social-contract-opening` returned
  Book 1 at rank 2.
- p001 `korea-1972`: other national constitutions outrank
  `Constitution of the Republic of Korea (1972)`; `huck-finn-raft` found the work
  page at rank 3, and `emancipation-preliminary` at rank 2.
- p002 `moby-dick-opening` and `odyssey-homecoming`: chapter and book pages are
  selected ahead of the work landing pages, so the exact top-level title is not
  in the six returned documents; `areopagitica-printing` ranked 4 and
  `origin-species-introduction` ranked 5.
- p003 `beowulf-grendel`: other Beowulf editions outrank
  `Beowulf (Harrison and Sharp)`; `gettysburg-bancroft` ranked 2 and
  `aeneid-conington` ranked 3.

This measures host document retrieval only. It does not score generated answers
and does not predict Android latency or a target-device acceptance run, which
remains unmeasured.

## Conditions a reviewer must accept or reject

1. **Attribution.** Every record carries publisher `Wikisource contributors`, a
   canonical work URL, and a revision-history URL in its licence/provenance
   string. The pack and the in-app source reader must show the licence and the
   per-document history path.
2. **Share-alike.** Wikisource contributions, translations and annotations are
   CC BY-SA 4.0. Library text must stay separately licensed from the Apache-2.0
   application code, and any adapted text must be offered under the same licence.
3. **Indication of changes.** Text was normalized: wiki furniture, navigation and
   images were removed and page text was reformatted. The lock, records manifest
   and package licence state this.
4. **Per-work rights status.** Wikisource hosts only public-domain or freely
   licensed works, but this edition does not assert an independent
   public-domain/copyright boolean for each of 622,149 records. Each record points
   to its work page and revision history, and the edition relies on Wikisource's
   own rights review. That reliance must be disclosed.
5. **Revision pinning.** 2,897 records (0.5%) have no MediaWiki revision id, but
   every record has a history URL and the archive pins the whole source by
   digest. Archive-level pinning plus the per-record history path is the
   attribution mechanism for those records.
6. **Upstream defects.** 135 bodies contain U+FFFD replacement characters that
   are present in the pinned ZIM. A transcription can also preserve source
   errors, omitted pages, and historical spelling.
7. **Transcription, not critical edition.** The texts have no editorial
   apparatus, collation, or guarantee of completeness. Citation should name the
   Wikisource page and its revision history, not treat the package as the
   authoritative edition.
8. **High-stakes texts.** Court opinions, constitutions, treaties, statutes,
   speeches and medical or scientific texts can be amended, repealed,
   superseded, or translated by a named translator with separate rights. Nothing
   in this library is legal, medical, or professional advice; users must verify
   against the current official source.
9. **Name and mark use.** "Wikisource" is a Wikimedia project name. It is used
   only as an accurate source description, never as affiliation or endorsement.
10. **Deterministic split.** The four volumes are hash partitions, not topical
    selections. Each is independently installable, but the reviewed edition is
    complete only when all four are installed; the catalog must say so.

## Review decision

```
Reviewer identity: Qoder agent acting for the product owner (nonomnonom); automated review
Review date: 2026-10-24
Decision on conditions 1-10:
  1. Attribution — ACCEPT. Publisher, canonical URL and history URL are on every
     record; the package licence names Wikisource contributors.
  2. Share-alike — ACCEPT. Library text stays CC BY-SA 4.0 where applicable and
     separately licensed from the Apache-2.0 app; the profiles state the
     share-alike obligation.
  3. Indication of changes — ACCEPT. Lock, manifest and licence state that Blobs
     removed wiki furniture and images and reformatted the text.
  4. Per-work rights status — ACCEPT WITH LIMITATION. The edition relies on
     Wikisource's per-work rights review and preserves each work's history URL;
     it does not assert an independent rights determination per record.
  5. Revision pinning — ACCEPT WITH LIMITATION. 2,897 records lack a revision id;
     the pinned archive digest plus the per-record history URL is the recorded
     provenance.
  6. Upstream defects — ACCEPT. 135 bodies carry upstream replacement
     characters; dropping 135 real texts would cost more coverage than the
     disclosed defect, and the transcription caveat is published.
  7. Transcription, not critical edition — ACCEPT. The profile and reader must
     carry the caveat and cite the Wikisource page and history.
  8. High-stakes texts — ACCEPT. The profile forbids legal, medical and
     professional-advice claims and tells the reader to verify against the
     current official source.
  9. Name and mark use — ACCEPT. "Wikisource" appears only as an accurate source
     description.
 10. Deterministic split — ACCEPT. The profiles state that the four volumes are
     hash partitions and that the edition is complete only as a set.
Conditions accepted with limitations recorded in the catalog profiles: yes
```
