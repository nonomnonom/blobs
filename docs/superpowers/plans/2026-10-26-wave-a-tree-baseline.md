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

## What the Wave A lane answered, 2026-10-26 01:46

Task 4 landed as `188b1c2` and their tree is clean: `4144120` spec and baseline, `0339ee6` stage
timers, `c697823` fixed capacity, `188b1c2` prompt bound, `e593ced` capture header fields, `696e65e`
roadmap numbers, `f7dc720` style, `ddfa6e1` + `00e18cc` the merge contract. They accept dropping
`4a2905e`, and their rule never throws, which retires the `PROMPT_BUDGET` path and its test.

Their native stage timers and my regression agree without being compared: **55.7-58.7 ms per prompt
token** from `generate()` on the 2 GB profile, **15.99 ms per character** fitted over twelve captures
on the 8 GB profile. At 3.4-3.6 characters per token those are the same measurement made two ways on
two devices, so prefill being the whole wait is not an artifact of either method. They also applied
it back to this file's paired data and got the same verdict: 335 tokens removed predicts 19 s and
20.8 s was observed on one pair, 420 tokens predicts 24 s and +47 s was observed on the other.

The lever then moved out from under everything. Their probe `wavea-probe-1` asked the same question
twice in one process — identical 4,261-character, 1,222-token prompt — and still recorded
`reused_prefix_tokens: 0` with 71.7 s on the second turn, while `context_prepare_ms` was 0. The
context survived and `KvCache::reuse_prefix` (`android/app/src/main/cpp/kv_cache.cpp:59`) did not, so
prefix reuse is broken as a code defect, not as a prompt shape. That is Wave A's own Task 3 exit
criterion, and it now outranks the warm path.

Device split corrected against what I wrote earlier: `emulator-5554` is **blobs-research (8 GB)** and
belongs to this lane; `emulator-5556` is **blobs-e2e (2 GB)** and is theirs, left holding their APK
`dffbbe9890554d93…`.

### One defect this lane proves and then deliberately leaves to the merge

`engine/LocalResearchEngine.kt:105` publishes the **retrieved** set to `onEvidence`, and `4a2905e`
may trim it afterwards, so on a bound turn the source panel, the persisted checkpoint and the new
pending chips can name six passages while the model was handed four. `188b1c2` computes the carried
set before publishing, so the merge closes it, and the two-line republish that would have closed it
here was withdrawn rather than duplicated because `ddfa6e1` bars this lane from editing that file
while Wave A is open. Both halves were measured on device: a build carrying the republish showed
exactly four chips on `compare-tubeless-tubed-tyres` (`bound-ui-1`: 4 published passages, cited S1-S4
contiguously, reader verified, first text 97,465 ms), while the branch as committed shows the
untrimmed six. The six-chip layout itself is captured in `packing-ui-1`.

## The prefill multiplier was a build flag, not a model limit

The Wave A stage timers settled where a turn goes: of ~86 s, **prefill was 80.6 s** and every other
stage - model load 2.2 s, template 6 ms, tokenize 5 ms, context prepare 35 ms, grammar 0 ms, sampler
0 ms, first token 14 ms - was under 70 ms in total. Prefill cost 55.7-58.7 ms per prompt token,
which is barely twice the documented 64 ms per token of plain decode on this target. A runtime whose
prefill costs what its decode costs is not batching its matrix multiplies.

Two settings explain it, and neither was a llama.cpp limit. `cpp/CMakeLists.txt` enabled only
`GGML_SSE42` for x86_64 - the quantised GEMM kernels want AVX2 - and `GGML_LLAMAFILE` was forced
`OFF` with no reason recorded anywhere (`git log -S` reaches one squash commit). AVX/AVX2/FMA/F16C/
BMI2 are now enabled for x86_64, which is safe because ggml dispatches on CPUID at runtime.

Measured on `emulator-5554` (blobs-research, 8 GB), same shelf, same model `lfm2-1b2-q4km`, same
four cases, against `budget-1`:

