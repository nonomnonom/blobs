# Blobs engineering roadmap

**Status: active for subsequent work, 2026-10-23.** This roadmap directs the
Blobs rebuild from architecture through release evidence. It applies only to
`blobs/`. No source code, runtime, schema, executable, or asset from `blobs-ai`
is a dependency or a porting target.

## Outcome

Blobs must become a useful global offline research product: a user can install
large, high-quality libraries, ask a question without a network connection,
receive a local answer with inspectable sources, and manage library editions
without losing trust or storage control.

That outcome is not a bounty-win claim. Claims about answer quality, latency,
memory, offline behaviour, catalog scale, or release readiness require
reproducible evidence with an APK, exact model, exact library editions, device,
configuration, queries, raw responses, and method.

## Product planes

The product fails if any plane is treated as secondary.

| Plane | Product output | Current owner | Done only when |
| --- | --- | --- | --- |
| Local runtime | UI, turn lifecycle, FTS, bounded prompt, JNI completion, source reader, installer, cancellation | `android/app` | A target-device run proves the one fixed local research path |
| Knowledge supply | Source locks, reviewed records, SQLite FTS, `.blobs` archives, provenance, licences, retrieval cases | `corpus/` | A large library can be reproduced from declared inputs and validated before use |
| Asset delivery | Signed catalog, Hugging Face packages, bundled fallback catalog, APK, offline kit, release notes | `android/tools/`, `android/docs/`, `docs/` | Catalog, asset, APK, and release evidence point to the same immutable identities |
| Product quality | Retrieval suite, answer review, device acceptance, report schema | `corpus/evaluation/` and Android acceptance owners | Improvements are measured on a held-out, fixed package set |

Hugging Face is an optional asset-delivery channel. It must never be an
inference, retrieval, agent, or answer service after installation. A real upload
or release requires approved input assets, authorised credentials, protected
private keys, and the validation gate appropriate to that release. Do not
publish placeholders just to make progress appear complete.

## Data is a first-class product

Blobs is not a starter pack plus a language model. The durable advantage is a
large catalog whose records can be traced, updated, installed, searched, and
cited.

### Catalog build order

| Wave | Library family | Why it comes next | Admission criteria |
| --- | --- | --- | --- |
| 1 | Core reference | Establish broad global lookup coverage | Reproducible large source edition, redistribution rights, language/scope metadata, package and retrieval cases |
| 2 | Travel and regional | Support practical global research and place context | Country/region coverage plan, source-date policy, explicit gaps, source-reader attribution |
| 3 | Primary-domain | Improve trust where general reference is weak | Official/first-party source preference, defined freshness policy, claim boundaries |
| 4 | Structured local data | Serve narrow factual tasks such as facilities or named places | Frozen snapshot, reviewer sign-off, source-query provenance, no inferred volatile claims |
| 5 | User libraries | Allow user-owned knowledge without weakening catalog trust | Clear unverified origin state, safe import, local-only ownership |

Large library size is acceptable and expected. The app must expose archive size,
installed size, language, scope, edition, source snapshot date, update policy,
publisher, licence, limitations, and storage impact before installation. Each
edition remains immutable; a newer edition is a new installable identity and is
never a silent replacement.

The signed catalog is the public data contract. Each library entry has a
required `library_profile` with an exact record count, scope, source snapshot
date, update policy, and limitations. The app shows this profile rather than
turning a broad description into an unsupported quality or freshness claim.

`corpus/supply/registry.json` controls the portfolio before a pack reaches the
catalog. It gives core reference, travel, structured facts, and primary
guidance distinct supply lanes; keeps legacy artifacts development-only or
retired; and carries the exact allow-list consumed by the publisher. A planned
family is not a dataset or package. Only a release-ready entry, whose source
lock, rights review, reviewed records, retrieval cases, archive receipt,
`records_sha256`, archive digest, and `library_profile` match the catalog item,
can be published.

