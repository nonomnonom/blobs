# Wave A tree baseline, 2026-10-25

Captured before any Wave A code change, because Task 1 exists to make the later before/after
comparisons attributable to one tree.

## Where this wave lands — settled 2026-10-26, changed from the plan

Wave A code is **not** landing on `feat/offline-research-engine`. A second lane
`D:\rimbara\blobs-wave-a` on branch **`feat/speed-governor-wave-a`**, branched from this branch at
`81af8c7`, has committed the wave:

- `0339ee6` — per-stage native timings through JNI into the instrumented capture (Task 2)
- `c697823` — one fixed context capacity so the KV prefix survives a turn (Task 3)
- `4144120` — the SpeedGovernor spec and the three wave plans, now committed and citable

Task 4 (the prompt character budget) is in progress there and is **owned by that lane**. The
`withContextBudget` bound committed here as `4a2905e` is superseded by it, not to be extended: that
commit sheds retrieved passages first and fails the turn with `PROMPT_BUDGET` when a single passage
will not fit, and the paired device numbers below show what that costs. At merge time keep the other
lane's rule — shed retained history before cutting any passage, cut from the tail of the ranked list,
never fail the turn — and drop `4a2905e`.

This branch keeps the measurement lane: the 14-case suite, the shelf captures, the published catalog
work. It writes no more Wave A native or prompt code.

Main checkout `D:\rimbara\blobs` is on `release/0.4.0` at `823dfc9`.

## Foreign uncommitted edits over Wave A's own files

`git status --porcelain android/app/src/main/cpp` in the main checkout, with
`git diff -w --ignore-blank-lines --stat` (210 insertions, 74 deletions):

- `cpp/CMakeLists.txt` (+6): GGML AVX/AVX2/FMA/F16C/BMI2 enabled for x86_64 — uncommitted
- `cpp/kv_cache.h` (+22/-…): `kKvDecodeBatchTokens` 256 → 512, plus cache-interface changes — uncommitted
- `cpp/kv_cache.cpp` (+50/-…): prepare/reuse changes — uncommitted
- `cpp/blobs.cpp` (+48/-…): thread guard `> 4` → `> blobs::kMaximumThreads` (8) — uncommitted
- `cpp/local_model_runtime.cpp` (+157/-…): runtime path including prefill and sampler — uncommitted
- `cpp/local_model_runtime.h` (+1): declaration for the above — uncommitted

Six files, not the three the plan anticipated, and they include the exact files Task 2 and Task 3 must
modify (`local_model_runtime.{h,cpp}`, `kv_cache.{h,cpp}`, `blobs.cpp`).

**They are still uncommitted, and the Wave A lane deliberately left them out.** Checked in
`D:\rimbara\blobs-wave-a` at `c697823`: `kv_cache.h:10` is still `kKvDecodeBatchTokens = 256`,
`CMakeLists.txt:45` still forces `GGML_LLAMAFILE OFF`, and the x86_64 comment still says AVX features
remain runtime-dependent. So the AVX2 / batch-512 / 8-thread configuration that the earlier
measurements called the best native setup exists in **no branch at all** — it is only the working tree
of `D:\rimbara\blobs`, and it is not part of any Wave A number. Its author still has to commit it;
until then it cannot be cited as a baseline or claimed as a gain.

## Baseline for before/after comparison

Wave A's real baseline is therefore `81af8c7` as committed — threads rejected above 4, 256-token
batches with a 128-token ubatch, `GGML_LLAMAFILE` off, x86_64 SIMD limited to SSE4.2, KV buckets
doubling between 512 and 4096 — plus `4144120`, `0339ee6`, `c697823`. That is a state that exists in
git, which is what Task 1 asked for.

Task 4's bound was measured here before the lane split, on this branch at `4a2905e`, against
the `eightg-1` baseline: same 8 GB AVD, same shelf (all nine packages byte-identical by
`sha256`), same model `lfm2-1b2-q4km`, same suite `research-comparison-synthesis-v1` at
`cases_sha256 7f8955d1240d`.
The first four paired `COMPLETE` cases of `budget-1`:

| case | passages | passage chars | answer chars | first visible text |
| --- | --- | --- | --- | --- |
| compare-disc-rim-brakes | 6 → 5 | 5,113 → 3,970 | 710 → 458 | 109,731 → 88,969 ms |
| compare-tubeless-tubed-tyres | 6 → 4 | 5,915 → 4,481 | 682 → 400 | 114,804 → 162,212 ms |
| compare-bangkok-hanoi | 6 (unbound) | 4,073 (unbound) | 798 | 87,460 → 98,955 ms |
| compare-magna-carta-bill-rights | 4 (unbound) | 3,016 (unbound) | 1,263 | 65,940 → 76,600 ms |