| case | first visible text | end to end | retrieval |
| --- | --- | --- | --- |
| compare-disc-rim-brakes | 88,969 -> **18,551 ms** | 114,609 -> **40,327 ms** | 4,405 -> 2,644 ms |
| compare-tubeless-tubed-tyres | 162,212 -> **15,566 ms** | 209,483 -> **34,208 ms** | 8,693 -> 5,273 ms |
| compare-bangkok-hanoi | 98,955 -> **20,129 ms** | 110,016 -> **45,866 ms** | 18,486 -> 11,086 ms |

**Five times faster to the first character, 2.4-6x faster end to end**, and prefill is now roughly
6 ms per token instead of 56. The turn is no longer dominated by one stage: retrieval at 2.6-11 s is
now the same order as the whole model path, so the retrieval fan-out fix moved up the queue with it.

Two things this does not do. It does not make the phone faster: arm64 already had NEON and KleidiAI,
and `GGML_LLAMAFILE` is still forced off for it, which is the next measurement. And it does not
justify raising the thread cap - the documented scaling is 130 ms per token at one thread, 79 at two,
64 at four, and asking for eight on this four-CPU target collapsed to 442 s for 75 tokens.

`GGML_LLAMAFILE` was then turned on for both ABIs and re-measured on the same four cases. On x86_64
it adds little once AVX2 exists: median first visible text 18,551 -> 16,582 ms and median end to end
40,327 -> 35,256 ms, with one case unchanged within noise. That is the expected shape - llamafile's
blocked GEMM is the thing AVX2 unlocked here - but it is kept because **arm64 has no AVX2 at all**:
on a phone this flag is the difference between the NEON path and the scalar one, and the phone is the
device that matters. End to end on this build is now 31.6-44.2 s against the 97.6 s median the same
shelf measured an hour earlier, so retrieval at a 7.8 s median is now the largest single non-model
term and the fan-out fix is next.

### Qwen2.5 0.5B against LFM2-1.2B on the same fast build

The 0.5B was installed through the product path (`downloadsAndInstallsPinnedCatalogPackage`,
`q554-install`, 120.9 s for 491,400,032 bytes) and run on `emulator-5554` with the same shelf, the
same four cases and the same APK as `llamafile-1`, so the only variable is the model.

| case | 0.5B first text | 0.5B end to end | LFM2 first text | LFM2 end to end | retrieval |
| --- | --- | --- | --- | --- | --- |
| compare-disc-rim-brakes | 13,686 ms | 36,720 ms | 13,376 ms | 31,596 ms | 3,208 ms |
| compare-tubeless-tubed-tyres | 15,081 ms | 61,332 ms | 15,244 ms | 33,982 ms | 5,132 ms |
| compare-bangkok-hanoi | 19,133 ms | 35,408 ms | 20,294 ms | 44,162 ms | 10,874 ms |
| compare-magna-carta-bill-rights | 16,883 ms | 30,685 ms | - | - | 11,086 ms |

Three findings, none of them the expected one:

- **The 0.5B is not faster than LFM2 any more.** Time to first text is a tie (medians 15,982 and
  ~15,310 ms), because prefill is now cheap enough that the model size barely moves it.
- **Decode is the new dominant term.** End to end minus first text is 16-46 s, and the 0.5B's worst
  case is 46 s for a 400-character answer - about 2.8 tokens per second, slower than LFM2's 19 s on
  the same turn. A small model that will not stop talking is slower than a bigger one that does.
- **Retrieval is now 9-36 % of the turn** (3,208-11,086 ms) while the whole model path costs about
  15 s. It went from a rounding error to the second biggest term, which is what makes the parallel
  per-library fan-out the next change rather than any further prefill work.

So the owner's bar - a 0.5B turn under 30 s - is met on first text (13.7-19.1 s) and on two of four
cases end to end (30.7 and 35.4 s), and is missed by the two cases where the model keeps generating.
The remaining path to it is output length and retrieval fan-out, not the model.

### Retrieval fan-out: the seven searches now run together

Each enabled library is an independent FTS search on its own connection, so the sequential loop
added their wall-clock costs together. `LibraryStore.retrieve` now runs them on the common pool and
writes every result back at its own index, which keeps the candidate list in library order - the
order the ranking's last tie-break depends on - so the evidence set is unchanged by construction and
no SQL, stop-word or ranking text was touched (the diff is the loop plus a moved `searchLibrary`
helper). `host_replay.py`'s `LIBRARY_STORE_SHA256` pin was re-pointed at the committed file for that
reason; the mirror still models the same policy.