### Pack admission contract

No library enters the public catalog without all of the following:

1. a source lock with edition, origin, licence, scope, and acquisition identity;
2. raw or revision-pinned input retained by the authorised maintainer;
3. documented selection, normalization, and exclusion rules;
4. bounded records with per-document attribution and provenance;
5. SQLite FTS package, archive manifest, archive SHA-256, and `records_sha256`;
6. declared language, geographic scope, record count, archive size, publisher,
   update policy, known limitations, and required attribution;
7. versioned retrieval cases that exercise coverage and known misses; and
8. a rights review that covers distribution of source text and derivative form.

When one source edition is split into complementary volumes, every admission
also names the immutable partition plan and volume ID. The publisher compares
the plan digest and source-lock digest against the build receipt, preventing an
arbitrary topical subset from being labelled as one volume of the edition.

A place-data pack additionally needs the raw snapshot digest, exact query,
source timestamp, selection rule, reviewer identity/date/notes, and an
explicit statement of fields it cannot prove. It must not turn a keyword match
into “open now”, distance, route, popularity, safety, or best-choice claims.

### Data lifecycle

```text
source decision -> source lock -> raw snapshot -> review -> normalized records
                -> FTS library -> immutable archive -> signed catalog entry
                -> verified local install -> retrieval cases -> new edition
```

A failed acquisition, review, build, package validation, publication check, or
install must not replace a previous good edition. Raw input and temporary
artifacts remain outside Git unless they are intentionally versioned source
metadata. A historical observation must say so if the original input, corpus,
device, or executable can no longer be reproduced.

## Production-readiness mode

The product owner explicitly re-enabled validation on 2026-10-23. The rebuild
is not considered complete until the current code is built, tested, exercised
on supported Android devices, and its data and release paths are verified.
Existing tests are regression evidence, not a substitute for missing
acceptance coverage. Never change or remove a failing test merely to make a
gate pass.

Start with non-destructive baseline checks and identify active processes before
starting overlapping builds, tests, or data preparation. Preserve those
processes. Run focused checks first, then repository verification and device
acceptance when prerequisites are available. Record exact commands, results,
and blockers. No performance, answer-quality, offline, or release claim follows
from compilation alone.

| Rebuild area | Direction | Current evidence state |
| --- | --- | --- |
| Research engine | `engine/LocalResearchEngine` owns one retrieval-to-completion pass and renders only the decoded answer | Unverified until current build, tests, and device flow pass |
| Response contract | `inference/StructuredAnswer` owns the response document, its grammar, and its decode | Unverified until the constrained completion runs on device |
| Native runtime | `local_model_runtime.cpp` owns model lifecycle, constrained sampling, and decode; `KvCache` owns transient exact-prefix reuse; `blobs.cpp` is a JNI adapter | Unverified until native build and model/device exercises pass |
| Dataset archive | `archive.py` owns the `.blobs` envelope; `package.py` carries `records_sha256` | Unverified until reproducible package build and archive checks pass |
| Transfer | `PackageTransferExecutor`, recovery, and `PackageTransferWork` own interrupted asset lifecycle | Unverified until cancellation, recovery, and atomic-install acceptance passes |
| Retrieval | SQLite boundary, validation, reader, aliases, and ranking are separated | Unverified until fixed retrieval cases run against identified packages |
| Persistence and UI | `ConversationStore`, Room migrations, scope snapshot, grounding, finish reason | Unverified until compile, migration tests, and interaction acceptance pass |
| Knowledge catalog | Source locks, provenance, pack admission, and publication contract | Existing artifacts are inventory, not a claim of quality or completeness |

## Runtime invariants

A question follows one fixed local path:

```text
question -> FTS retrieval -> evidence -> bounded prompt -> one grammar-constrained JNI completion
         -> response decode -> citation validation -> persistence -> UI and source reader
```

Keep these invariants unless a new approved product decision replaces them:

