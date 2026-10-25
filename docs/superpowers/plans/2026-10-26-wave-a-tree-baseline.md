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
`budget-1` ran the suite to 13 of 14 results (two cases skip without `wikipedia-top1m-mini`; the
fourteenth died on a 600 s UI wait, which is itself a sign the machine was contended). Eleven cases
are paired with `eightg-1`. The bound bit on four of them, and the other seven — identical prompts,
identical passages, identical character counts — are a free control for how the device felt:

| case | passages | passage chars | answer chars | first visible text |
| --- | --- | --- | --- | --- |
| compare-disc-rim-brakes | 6 → 5 | 5,113 → 3,970 | 710 → 458 | 109,731 → 88,969 ms (-19 %) |
| compare-tubeless-tubed-tyres | 6 → 4 | 5,915 → 4,481 | 682 → 400 | 114,804 → 162,212 ms (+41 %) |
| synth-winter-storm-home | 6 → 4 | 5,995 → 4,403 | 596 → 1,444 | 119,621 → 105,003 ms (-12 %) |
| synth-power-outage-household | 6 → 4 | 5,168 → 3,748 | 660 → 457 | 109,323 → 95,497 ms (-13 %) |
| compare-bangkok-hanoi | 6 (kept) | 4,073 | 798 | 87,460 → 98,955 ms (+13 %) |
| compare-magna-carta-bill-rights | 4 (kept) | 3,016 | 1,263 | 65,940 → 76,600 ms (+16 %) |
| compare-gettysburg-drafts | 4 (kept) | 3,454 | 1,043 | 69,687 → 79,281 ms (+14 %) |
| compare-korea-indonesia-emergency-powers | 6 (kept) | 2,765 | 889 | 59,272 → 68,691 ms (+16 %) |
| synth-house-cash-mortgage | 1 (kept) | 1,222 | 119 | 41,317 → 44,670 ms (+8 %) |
| synth-japan-rail-week | 6 (kept) | 2,672 | 901 | 65,214 → 74,772 ms (+15 %) |
| synth-geneva-conventions | 2 (kept) | 1,468 | 1,590 | 36,398 → 49,767 ms (+37 %) |

- The seven unbound cases are all slower, median **+15 %**, with unchanged prompts. So the machine,
  not the code, accounts for about a fifth of every turn in this run.
- The four bound cases cut 22-28 % of the prompt characters and moved -19 %, -13 %, -12 % and +41 %.
  Against the +15 % control that is roughly **-27 percentage points** for the cases the bound
  reaches, with one case (tubeless) moving the wrong way by more than the control explains. Three of
  the four land near the ~16 ms-per-character prediction (1,143-1,592 characters cut should save
  18-26 s; observed -20.8, -13.8, -14.6 s).
- It bound on **4 of 11** cases, because it only bites above ~4,500 characters of passages.
- It never failed a turn: no `PROMPT_BUDGET` throw appeared in thirteen cases, so the rule's worst
  case did not fire here — which is not the same as it being safe, and the other lane's
  never-throw floor makes it moot.

Read together: the bound is worth something on the large-prompt tail, at the price of 1-2 of six
sources on exactly the comparison questions where breadth is the point. That is why the surviving
rule has to shed retained history first, and why the ceiling on a *passage* is the cheaper lever if
it ever binds — which today it does not.

Two more things the table settles. A 6,000-character total budget **is not 6,000 characters of
evidence**, and the bound cases bracket what is left for the sources: these are first turns, so no
history is in the prompt and the scaffold — system instructions, the 175-character question, the chat
template — is paid for before any passage is added. `disc-rim-brakes` kept 5 of 6 passages (3,970
characters, the 6th would have made 5,113) and `tubeless-tubed-tyres` kept 4 of 6 (4,481, the 5th
would have made 5,651), so the space actually available to read is between 4,481 and 5,112 characters
and the scaffold costs between 888 and 1,519. Six sources at the observed median passage length of 858
characters fit in that; six max-length passages never would.

And the ±40 % spread between cases means a single paired run cannot carry a claim on its own: four
bound cases is enough to see a direction against an in-run control, not enough to quote a number. The
stage-decomposed timings from `0339ee6` are what turn this into an attributable per-stage figure, and
that is the run to pair against next.

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

`budget-1` re-fits the same relationship on a different build a day later and reproduces the
direction without the tightness: 23.4 ms per character, R² 0.675 over 11 cases, decode 6.9 tok/s
against the baseline's 8.6. That is the shape of a contended machine — same story, worse numbers —
and it is the reason every figure in this note is quoted as an order of magnitude rather than a
specification. The prefill-dominates finding survives it; the exact slope does not.

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
