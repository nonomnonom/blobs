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

## Gate

`git -C D:/rimbara/blobs-wave-a status --porcelain` is empty for `android/app/src/main/cpp`. If any
native file becomes dirty without a Wave A commit explaining it, stop and re-establish the baseline.