- no Rust runtime, local HTTP server, socket, remote inference, or remote
  retrieval in the installed research path;
- no agent loop, tool loop, hidden retry, second retrieval, or second completion;
- one bounded FTS statement per selected library, then local ranking;
- evidence saved before completion begins;
- citations can identify only evidence saved by that turn;
- any cancellation preserves valid partial output and recorded evidence;
- catalog/download activity is explicit and separate from research;
- no data pack makes claims that its source and freshness metadata cannot support.

## Engineering sequence

| Stage | Deliverable | Acceptance intent |
| --- | --- | --- |
| A. Contracts | English product docs, owners, language policy, source and package contracts | Every behaviour and data invariant has one owner |
| B. Core data | Reproducible core-reference pack inventory and admission manifests | Pack scope, size, source rights, provenance, and gaps are reviewable |
| C. Device package path | Verified catalog download or user import to atomic activation | Old package survives failed/cancelled update |
| D. Retrieval baseline | Fixed cases per library and FTS coverage diagnosis | Same package gives explainable hit/miss results |
| E. Prompt and inference | One bounded prompt and one grammar-constrained completion on native runtime | No hidden request path; the response shape is enforced by the sampler and re-checked in code; cancellation has a terminal result |
| F. UX and source reading | Clear research states, citations, library management, source reader | Evidence is distinct from model prose |
| G. Target-device validation | Cold/warm, one-to-five turns, memory, storage, thermal, cancellation, offline | Results name device, OS, ABI, model, library, configuration |
| H. Quality iteration | Held-out retrieval and answer review | Improve a measured weakness without changing uncontrolled variables |
| I. Publication | Immutable library uploads, signed catalog revision, verified release candidate | Catalog and package digests agree |
| J. Release | Signed APK, offline kit, install/upgrade/rollback records, release notes | A user can reproduce the intended asset set |

Stages are ordered but may loop back when new evidence shows a regression.
Semantic retrieval, reranking, new model families, spatial indexes, and
recommendation scoring are not assumed requirements. They are experiments only
after the lexical baseline exposes a measured gap on the target device.

## Evidence gates

Validation is active. Collect evidence in layers:

| Layer | What it proves | What it does not prove |
| --- | --- | --- |
| Deterministic contract tests | Input, package, migration, cancellation, persistence, prompt, citation identity | World-fact correctness |
| Retrieval evaluation | Corpus coverage, recall@k, evidence selection, ranking regressions | Target-device latency or answer quality |
| Device acceptance | APK, native lifecycle, actual package install, offline flow, observed memory/timing | Performance on every Android device |
| Human answer review | Whether claims are supported by the recorded passage and useful for the task | General model intelligence |

Comparisons hold model, library SHA-256, case set hash, APK, device, OS, ABI,
network mode, context policy, output limit, and warm/cold policy fixed unless
one is the declared experiment variable. Do not call a desktop observation an
Android result, and do not call a result a competitor win if corpora, task,
rubric, or device differ.

## Blocked now

These are recorded as blockers, not worked around. Most need an input or an owner
decision rather than a code change; the two runtime items stay listed because
what is missing there is a phone-class device, not a patch:

- Rebuilt editions are admitted and published. Under the corrected record contract these nine
  packages were rebuilt from the same pinned sources and each passed
  `runtime_contract.audit_library` with zero rejected documents, zero rejected passages and no
  duplicate ids: `wikivoyage-en@2026-09.5` (34,597 guides; installed and answered offline on
  device; retrieval replay 45 of 48 titles, 29 at rank 1, MRR 0.7431, identical to the withdrawn
  `2026-09.4` when re-scored with the same tool), `howto-repair@2026-10.4`
  (78,626 = published 78,633 minus exactly the 7 corrupt posts), `howto-practical@2026-10.4`
  (189,294 = 189,297 minus 3), `wikipedia-top1m-mini@2026-04.5` (997,714 = 997,717 minus 3, and
  all 997,714 records pass a full `validate_document` iteration), and
  `wikisource-en-history-p000`..`p003@2026-09.3` (155,527 / 155,739 / 155,378 / 155,370 documents,
  matching the rebuilt partition plan exactly). Coverage cost of the fix is therefore measured per
  lane, not assumed: 2, 7, 3, 3 and about 135 records respectively, and every case document id
  still resolves (`40` of `40` for Wikisource, no case changing volume). Each successor carries a
  rights review, re-pinned retrieval case files and a measured recall report, and a single atomic
  registry write retired each predecessor while admitting its successor.
  `nairobi-health-places@2026-10.1` was already clean and remains the only installed-and-answered
  place pack. See `corpus/supply/reviews/admissions-2026-10-24.txt`.