Measured on `emulator-5554`, same shelf, model and cases as `llamafile-1`, only the retrieval loop
changed:

| case | retrieval | first text | end to end | passages |
| --- | --- | --- | --- | --- |
| compare-disc-rim-brakes | 3,209 -> **1,876 ms** | 13,376 -> 13,636 ms | 31,596 -> 31,567 ms | 5 -> 5 |
| compare-tubeless-tubed-tyres | 4,686 -> **2,606 ms** | 15,244 -> 13,106 ms | 33,982 -> 31,487 ms | 4 -> 4 |
| compare-bangkok-hanoi | 10,969 -> **5,967 ms** | 20,294 -> 15,202 ms | 44,162 -> 39,868 ms | 6 -> 6 |

**~1.8x on retrieval, identical passage counts.** Three of the four turns now finish in 31.2-31.6 s
with LFM2-1.2B on an emulator, against a 97.6 s median two hours ago.

A method note worth keeping: two captures before this one hung with the question sitting unsent in
the composer and no ANR in logcat. That was stale app state - a turn left RUNNING by an earlier
timed-out run - and a plain `am force-stop` cleared it. It is not a reason to distrust a change, but
it is a reason to restart the app before blaming a build for a hang that happens before any of its
code runs.

### The 0.5B on the optimized build, and the one term left

Same four cases, same shelf and device, model `qwen25-0b5-q4km`, build `6919d3a`:

| case | retrieval | first text | end to end | visible answer |
| --- | --- | --- | --- | --- |
| compare-disc-rim-brakes | 2,335 ms | 12,688 ms | 34,996 ms | 458 chars |
| compare-tubeless-tubed-tyres | 2,977 ms | 12,976 ms | **57,632 ms** | 400 chars |
| compare-bangkok-hanoi | 5,612 ms | 13,985 ms | **29,894 ms** | 937 chars |
| compare-magna-carta-bill-rights | 5,817 ms | 11,609 ms | **25,337 ms** | 384 chars |

Two of four turns now finish under 30 s and every turn reaches its first character in 11.6-14.0 s,
against 43.9 s first text and 99.1 s end to end for this same model on 2026-10-25. What is left is
one number: the tubeless turn spent **44.7 s decoding a 400-character answer**, which means it ran to
`MAX_RESEARCH_OUTPUT_TOKENS` and the visible prose was only the part that survived citation
validation. Decode is no longer a cost to make faster, it is a length to stop.

That constant is `engine/LocalResearchEngine.kt:17`, a file this lane agreed not to edit while Wave A
is open (`ddfa6e1`), so the change is handed to that lane with these numbers rather than applied
here.

### Batch shape is exhausted: 512 was tried and reverted

`kKvDecodeBatchTokens` (`cpp/kv_cache.h:10`) sets `n_batch`, `n_ubatch` (half of it) and the prefill
loop stride. llama.cpp defaults far higher, so it was raised to 512 and measured on the same four
cases. First visible text moved 13,636 -> 12,571 ms on `compare-disc-rim-brakes` - about 8 %, at
noise level - end to end got worse (31,567 -> 36,936 ms), and the next case crashed the
instrumentation (`Tests run: 1, Failures: 1`, report left at `complete: false`). Reverted.

With AVX2, llamafile and Q8_0 KV already in place, prefill is ~6 ms per token and there is nothing
left in batch shape. What is left is length: `MAX_RESEARCH_OUTPUT_TOKENS` (320) in
`engine/LocalResearchEngine.kt:17`, which this lane is barred from editing, and the `reuse_prefix`
defect in the Wave A lane.

### Decode was the sampler, not the model

Two changes, measured on `emulator-5554` with `qwen25-0b5-q4km`, the same shelf and the same four
cases at each step.

`MAX_RESEARCH_OUTPUT_TOKENS` 320 -> 200 (`q554-cap200`): the worst turn went 57,632 -> 41,699 ms and
**every surviving answer was byte-identical** (458 / 400 / 937 characters, 5 / 4 / 6 citations). The
120 tokens it used to spend had contributed nothing - they were generated and then dropped by
citation validation. The median did not move; this change removes a tail.

