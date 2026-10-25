# Wave A tree baseline

Recorded 2026-10-26 before any implementation edit. Every before/after number in this wave cites this file.

## Where the work lands

- **Workspace:** `D:/rimbara/blobs-wave-a` — a linked git worktree, not the shared checkout.
- **Branch:** `feat/speed-governor-wave-a`, created from `feat/offline-research-engine` at `81af8c7`
  ("chore(release): publish the four-model download list and prove a device accepts it").
- **Why that base:** `feat/offline-research-engine` is 17 commits ahead of `release/0.4.0` and 0 behind,
  and it owns the surfaces this wave measures — `MAX_ANSWER_SOURCES = 6`, the four-model catalog, the
  0.5 B Qwen candidate, and the clean-tree shelf captures (`eightg-1`, `run6`).
- **Why not the shared checkout:** `D:/rimbara/blobs` carries another session's **uncommitted** edits to
  `cpp/CMakeLists.txt`, `cpp/blobs.cpp`, `cpp/kv_cache.*`, `cpp/local_model_runtime.*`, `NativeBridge.kt`,
  `ResearchWork.kt`, `LocalResearchEngine.kt`, `ResearchPrompt.kt` and `InferenceThreadPolicyTest.kt` —
  the exact files Wave A edits. Committing into that tree would fuse two people's work.

## Baseline native configuration (`81af8c7`, clean)

| Item | Baseline value | Where |
| --- | --- | --- |
| KV decode batch | `kKvDecodeBatchTokens = 256` | `android/app/src/main/cpp/kv_cache.h:10` |
| Context bucket | min 512, max 4096, doubling between them | `cpp/local_model_runtime.cpp:22-23,62-71` |
| JNI thread guard | rejects `threads > 4` | `cpp/blobs.cpp:51` |
| `GGML_LLAMAFILE` | **OFF** (forced) | `cpp/CMakeLists.txt:45` |
| x86_64 SIMD | SSE4.2 only (ABI-guaranteed); AVX/AVX2/FMA/F16C/BMI2 not forced | `cpp/CMakeLists.txt:56` |
| Arm kernels | `GGML_CPU_KLEIDIAI` ON when the pinned vendor tree exists | `cpp/CMakeLists.txt:47-53` |
| Prompt bounds | per-field only: question 4 000, source excerpt 2 000, history 300/500, `MAX_ANSWER_SOURCES = 6` | `inference/ResearchPrompt.kt:12-13,66,99-102` |

## Foreign edits held out of this baseline

Present only as uncommitted working-tree changes in `D:/rimbara/blobs`, deliberately **not** in `81af8c7`:

- `cpp/CMakeLists.txt`: `GGML_AVX`, `GGML_AVX2`, `GGML_FMA`, `GGML_F16C`, `GGML_BMI2` forced ON for x86_64.
- `cpp/kv_cache.h`: `kKvDecodeBatchTokens` 256 → 512.
- `cpp/blobs.cpp`: thread guard `> 4` → a raised maximum (8).
- `cpp/local_model_runtime.*`, `NativeBridge.kt`, `ResearchWork.kt`, `LocalResearchEngine.kt`,
  `ResearchPrompt.kt`, `InferenceThreadPolicyTest.kt`: further unstaged edits plus whitespace/reformat churn.

**Consequence for every claim in this wave:** the speedup those edits are expected to give is *not*
credited to Wave A, and Wave A numbers are not comparable to the older cost artifacts that already
include them. Before/after pairs inside this wave share `81af8c7` and nothing else.

## Correction to the design spec, §6.13

The spec asserted that commit `19ad372` dropped a global `MAX_PROMPT_CHARACTERS = 6_000` bound when the
engine moved to six sources. That is wrong about the mechanism:

```
$ git log --all --oneline -S"MAX_PROMPT_CHARACTERS"
f5eaab7 Blobs 0.4.0: signed catalog, rebuilt library portfolio, third model (#18)   # docs/architecture.md
6e0d158 docs: describe the released portfolio instead of a pending admission          # docs/architecture.md
$ git grep -n "MAX_PROMPT_CHARACTERS" HEAD
HEAD:docs/architecture.md:251:`ResearchPrompt.MAX_PROMPT_CHARACTERS` caps the built text at 6,000 characters:
```