- The full-Wikipedia partition plan is not created, and its source lock says do
  not acquire or package.
- Catalog publication is done for the data release, and APK 0.4.0 is signed and published with a
  replacement release key.
  The catalog asset is now compiled from `corpus/supply/registry.json` plus
  `models.present.json`, so its nine libraries are the admitted editions and every entry carries
  the `records_sha256` the publisher requires. `docs/web/components/asset-tables.tsx` reads that
  same asset, so the public library page names only installable packs. The catalog was signed
  with the v3 P-256 key and uploaded with its packages to `nonomnonom/blobs-catalog`, and the
  v3-signed envelope was verified through the exact Java path `CatalogCodec` uses. The 0.3.14
  signing key is not on this host, in Git history, or in any published artifact, so 0.4.0 was
  signed with a replacement RSA-4096 key held outside the checkout. Its certificate SHA-256 is
  `626e4f7f02a11f74ddcab001c79a340c1e47918184bab606946b20cb96a444e3`; the APK is 31,215,434 bytes
  with SHA-256 `5fb939c2531a8b6d166a7a00f26a4ce2ae64224353854f77af0d462b9faa3be4`. Because the
  identity changed, 0.3.14 users cannot update in place: they must export their conversations and
  uninstall first, and the release notes say so. A fresh 0.4.0 install trusts the v3 catalog, so
  it no longer shows the withdrawn 0.3.14 revisions. Nothing already downloaded breaks: all twelve
  package URLs pinned by the 0.3.14 catalog still answer at their original commit, even though
  main no longer lists those files.
- `UiParityTest#readingControlsKeepDraftAndOpenSavedSourcesAtDoubleText` is the one

  red standard check and it is not a tap-delivery problem. At 2x font scale on the
  acceptance emulator it passed in the model-plus-library state against APK
  `73ca6f83e93cd808...` and passed clean data inside a 60-check suite on APK
  `d72c6e9e748f1687...`, then failed on clean data against APK `6849c249245dc686...`.
  The check was extended to name which path failed, and the failure message answers it:
  the chip is on screen (`Rect(84, 2267, 996, 2361)` of a 1080x2424 window), the tap is
  delivered, the page stays `CHAT` so the tap went nowhere else, the node's own click
  action is present, and invoking that action directly still shows no `Manage models`
  item within five seconds. A window swallowing the tap is therefore ruled out; what is
  missing is the menu itself in that state. This is the composer's popup, so it belongs
  to whoever owns that surface, and the numbers above are why the check keeps recording
  the distinction instead of asserting one cause.
- Gates on the current APK `cece676507b01d5d...` (three advertised models), 2026-10-24:
  `verify` is green (unit tests, ktlint over main/test/androidTest, debug and release
  assembly), the catalog-pinning check passes with LFM2 included, and
  `connectedDebugAndroidTest` runs 60 checks with the single composer-menu failure
  recorded above — that check is now red on APKs `6849c249245dc686...` and
  `cece676507b01d5d...` and green on `73ca6f83e93cd808...` and `d72c6e9e748f1687...`, so
  it tracks the composer, not the research path. A bare `am instrument` with no
  annotation filter is not a gate: it also runs the `ManualCheck` captures, which need
  fixtures and a network the gate does not have.
