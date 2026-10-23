# Historical competitor runtime observations, 2026-10-23

> **Historical, not a Blobs specification.** This record preserves observations
> made from competitor artifacts that were available at the time. It does not
> authorise code porting, determine the Blobs runtime, or prove a leaderboard
> result. Active decisions are in the [engineering roadmap](engineering-roadmap.md)
> and proof requirements are in the [evaluation system](evaluation-system.md).

This record captures local, reproducible execution used to inform Blobs
engineering decisions. It is not an Android-device benchmark and does not
establish a bounty ranking.

## Read-only catalog observations

On 2026-10-23, the local competitor checkouts at
`D:/rimbara/bounty-submissions/commonplace` (commit `4d29fbe`) and
`D:/rimbara/bounty-submissions/fieldatlas` (commit `144c10c`) were inspected
read-only. The empty `D:/rimbara/blobs-ai` directory provided no artifact to
analyse.

- Commonplace documents user-selectable large reference packs, explicit
  installed-storage limits, package-level source reading, and desktop-built
  search assets. Its documented runtime combines lexical and dense retrieval,
  fusion, and cross-encoder reranking.
- Field Atlas documents optional global, travel, structured-place, and
  safety-focused collections with package sizes and source links. Its own
  release notes distinguish fast saved-data lookup from substantially slower
  generated answers, and its held-out result reports unresolved synthesis and
  reasoning weaknesses.

These are their documented claims and historical local artifacts, not Blobs
measurements. Blobs therefore adopts the product-level lesson—large,
user-managed, source-attributed libraries with visible storage and immutable
editions—while retaining its independently designed one-FTS-pass, one-JNI-call
runtime. No competitor code, format, asset, evaluation score, or runtime
contract is copied.

## Host and boundaries

- Host: local Windows x64 with AVX2/FMA/F16C/BMI2 available.
- Both runs used public, pinned artifacts and local CPU inference only.
- No answer was sent to a remote inference or embedding service.
- Desktop timing is not Android or GrapheneOS timing.

## FieldAtlas

### Verified artifacts

| Artifact | Identity | Result |
| --- | --- | --- |
| Answer model | `bartowski/Qwen_Qwen3.5-2B-GGUF@7d26695454df6de5fbcce2e58681e62dae06ce43`, Q4_K_M, Apache-2.0 | 1,396,198,496 bytes; SHA-256 `57a1085840f497d764a7fc5d346922dbde961efb54cc792ea81d694fd846a1d8` |
| Knowledge pack | FieldAtlas Biology & longevity 2026.10.13 | 8,183,535 bytes; SHA-256 `06e49130ad80d75a83275b7c0c09ac1ba47fb6ea2e3f6c5786b0426caed2b376`; extracted `content.sqlite` 27,254,784 bytes |
| Runtime | llama.cpp `60081bb2b5b3294165a4d67c5cbeebe74c868014` | Compiled locally with AVX2/FMA/F16C/BMI2 |
| Native search | Vendored Leviathan | Two JNI tests passed; the converted-pack fixture was intentionally skipped because its synthetic fixture was absent |

The Android/Kotlin desktop test could not run because the required Android NDK
29 license had not been accepted locally. It was not accepted on the user's
behalf.

### Evaluator observation

The local evaluator used a keyword planner, FTS retrieval, evidence packing,
local llama-server synthesis, and a local model judge. Its run for “Is a diet of
only meat and eggs healthy?” failed after 41.9 seconds. It retrieved seven
passages but the answer added unsupported nutritional particulars, and the
local judge rejected its confidence.

Raw report:
`D:/rimbara/bounty-submissions/fieldatlas/build/desktop-evaluation/fieldatlas-e2e-diet.json`.

The llama-server log reported ignored Qwen tensors from `blk.24` while loading
the artifact. This is a compatibility warning requiring upstream confirmation,
not a performance conclusion.

## Commonplace

### Verified artifacts

| Artifact | Identity | Result |
| --- | --- | --- |
| Knowledge bundle | `0xPD33/commonplace-packs/packs-2026-09/commonplace-breadth.tar` | 1,995,067,392 bytes; SHA-256 `36a7db9870c411e082316e2d48aad0fc570efab995697f4ef53422d39c32c525` |
| Encoders | `MongoDB/mdbr-leaf-mt` and `0xPD33/ettin-reranker-17m-commonplace` | Downloaded through the project-pinned script; local `SHA256SUMS` written |
| Answer model | `bartowski/Ling-3.0-tiny-GGUF@ea072726af0d2e8ba325b2f90fc0efa762105a91`, Q4_0, MIT | Reused verified local file, 4,623,917,248 bytes |
| CLI | `commonplace.exe` | Built locally after supplying CMake, libclang, and an MSVC-only empty `stdc++.lib` link shim; no project source changed |

The shim compensates for an unconditional `stdc++.lib` link directive that is
unavailable with MSVC. A cross-platform fix belongs in that project's build
script, not Blobs.

### Hybrid retrieval observation

On “What is photosynthesis?”, the installed bundle exposed 13 packs and 4.59
million passages. It returned relevant passages from Wikiversity, Wikibooks,
and OpenStax.

| Stage | Measured local time |
| --- | ---: |
| Query encoding | 16.2 ms |
| BM25 sparse search | 23.2 ms |
| Dense search | 22.9 ms |
| Fusion | 7.7 ms |
| Cross-encoder rerank | 697.5 ms |
| Answer card | 794.3 ms |

The run created 2,444 sparse candidates and 2,600 dense candidates, fused 40,
and reranked 40.

Two local Ling synthesis attempts stopped without a final response: 191.6
seconds with the default cap and 152.5 seconds with `--max-answer-tokens 64`.
Working set was approximately 5.0 GB and 4.9 GB respectively. These are
host-specific timeout observations. Fast retrieval alone does not establish
usable answer latency, and the result does not disprove a competitor's
physical-phone measurement.

## Lessons kept separate from product contract

1. Record retrieval and answer-generation time separately.
2. Treat source metadata as a trust boundary; do not assume it makes an answer
   correct.
3. A shared corpus, task, rubric, output bound, model, and device are required
   for a controlled comparison.
4. Record retrieval time, evidence-ready time, completion time, working set,
   citation precision, and answer quality independently.
5. Competitor code, runtime, wire contracts, formats, and assets remain
   read-only evaluation inputs and must not be copied into Blobs.

A past shared-retrieval control run against a travel-only Wikivoyage pack
returned 0/63 label hits and 63 `pending/model_warming` turns. That was a
scope-control observation, not a quality score or active baseline. Any future
comparison must name its active package manifest and suite result.