The sampling tail itself was the real cost. Replacing `top_k(20) -> top_p(0.9) -> temp(0.3) ->
dist(42)` with `greedy`, keeping the grammar constraint and the repetition penalty, gave
(`q554-greedy`, complete, 4 of 4 cases):

| case | sampled | greedy | answer |
| --- | --- | --- | --- |
| compare-disc-rim-brakes | 36,955 ms | **25,225 ms** | 458 chars, 5 citations - identical |
| compare-tubeless-tubed-tyres | 41,699 ms | **28,405 ms** | 400 chars, 4 citations - identical |
| compare-bangkok-hanoi | 30,582 ms | 34,104 ms | 937 chars, 6 citations - identical |
| compare-magna-carta-bill-rights | - | **26,237 ms** | 384 chars, 4 citations |

Three of four turns now finish under 30 seconds and the paired answers did not change at all, which
is what a temperature of 0.3 predicts. The cost being removed is three vocabulary-wide passes over a
151,669-entry vocabulary per token, on a model whose own forward pass is smaller than that scan.

This is a decoding-behaviour change, so it is gated on the 14-case suite, not on four: greedy can
loop, and identical answers on four cases is evidence, not proof. The repetition penalty stays at 1.2
for that reason. Pending that run this is recorded as measured but not settled.

### The 14-case gate: greedy holds, and the suite is 3.8x faster

`q554-suite` ran the whole `research-comparison-synthesis-v1` suite (sha `7f8955d1…`, the same file
the 97.6 s median baseline used) with `qwen25-0b5-q4km` on the optimized build: **complete, 12
cases run, 2 skipped for the missing `wikipedia-top1m-mini`**.

- median first visible text **12,694 ms**, median end to end **25,798 ms**
- 8 of 12 cases under 30 s; the four over it are 31,242 / 33,704 / 37,597 / 40,585 ms
- **all 12 cases cited** (1-6 markers each) and `cited_source_reader_verified` was true on all 12

Against `eightg-1` (LFM2-1.2B, same suite, same device, same shelf, before any of this): median
78,573 -> 12,694 ms first text (**6.2x**) and 97,587 -> 25,798 ms end to end (**3.8x**).

So greedy is not looping and not silently degrading: the quality signal the app reports - a citation
whose passage the reader can open and match - held on every turn. What is left is the four long
turns, and those are the ones prefix reuse would pay for.

### Prefix reuse: the precondition is real, the win here is not

`context_capacity` in this branch doubles a bucket between 512 and 4096, and `KvCache::prepare`
treats a capacity change like a model change - it frees the context and the remembered prefix with
it. That is the mechanism the design blamed for reuse never landing, so it was pinned to one fixed
4096 and measured on the same four cases.

Nothing moved: median end to end 27,321 -> 27,019 ms, and all four answers were byte-identical.
First turns of fresh conversations do not cross bucket boundaries often enough for the churn to
cost, and a follow-up turn only shares the system block plus earlier questions, which is a few
hundred tokens of a ~1,400-token prompt. Reverted, because the fixed capacity is not free: it holds
a 4096-token KV cache on a 2 GB phone at all times.

The change belongs to `c697823` in the Wave A lane, which already pins one capacity, and it should
arrive through that merge rather than as a duplicate here. What is left for reuse is theirs to
measure: their own probe still saw `reused_prefix_tokens: 0` on an identical prompt with
`context_prepare_ms: 0`, which is a defect inside `KvCache::reuse_prefix`, not a capacity question.

### One experiment of mine was wrong and was removed

Connection pooling in `LibrarySqlite` (hold one leased connection per package across turns) was built
on the belief that a turn pays a cold FTS open per library. It does not: with pooling in place, the
first turn of a fresh process retrieved in 4,083 ms and the second in 4,669 ms - no cold penalty, so
the pages were already resident and the seconds are query work. Worse, the pooled `open()` performed
the multi-second SQLite open **inside a global monitor**, which stalled a following caller: two
consecutive four-case captures hung before the first turn with the question sitting unsent in the
composer. Reverted, not fixed. The retrieval win has to come from running the per-library queries
concurrently and deterministically, not from caching handles.