- The pinned 2B model does not cite inline. Every pulled journey artifact on it has
  zero citation markers in the answer while each turn claims `sourced`, so the
  reader's badge is structural (`AnswerGrounding`) and the claim is discarded. The
  grammar cannot supply the missing capability: masking the closing quote until a
  marker appears yielded no marker in three turns, doubled the turn to 26.5-29.5 s,
  and lost the support claim because the document never closed
  (`journey-cite-req-001`, reverted). Coverage needs a model that emits markers, or
  a per-sentence attribution mechanism, not a stricter mask.
- The first-run setup flow no longer depends on which device state it inherits.
  `setupStep` derived `READY` whenever any model was installed, so a device with a
  model and no local source was told to ask a first question and never saw the
  starter-library offer, and `continueSetup` stepped `MODEL -> READY` outright.
  The library step is now derived from installed packages, and the check covers
  all three states: no model, a model with no library, and both assets present.
- The two catalog-download captures (`starterLibraryFromHubAppearsInYourLibrary`,
  `emergencyPreparednessLibraryFromHubAppearsInYourLibrary`) fail at
  `catalog.refreshNow()` before any package transfer, because the remote signed
  catalog is not fetchable while nothing in the registry is publishable. They are
  `ManualCheck` captures, excluded from the standard suite, and they are supply
  blockers rather than runtime failures.
- A ~1B candidate is measured and waiting on a decision, not on engineering.
  `lfm2-1b2-q4km` (LFM2-1.2B Q4_K_M, 730,893,248 bytes) is pinned in
  `android/models.lock.json`, loads on the existing llama.cpp pin, and installs through
  the normal model-package path. On one APK it beat the 2B default on every measured
  axis: 50.3 against 92.3 ms per token constrained, 6.4-11.7 s against 15.9-29.2 s per
  journey turn, 1.30 against 1.89 GiB process PSS after a turn. Making it selectable in
  the app still needs the catalog rebuilt with release tags and a signing key, which is
  owner-authorized publication work, and its LFM Open License 1.0 caps Commercial Use
  below 10,000,000 USD annual revenue, which only the owner can accept. Its published
  language list is English-first and omits Indonesian, unlike the current default.
- `/no_think` is a Qwen chat-template control, and it used to be appended to every
  request, so a non-Qwen model received a token that means nothing to it. It is now
  offered only when the selected model's id declares the family
  (`ResearchPrompt.understandsThinkingControl`), and the LFM2 measurements above were
  taken before that change, with the stray token present.
- Grammar-constrained decoding no longer costs extra. The chain used to put the
  grammar first, so its mask walked all 151,936 vocabulary entries every token:
  180 ms per token against 64 ms unconstrained, 29.8 s against 18.4 s on one real
  2,610-character three-passage prompt (`cost-app-001`). The runtime now samples
  the pruned chain, verifies that single token against the grammar, and pays the
  vocabulary-wide mask only when the candidate cannot extend the document, which
  is llama.cpp's own default order. Re-measured on the same prompt: 63.9 ms per
  token constrained against 64.3 unconstrained, and the response document still
  decodes (`cost-app-002`).
- Turn stages are now individually timed, and the measurement settles the open question from
  2026-10-25 about where a shelf turn's wait actually goes. On the 2 GB emulator profile
  (`blobs-e2e`, API 36, x86_64, four threads, APK `dffbbe9890554d93…`, LFM2-1.2B Q4_K_M, one
  `wikivoyage-en` library, four turns across `wavea-e2e-1` and `wavea-probe-1`):
  **prefill is the wait** — 55.7-58.7 ms per prompt token, steady across 998, 1 222 and 1 426-token
  prompts, so the cost is linear in tokens and the earlier "superlinear in prompt characters" reading
  is retracted. `compare-bangkok-hanoi` spent 80.6 s of prefill inside a 85.8 s first character, with
  2.2 s cold model load, 2.5 s retrieval and 35 ms context prepare accounting for the rest; the second
  turn of the same run loaded in 0 ms (`model_cached: true`) and prepared in 0 ms because the capacity
  is now fixed at 4 096 rather than doubling per prompt.