The constant has **never existed in code** on any branch; only `docs/architecture.md:251` describes it.
So the finding stands — an unbounded prompt is what the model actually receives, with per-field caps only
(4 000 + 6 × 2 000 ≈ 16 000 worst case) — but the fix is *implementing a documented invariant for the
first time*, not restoring a regression. That makes it a behaviour change with a truncation decision to
justify, and the same change has to amend `docs/architecture.md:251` to describe what the code now does.

## Second correction found while editing: the shipped docs over-claim performance

`docs/architecture.md:320-326` states, on both `release/0.4.0` and this baseline, that inference
"threads are auto-detected from the device and clamped to 8", prompt decoding runs in "512-token
batches with a 256-token physical ubatch", and "x86_64 builds enable AVX/AVX2/FMA/F16C/BMI2 paths".
Commit `f5eaab7` introduced those sentences. **No committed code on any branch does any of it:**

| Claim | Computed code at `81af8c7` |
| --- | --- |
| threads clamped to 8 | `blobs.cpp` rejects `threads > 4` (baseline `:51`) and `NativeBridge.kt` sets `MAX_INFERENCE_THREADS = 4` (baseline `:3`) |
| 512-token batches | `kv_cache.h:10` `kKvDecodeBatchTokens = 256`, `params.n_ubatch = 128` |
| AVX/AVX2/FMA/F16C/BMI2 enabled | `CMakeLists.txt` forces none of them; only SSE4.2 is relied on |

So the published documentation describes performance work that is still uncommitted in
`D:/rimbara/blobs`. This wave leaves those sentences alone — the pending native edits are what make
them true, and rewriting them here would only create a conflict — but no Wave A record may cite them
as evidence of what ships. The `verify`-time docs check does not compare prose to binaries, which is
how this survived a release.

## Merge contract with the `feat/offline-research-engine` lane (agreed 2026-10-26)

The other session owns `D:/rimbara/blobs/.worktrees/offline-research-engine` and its uncommitted native
edits; this branch owns Wave A. Coordination settled as:

- Their `4a2905e "bound the prompt with a global character budget"` is **superseded** by `188b1c2` here.
  At merge, keep this branch's `ResearchPrompt.plan` / `PromptBudget` and **drop theirs**, including the
  `PROMPT_BUDGET` throw path — any test in their lane that asserts a throw has to go with it, because a
  budget that fails a turn is a product regression (`goal.md`: a retrieval miss never hides an otherwise
  usable answer).
- They do not touch `ResearchPrompt.kt`, `LocalResearchEngine.kt`, `NativeBridge.kt`,
  `ResearchPromptTest.kt` or `docs/architecture.md` while this branch is open.
- Their paired bound measurement (8 GB profile, `lfm2-1b2-q4km`, suite sha `7f8955d1240d`) confirms the
  budget binds by removing 1-2 of 6 passages on comparison questions. Read against this branch's
  55.7-58.7 ms per prompt token, `compare-disc-rim-brakes` (1 143 passage chars removed ≈ 335 tokens ≈
  19 s predicted, 20.8 s observed) matches, and `compare-tubeless-tubed-tyres` (1 434 chars ≈ 420 tokens
  ≈ 24 s predicted, +47 s observed) does not. **No single-run latency claim for the bound is supportable
  yet**, which is what this file's gate says; the delta is prefill-independent variance.
- Device split: `emulator-5554` = blobs-research (8 GB) is theirs; `emulator-5556` = blobs-e2e (2 GB) is
  this branch's. This branch left 5556 with `dev.blobs.app.debug` replaced by this build (APK digest
  `dffbbe9890554d93…`), airplane mode on (its prior state), and two extra staged run directories
  `files/evaluation/wavea-e2e-1` and `wavea-probe-1`.

## Gate

`git -C D:/rimbara/blobs-wave-a status --porcelain` is empty for `android/app/src/main/cpp`. If any
native file becomes dirty without a Wave A commit explaining it, stop and re-establish the baseline.