What the numbers say, and why the other lane's rule is the one to keep:

- A 6,000-character total budget **is not 6,000 characters of evidence**, and the two bound cases
  bracket what is left for the sources. These are first turns, so no history is in the prompt: the
  scaffold — system instructions, the 175-character question, the chat template — is paid for before
  any passage is added. `disc-rim-brakes` kept 5 of 6 passages (3,970 characters, the 6th would have
  made 5,113) and `tubeless-tubed-tyres` kept 4 of 6 (4,481, the 5th would have made 5,651), so the
  space actually available to read is between 4,481 and 5,112 characters and the scaffold costs
  between 888 and 1,519. Six sources at the observed median passage length of 858 characters fit in
  that; six max-length passages never would. Breadth of sources is the thing the owner asked for, so
  shedding retained history has to come before cutting any passage.
- It has **no demonstrable latency win yet**: median +14.7 % on first visible text over n=4, with
  both signs present. Run-to-run noise on this profile swamps the effect the bound could have —
  retrieval alone moved 14,258 → 4,405 ms on one case and 6,342 → 8,693 ms on another.
- Therefore no single-run claim about the prompt bound is supportable on this device profile. Paired
  runs, or the stage-decomposed timings from `0339ee6`, are the only way to rate it against the
  other levers. That conclusion re-rates Wave B against Wave C and belongs in the wave sequencing
  decision.

## The §7.3 attribution, computed from the capture that already exists

The design's open question was which term dominates a turn, and it assumed prompt size was the
lever. Decomposing the twelve `COMPLETE` cases of `eightg-1` on the same 8 GB profile answers it
without a new run, because the capture already records retrieval, first-visible-text and end-to-end
separately:

| term | median | share of the 97,588 ms median turn |
| --- | --- | --- |
| retrieval | 11,595 ms | 11.9 % |
| load + prompt render + tokenize + prefill (first-visible minus retrieval) | 68,743 ms | 70.4 % |
| decode (end-to-end minus first-visible) | 26,558 ms | 27.2 % |

The dominant term is not a mystery: it is linear in the characters handed to the model. A
least-squares fit of `first_visible_text − retrieval` against prompt characters (passage text plus
the question, 12 points) gives **15.99 ms per character with a 6,653 ms intercept and R² = 0.979**.
At ~3.6 characters per token that is **17.4 tokens/s of prefill**, while the same twelve turns
decoded 9,862 answer characters in 319 s — **8.6 tokens/s**. Prefill is running only twice as fast as
decode, which is not what llama.cpp does when the batch path is working; on this tree it cannot:
`GGML_LLAMAFILE` is forced off, x86_64 SIMD is limited to SSE4.2, and the ubatch is 128.

That reframes the levers, in order of what they actually buy on a median turn:

- **Native prefill throughput** (llamafile, x86_64 AVX paths, ubatch): at a normal 100 tok/s the
  same 3,948-character prompt costs ~11 s instead of ~63 s, so **~52 s saved and no content
  removed**. This is Wave C's territory, and it is the biggest single term in the wave.
- **Prompt size**: halving it (dropping half the passages) saves ~32 s — real, but paid for in
  source breadth, which is the thing the reader sees.
- **Per-passage excerpt ceiling**: almost worthless here, and this is the finding that should stop
  any plan built on it. Across 58 retrieved passages the median is 858 characters and the longest is
  1,397 — the 2,000-character ceiling has never bound on this suite. Cutting it to 1,200 characters
  saves 1.8 s per turn, to 900 saves 9.8 s, and only 600 approaches the prompt-size number (22.8 s)
  while starting to amputate the passages that carry the answer.
- **Retrieval**: 11.9 %, noisy (4-16 s), and already known to be per-library cold opens rather than
  query work.

Scope: emulator profile, `device_acceptance: "unverified"`, and the attribution is arithmetic on one
capture, so `0339ee6`'s per-stage timings should confirm the split (specifically whether the 6.7 s
intercept is model load or context prepare) before Wave B/C sequencing is treated as settled. The
direction though is unambiguous: prefill throughput first, prompt budget second, evidence-on-screen
after, because the second and third levers are worth less than the first and one of them is worth
almost nothing.