- **Prefix reuse is broken inside the runtime, not by prompt divergence.** `wavea-probe-1` ran the same
  question twice in one process: identical 4 261-character prompts, identical 1 222 token counts, and
  `reused_prefix_tokens` still 0 on the second turn, which cost 71.7 s rather than the 68.1 s of the
  first. The context object itself survived (turn two reported `context_prepare_ms` 0), so the loss is
  in `KvCache::reuse_prefix` — either the matching scan or the `llama_memory_seq_rm` call against a
  `kv_unified` memory — not in the tokens being compared. It is a recorded defect, and no speed claim is
  made for it.
- **The 56 ms/token figure is a build property, and it changes the lever ranking.** It was measured on a
  baseline that forces no AVX features (only SSE4.2, the x86_64 ABI guarantee). The
  `feat/offline-research-engine` lane then landed `35869bf` (AVX/AVX2/FMA/F16C/BMI2 for x86_64) and, on
  its 8 GB emulator with the same shelf, model and case suite, first visible text fell from 88 969 /
  162 212 / 98 955 ms to **18 551 / 15 566 / 20 129 ms** — prefill ≈ 6 ms/token. `2e736fa` added
  `GGML_LLAMAFILE` (~11 % more on x86_64; arm64 has no AVX2 fallback, so on a phone that flag carries it)
  and `6919d3a` ran the per-library FTS searches on the common pool (retrieval 3 209 → 1 876, 4 686 →
  2 606, 10 969 → 5 967 ms at identical passage counts). Consequences: at this build speed a repeated
  turn loses the reusable common prefix of a real follow-up, which a three-turn conversation measured
  at 1.2-1.9 s per turn — not the whole-prompt ~7 s the first arithmetic assumed, because a follow-up
  shares only its system-and-history prefix with the previous turn, not all 1 222 tokens. The defect
  therefore no longer outranks answer-length policy; and the answer bound `MAX_RESEARCH_OUTPUT_TOKENS`
  moved 320 → 200 for every mode (the owner's 2026-10-25 decision) because the longest remaining tail
  is decode, evidenced by a 57 632 ms turn whose visible answer was 400 characters. Neither set is
  phone evidence: the AVX2 numbers
  come from the other lane on the 8 GB emulator, and every figure here says `unverified`.
- Every capture from this wave names its APK digest, fingerprint, API, ABI, thread count, prompt size and
  stage timings, and each states `device_acceptance: unverified`. Records live in
  `corpus/evaluation/results/wavea-e2e-ui-1.json` and `…/wavea-probe-ui-1.json`.
- The sampling chain is greedy with the repetition penalty kept, on the owner's repeated instruction to
  reach the speed target: the removed top-k/top-p/temperature passes were three vocabulary-wide scans
  per token worth 12-13 s of a 0.5B turn, and three paired answers were byte-identical without them.
  The 14-case gate passed on this chain and the suite itself runs 3.8× faster than at 0.4.0.
- Where a settled turn's 27 s actually goes, median over three independent captures with nine common
  cases (0.5B on the 8 GB emulator, same shelf): retrieval 3,347 ms, prefill 9,012 ms (~156 tokens/s —
  healthy after AVX2 and llamafile), decode 15,062 ms, end to end 27,421 ms; the three captures agree
  on a 25.5-28.9 s median and 6 of 9 cases finish under 30 s. Decode at 7.4 tokens/s is the wall, and
  it is the emulator's wall, not the model's: the same 0.5B Q4 runs 25-40 tokens/s on an arm64 8 GB
  phone, which would put a full turn near 12-16 s — a figure only a device can demonstrate. Even a
  one-token answer costs 12.4 s here (retrieval plus prefill), so "under 15 s" on this host also needs
  the retrieval lever, not just shorter answers. Run-to-run noise on this host is ±20 %, and two
  earlier single-run claims were downgraded on that basis.
- Thread policy is measured, not guessed: eight threads on a four-CPU target took
  442 s for 75 tokens against 4.8 s at four threads (`cost-baseline-001`), so the
  count comes from the device processor count and is resolved once per process.
  What is still missing is a phone-class device for both items above: every
  timing in this repository is emulator-only, so no latency claim about the
  99-second turns a user reported is in evidence yet, and no thread count above
  the emulator's four has ever been measured on real silicon. Confirmed with the
  product owner on 2026-10-24: no phone-class device can be attached for now, so
  device acceptance is a recorded blocker, not a step being skipped.
- Publishing under the rotated v3 catalog key momentarily stranded existing installs, and 0.4.0
  resolves it by starting a new signing identity. The owner kept the v3 rotation on 2026-10-24.
  Installed APK v0.3.14 trusts only `catalog-public-key-v2.pem`, so it rejects a v3-signed catalog
  and keeps showing its stale bundled revision 2026102002 - the twelve libraries the registry has
  since withdrawn. APK 0.4.0 bundles the corrected catalog and the v3 trust anchor, but because its
  signing key differs, 0.3.14 users must export conversations and uninstall before installing it.
  The release keystore and `BLOBS_*` values stay outside this checkout.
- CI never runs instrumented tests: `verify` builds and compiles them only, so
  device acceptance stays a manual, recorded step.
- The pinned `clang-format==23.1.3` rejects the committed native style: an
  unmodified `git show HEAD` copy of `blobs.cpp` already fails the
  `--dry-run --Werror` check the corpus job runs. The formatter or
  `.clang-format` needs one deliberate decision before that gate can be green;
  reformatting five files to a new default is not that decision.
- Python formatting drift is down to 7 files from 19. Twelve files carrying no
  uncommitted edits from another session were reformatted on 2026-10-24 under the
  decision that pinned `ruff 0.17.1` plus the repository `ruff.toml` are canonical;
  `ruff check` stayed clean and both suites still passed (84 corpus, 38 tooling), so
  the change is formatting only, proven by the suites rather than assumed. The seven
  that remain are `android/tools/publish_hf_assets.py`,
  `android/tools/tests/test_publish_hf_assets.py`, `corpus/blobs_corpus/{places,portfolio}.py`,
  `corpus/evaluation/retrieval/host_replay.py`, and `corpus/tests/{test_curate,test_places}.py` -
  all of them holding another session's uncommitted work, so their owners run
  `ruff format` on them before committing. The native gate is in the same position:
  all five files under `android/app/src/main/cpp/` carry uncommitted edits, so the
  `clang-format 23.1.3` pass waits for that lane, and it will still need the same
  one deliberate decision about whether the pinned formatter or `.clang-format` wins.

## Immediate next work

1. Establish a safe baseline: inspect running work and available toolchains,
   then run the smallest deterministic Android and corpus checks.
2. Compile the current app, run JVM and corpus/release tooling tests, and
   resolve actual failures without obscuring unrelated baseline issues.
3. Inventory existing core-reference inputs, source rights, reviewed records,
   package identities, retrieval cases, and signed-catalog consistency.
4. Verify Android install, update, cancellation, recovery, chat, citations,
   history, import/export, storage, and offline behavior on available supported
   devices; record unavailable devices as blockers.
5. Use fixed package-specific cases and repeatable device records to find the
   largest measured answer-quality, retrieval, reliability, or latency gap.
6. Fix and re-run the relevant evidence gates. Keep claims limited to what the
   exact APK, model, package, device, and test record demonstrate.
7. Publish only owner-authorized, verified immutable assets, sign the catalog,
   validate the published revision, and create release evidence.
