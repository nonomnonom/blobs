# Bounty requirements and evidence

This page tracks the public requirements for [poidh bounty #31](https://poidh.xyz/mainnet/bounty/31). It separates implementation checks from evidence that must come from a real device or an external review. A passing emulator build does not prove real-device compatibility or the quality threshold.

Status means:

- **PASS**: the stated scope has direct, repeatable evidence.
- **PARTIAL**: some evidence exists, with a known gap.
- **OPEN**: no sufficient evidence yet.

Last reviewed: 2026-10-18.

| Requirement | Status | Current evidence and remaining proof |
|---|---|---|
| Android and compatible GrapheneOS hardware | PARTIAL | Android debug build and emulator instrumentation run. GrapheneOS hardware and a physical Android device remain unverified. |
| Maximum 12 GB RAM | PARTIAL | The app ran inference in an emulator configured with 8 GiB. A repeatable peak-process-RAM report and real-device measurement are still needed. |
| Maximum 50 GB for app and offline assets | PARTIAL | Local signed kit 0.2.1 verifies at 3.85 GB distribution, 6.26 GB installed payload, and 10.12 GB modeled import peak with source packages retained. The kit is not yet built from the current public commit or published. Accounting excludes filesystem overhead, user data, and update coexistence; see its `FOOTPRINT.json`. |
| Fully offline after installation; no network requests during use | PASS (static/build checks) | The app does not declare Android's `INTERNET` permission, and the APK verification gate checks the offline boundary. Add an airplane-mode demonstration before submission. |
| Core functionality does not require Google Play Services | PARTIAL | The app is built from AndroidX and native dependencies. Verify the final release dependency report and an AOSP/GrapheneOS run. |
| Useful explanations, comparison, synthesis, and reasoning | PARTIAL | A versioned 40-question source-grounded evaluation exists. Eight paired model answers have been recorded; they need claim-to-source review and broader quality scoring. |
| Usable lookup speed on a phone | OPEN | Emulator retrieval and answer timings are recorded, but answer latency is high and no representative phone measurement exists. |
| Public GitHub repository | PASS | The source is public at [github.com/nonomnonom/blobs](https://github.com/nonomnonom/blobs). |
| Reproducible code, dependencies, assets, and instructions | PARTIAL | The `uv` corpus pipeline, Android source, tests, package verifier, and release-kit tooling are public. A clean-clone end-to-end release build and install instructions still need proof. |
| Models, datasets, indexes, and other resources documented | PARTIAL | Corpus source locks record URLs, hashes, licenses, and scope. The final selected model/library inventory and resulting package hashes must be published with a release. |
| Works on real Android hardware at submission | OPEN | This requirement cannot be proved by an emulator; keep it open until an actual device run. |
| Fresh setup in a few minutes | OPEN | A user-facing, timed clean setup from the public repo or release package has not been measured. |
| Required assets included or clearly downloadable | PARTIAL | `.blobs` packages are locally reproducible and the corpus sources are pinned. A public download location and tested instructions for the selected release assets are still needed. |
| Public demo showing offline use, hard queries, repo, and approach | OPEN | No submission demo has been recorded. |
| Screenshot and links submitted to poidh | OPEN | No bounty submission has been made. |
| Submitted build matches the public repo | OPEN | Verify the exact signed APK hash against the release commit when preparing a submission. |
| More than 50% as good as web search plus a frontier model | OPEN | No reference-answer set, rubric-based side-by-side scoring, or blind human review exists yet. This is the central quality claim, not something an offline benchmark alone can assert. |

## Current measured baseline

- Retrieval ran 40 versioned questions against the complete installed Wikipedia Top package. Exact expected-title coverage is 28/52 unique targets; retrieval returned all expected titles for 15/40 cases. These are coverage diagnostics, not answer-quality scores.
- Adjacent content-word phrases changed expected-title retrieval from 25/59 to 26/59 and matched the native Android evidence in 40/40 replay cases. The title denominator differs between the earlier and current evaluation variants; compare only runs using the same case file.
- Eight host answer pairs used identical model weights, runtime, sampler, questions, and retrieved evidence. The grounded prompt reduced median host answer time from 47.2 s to 30.5 s. Host time is not Android time, and valid citation syntax is not proof of factual support.
- One native emulator answer for the urban-heat question completed in about 89 s. This shows the local inference path runs, but it is not sufficient evidence of comfortable phone lookup speed.

## Submission proof still required

1. Build a new signed, immutable kit from the exact public source commit; local kit 0.2.1 predates later corpus commits. Verify its APK, package hashes, and total storage footprint before publication.
2. Run the exact release on compatible Android hardware, record peak RAM, answer timing, and airplane-mode behavior; separately verify GrapheneOS if compatible hardware is available.
3. Compare a versioned question set against web-search-plus-frontier reference answers with a stated rubric and blind human spot checks. Keep all raw answers and scores.
4. Record the required public demo, then submit its link, the exact repository commit, and a screenshot to the bounty.
