# Blobs SpeedGovernor — design spec

Status: **approved by the owner for implementation** (2026-10-26 — "the goal now moves to implementing,
not the brief"). Wave A executes on `feat/speed-governor-wave-a`, based on `feat/offline-research-engine`
at `81af8c7`, in `D:/rimbara/blobs-wave-a`; see `plans/2026-10-26-wave-a-tree-baseline.md`.
Scope owner: product owner (nonomnomon) · authored by the coding agent
Two §11 items remain unanswered and are treated as agent-adopted defaults, revocable by the owner:
the bounded 3-source build as the product target, and which model writes the 2 GB digest.

## 0. Read this first (30 seconds)

**What the user feels today.** Streaming is real — tokens arrive from native code one
at a time — but nothing appears until the model has read the whole prompt. The shelf
test suite records **53–115 seconds of empty screen** before the first word, then the
answer types normally for ~20 s. (`goal.md:67-69` is why this is stated from artifacts,
not impression: `corpus/evaluation/results/2026-10-25-8g-research/eightg-1-ui-report.json`,
`…/2026-10-25-2gb-avd/run6-ui-report.json`.) **That wait is inside the app, not an
artifact of how it is measured:** the same record carries the app's own internal
timer and the UI observation side by side — `first_visible_text_elapsed_ms` 109 731
against `first_observed_work_text_ms` 109 975 for `compare-disc-rim-brakes`, 0.2 %
apart. What is still unresolved is *why the branch path costs ~8× more per prompt
token than the release path* for the same model (§7.3).

**Why, in one line.** Waiting time after retrieval on a full shelf fits `≈ 12.6 s +
15.3 ms × prompt characters` (§7.3) — and the same prompt on the direct-engine test
path costs about a fifth of that per character, so a large part of the wait belongs to
the **app path**, not to the model. It is not memory: the same case costs 75.0 s on a
2 GB device and 74.0 s on an 8 GB one. It is not the new build flags either.

**The uncomfortable consequence.** Two of them, and they are different work.
(a) The prompt has **never had a global budget in code** — `MAX_PROMPT_CHARACTERS` exists only as a
sentence in `docs/architecture.md:251`; `withContextBudget` (`inference/ResearchPrompt.kt:30-36`) is a
pass-through that applies none — so a shelf answer can carry up to ~16 000 characters of question plus
passages; in the shelf suite's own numbers each passage-character costs ~15 ms before
the first word, so 6 sources ≈ 74 s, 3 ≈ 43 s, 1 ≈ 28 s. (b) Even with **zero**
passages that suite waits ~12.6 s — five times the §2 target — so a floor exists that
no amount of prompt-trimming removes; that floor is slices 1 and 3 (per-turn context
rebuild, per-turn load) plus the unattributed ~8× spread between the three harnesses
of §7.3. Fewer passages is the fast option and more passages is the slow one, *at
least within the shelf measurements* — and note the records cannot settle the rest
today: the shelf artifacts store no `prompt_characters` (§7.3, §9.1), which is why
slice 0 is a prerequisite rather than a refinement.

**What this design does about it, in priority order.**
1. Bound the prompt again, per plan and per scope size (§6.13, slice 1a) — biggest win.
2. Stop rebuilding the KV context every turn and actually reuse the system prefix
   (§6.5, slice 1) — measured dead today: turn 2 costs 5 410 ms vs turn 1 5 012 ms.
3. Move cold work out of the turn: prefault the model, load it, prime the prefix and
   open the libraries at idle or right after install (§6.4) — cold load alone is
   2.4–11.2 s paid at the send tap.
4. Show sources while the answer is still coming (§6.7) — evidence exists at **63 ms**,
   the UI hides it until the turn ends, and the contract already allows showing it.
5. Then the governor itself: allowed-core-based threads, RAM and thermal tiers, an ADPF
   hint session, Settings Automatic / Saver / Turbo, GPU behind the existing variant
   with no claims (§6.1–6.3, §6.8–6.11).

**Two decisions that block the rest.** (a) Which profile is the product target for
speed — the one-library profile where §2's numbers live, or the shelf where §7.3 says
2.5 s is unreachable at any sane passage count? (b) The new 0.5 B model exhausts its
whole token budget before closing its answer, so it is fast but not a writer — accept
digest-style answers on 2 GB devices, shorten its prompt, or drop it as a default?
Everything below is the detail behind those two questions.

## 1. Problem

A turn is genuinely token-streamed, but the reader waits a long time before the
first character appears, and the wait is mostly **cold work that runs inside the
turn**, not model decoding.

Measured baseline (emulator `sdk_gphone64_x86_64`, Android 16 API 36, 4 CPUs,
airplane mode; every number is emulator-only and supports no phone claim):

| Cost item | Measured | Source |
| --- | --- | --- |
| Retrieval, warm, one library | 17–41 ms | `corpus/evaluation/results/follow-up-journey-journey-current-001.json:61` |
| Retrieval, first turn after install | 1323 / 2211 / 2794 ms | `…journey-current-003.json:61`, `…journey-current-002.json:61`, `…lfm2-journey-001.json:61` |
| Retrieval, 7-library shelf | 3.7 s min / 11.6 s median / 16.1 s max | `work/demo-30s-blobs.md:15` — **untracked local note** (`/work/` is gitignored); corroborated by tracked-in-worktree shelf runs: `retrieval_elapsed_ms` 3 711 – 16 113 ms across 9 libraries (`2026-10-25-8g-research/eightg-1-ui-report.json`) | slice 9 replaces the note with a recorded artifact from the scaling suite |
| Native model load (`first_token_ms` of the first call) | 2412 ms (LFM2) / 3029 ms and 11 207 ms (Qwen) | `app-prompt-cost-cost-lfm2-001.json:20`, `…cost-app-003.json:20`, `…cost-app-004.json:20` |
| First visible text, warm turns | 5314–7331 ms | `follow-up-journey-journey-revert-001.json:61`, `journey-current-001.json:61` |
| First visible text, cold first turn | 8921–10127 ms | `journey-current-003.json:61`, `journey-current-002.json:61` |
| End to end | 11.3–15.7 s | `CHANGELOG.md:138` |
| Grammar-constrained decode | Qwen 92.3 ms/token · LFM2 50.3 ms/token | `cost-app-004.json:41`, `cost-lfm2-001.json:41` |
| Process PSS after a turn | Qwen 1.89 GiB · LFM2 1.30 GiB | `docs/architecture.md:566-583` |
| Evidence ready for display | 63 ms | `answer-qwen-pkg-002.json` (`evidence_ready_state_ms`) |
| Over-threading | 8 threads on a 4-CPU target: 442 s for 75 tokens vs 4.8 s at 4 threads | `docs/engineering-roadmap.md:305` |
| Heavy model on 2 GB | 82.7–86.3 s per turn, all turns STOPPED | `corpus/evaluation/results/performance/avd50gb-android16-minicpm5-20261022d.json` |

Three structural facts drive the design:

1. **Nothing is warmed.** A case-insensitive search for
   `warm|preload|prime|idle|speculat` across `app/src/main/kotlin` returns one
   unrelated string (`feature/library/ImportScreen.kt:118`). The GGUF is loaded
   lazily *inside* the first turn: `ResearchWork.kt:91` → `LocalResearchEngine.kt:141`
   → `local_model_runtime.cpp:206` → `load_model`. The user's send tap is the
   trigger for a 2.4–11.2 s load.
2. **The KV prefix cache never gets a hit.** `context_capacity()`
   (`local_model_runtime.cpp:70-81`) doubles between the min and max bound from
   `prompt_tokens + output_tokens`, and `KvCache::prepare()` (`kv_cache.cpp:38-43`)
   calls `reset()` whenever `capacity` **or `threads`** differ from the live
   context. `reuse_prefix()` (`local_model_runtime.cpp:248`) therefore runs
   against a freshly allocated context. Measured: turn 2 `first_token_ms` 5410 vs
   turn 1 5012 with a byte-identical system prompt. The intent is documented, not
   invented here: `ResearchPrompt.kt:16-19` states the system prompt "is
   byte-stable so every turn reuses the same KV prefix", and
   `InferenceContextPolicy.kt:4-8` already pins one fixed capacity
   (`LOCAL_RESEARCH_CONTEXT_TOKENS = 4096`, with every device tier an alias of it)
   — so the Kotlin layer already wants a single stable context and it is the
   native bucket that disagrees with it.
3. **Retrieval re-opens the world every turn.** `LibrarySqlite.open()`
   (`data/library/LibrarySqlite.kt:10-13`) calls `BundledSQLiteDriver().open()`
   plus two `PRAGMA` prepares per call, then `version()` and `hasDocumentNames()`
   each prepare again, and `LibraryStore.retrieve()` fans out with
   `enabledLibraries.flatMap { retrieveFromLibrary(...) }`
   (`data/library/LibraryStore.kt:71-92`) — sequential, no parallelism, no cached
   connection or statement.

### 1.1 The full-shelf reality, which the single-library baseline hides

Everything above comes from journeys that installed **one** library. The
comparison-suite runs use a normally installed shelf, and they are an order of
magnitude worse:

| Profile | cores | RAM | model | retrieval | first visible text | end to end |
| --- | --- | --- | --- | --- | --- | --- |
| `blobs-e2e` (2 GB AVD) | 4 | 2 G | LFM2 1.2B | 1316 / 1712 ms | **53 236 – 76 343 ms** | 79 769 – 86 695 ms |
| `blobs-research` (8 GB AVD) | 4 | 8 G | LFM2 1.2B | 5 691 – 14 258 ms | **59 272 – 114 804 ms** | 82 537 – 133 737 ms |
| Same model, one-library journey | 4 | 2 G | LFM2 1.2B | 176 – 518 ms | 3 360 – 8 251 ms | 6 402 – 11 338 ms |

Sources: `.worktrees/offline-research-engine/corpus/evaluation/results/2026-10-25-2gb-avd/run6-ui-report.json:105,201`,
`…/2026-10-25-8g-research/eightg-1-ui-report.json` (fields `retrieval_elapsed_ms`,
`first_visible_text_elapsed_ms`, `end_to_end_elapsed_ms`, and
`first_observed_work_text_ms` which agrees with it to within 300 ms); core counts
from `hw.cpu.ncore=4` in `D:\AndroidAVDs\blobs-research.avd\config.ini` and
`C:\Users\hi\.android\avd\blobs-e2e.avd\config.ini`.

Three consequences the design has to respect:

1. **This is the first in-repo evidence of the ~99-second turn.**
   `docs/engineering-roadmap.md:308-310` says no latency claim about "the
   99-second turns a user reported" is in evidence. `eightg-1` records 82,5–133,7 s
   end to end, so the report is now reproducible on a shelf profile instead of
   being an anecdote.
2. **The gap is between retrieval finishing and the first character, not in
   decode.** For `compare-disc-rim-brakes`: retrieval 14,3 s, first text 109,7 s,
   end to end 130,9 s. So ~95 s is spent after the passages are known and before
   anything is shown, while the 21 s that follows is normal decoding
   (≈66 ms/token at the 320-token bound). Prefill of a ≤6000-character prompt on
   4 threads cannot honestly account for 95 s, and **both profiles are 4-core**, so
   the measured 8-thread collapse is not the cause here either.
3. **Whatever that 95 s is, it is the single largest latency item in the product**
   — 10 to 20 times the size of everything else in §1. A booster that shaves 1–2 s
   off prefill is noise next to it. Slice 0 therefore has to run on a shelf
   profile, not only the journey profile, or it will measure the wrong thing.
4. **The slow runs are attributable to a commit, and the build is not the cause.**
   They come from a clean tree at `81af8c7` on `feat/offline-research-engine`
   (`git worktree list`), whose committed flags match `release/0.4.0` HEAD — see
   §10 risk 9 H0 for the specific lines. Same flags, same 4 cores, same model,
   ~10× the wait: the difference that correlates is the installed shelf and the
   RAM profile, not the binary. The reports also omit
   `installed_apk_sha256`, so this had to be reconstructed from git state, which
   is exactly the pairing failure §9.1 is meant to prevent.

## 2. Objective

Make a turn feel fast, and be measurably faster, by removing cold work from the
critical path, making the existing caches actually work, and adding a device
adaptive performance governor with an honest, legal boost path. Targets, to be
proven on the existing emulator profile before any device claim:

**Scope of these targets, stated plainly:** the three numbers below are defined
on the **narrow profile** (one library in scope, warm caches, 4 cores) — the same
profile every journey artifact in §1 was produced on. On a full installed shelf
the measured reality is 53–115 s to first text (§1.1), and this spec does **not**
claim those three numbers transfer there. Shelf gates get set only after slice 0
attributes the ~95 s gap; proposing them now would be a guess wearing a target.

- Evidence visible on screen: **≤ 1.0 s** after submit (from a 63 ms data point
  that is currently hidden until the turn finishes).
- First visible text: **≤ 3.0 s** on a cold first turn, **≤ 2.5 s** warm (from
  8.9–10.1 s and 5.3–7.3 s). Per §7 this warm figure is *conditional* on prefix
  reuse plus `GGML_LLAMAFILE` both delivering; if either falls short the recorded
  gate becomes **≤ 4.0 s warm**, which is still a 1.3–2.8× improvement and is
  written into the record instead of quietly missed.
- End to end: **≤ 8 s** with a shortened answer bound (note §7 item 3: the bound
  removes the tail, not the median).
- No regression in grounding disclosure, cancellation safety, or turn provenance.

**Derived shelf goal, from §7.3's law rather than from hope.** With a 12.6 s
intercept and ~15.3 ms per prompt character, the three targets above are not
reachable on a full shelf at any passage count the product would accept: the
intercept alone is five times the 2.5 s figure. What the arithmetic does support
is a shelf gate of **first text ≤ 20 s** at 3 sources and 600–1 000 characters of
passages, together with **sources on screen ≤ 1 s** (which §6.7 and §7.1 make
independent of generation entirely) — and it says the intercept must be attacked by
slices 1 and 1a before any sub-5-s first-text claim is written anywhere. These
shelf numbers are proposals for §11, not targets yet.

One more number falls out of the comparison, and it is evidence for H5: a
1 590-character prompt reaches first token in 4.3 s on the direct-engine path, while
a *smaller* 1 222-character shelf case waits 31.3 s after retrieval on the UI path —
roughly 25 s apart at comparable prompt size, which is not a prefill difference.
That gap sits outside `generate` or outside the model call entirely: Room turn begin,
`Evidence.encode`, `session.update`, the 500 ms checkpoint path, or per-case harness
setup. Slice 0 must time the Kotlin side of the turn and run one identical prompt
through both paths, or the design keeps optimizing a 15 ms/char figure that is not
even the largest term.

## 3. Decisions taken by the owner (2026-10-25)

| Question | Decision |
| --- | --- |
| Shape of the booster in the product | Exposed in Settings, adaptive underneath (Automatic default) |
| Device priority | Adaptive per device, not flagship-first |
| What may be traded away | Answer length may be shortened |
| GPU / NPU path | Experimental, behind a build variant, no numbers claimed |
| Answer bound 320 → 200 in **every** mode | **Approved** (2026-10-25, second round) |
| Make LFM2 the default model | **Out of scope.** The owner instead added a fourth model, `qwen25-0b5-q4km` (491 400 032 bytes, `minimum_ram_mb` 2048), on the `feat/offline-research-engine` worktree |
| Status of this document | The owner treats `docs/superpowers/` as internal documentation: the durable outcomes must be carried into `docs/architecture.md` / `docs/engineering-roadmap.md` (§9.1), not left living here |

Still open after that round: sustained model residency under Turbo, which the
owner answered "idk" — a delegation, not a refusal. To keep implementation
unblocked, this spec adopts an explicit default: residency is allowed only under
the §6.2 headroom rule, is never enabled by `Saver`, and releases after 5 minutes
of foreground idle. If the owner later rejects it, the change is confined to the
`residentModel` rule and the load-time saving it buys.

The 4.0 s fallback (§2, §7) is likewise adopted as the default gate for the warm
narrow profile unless the owner objects.

## 4. Correction to the brief

The original objective named `notifyWorkloadSpike` as part of the ADPF surface.
The NDK actually installed here (`27.1.12297006`, the version pinned by
`android/app/build.gradle.kts:14`, with `compileSdk = 37` at `:12`) exposes only:

```
APerformanceHint_getManager                     (__ANDROID_API_T__ = 33)
APerformanceHint_createSession(manager, tids, n, initialTargetWorkDurationNanos)  (33)
APerformanceHint_getPreferredUpdateRateNanos    (33)
APerformanceHint_updateTargetWorkDuration       (33)
APerformanceHint_reportActualWorkDuration       (33)
APerformanceHint_closeSession                   (33)
APerformanceHint_setThreads                     (__ANDROID_API_U__ = 34)
APerformanceHint_setPreferPowerEfficiency       (__ANDROID_API_V__ = 36)
APerformanceHint_reportActualWorkDuration2 + AWorkDuration (36)
```

There is **no** spike/increase hint, **no** `setMode`, and **no** core-mask or
affinity query in this header. `madvise(MADV_WILLNEED)` is also not declared in
the Bionic public headers here (`MADV_WILLNEED` appears only under
`asm-generic/`), while `posix_fadvise` with `POSIX_FADV_WILLNEED` is documented
in `fcntl.h`. The design therefore uses: a hint session with a target duration
plus per-turn reporting and `setThreads`, `posix_fadvise` for prefaulting, and
`llama_set_n_threads` for runtime thread changes. `sched_setaffinity` is
explicitly excluded: an app's threads are governed by cpuset groups, so manual
pinning is not a legitimate or portable path.

A second premise in the brief was also checked and is wrong. `minimum_ram_mb` is
**not** a no-op: `android/models.present.json` sets 6144 (qwen35-2b-q4km), 8192
(phi4-mini-q4km) and 4096 (lfm2-1b2-q4km), `tools/build_catalog.py` carries it
into the bundled asset catalog
(`app/src/main/assets/package-catalog.json:31,60,89`), and
`packageFitsMemory` (`PackageCatalog.kt:103-104`) reads it through
`CatalogCodec` (`:57`). The `?: 0` in that expression is only the fallback for an
asset that omits `compatibility` entirely. What is actually broken is one line
further up: `recommendedModel` ends with

```kotlin
?: availableModels.minBy { it.manifest.bytes }   // PackageCatalog.kt:100
```

so when no model passes `packageFitsStorage && packageFitsMemory`, the catalog
still recommends the smallest model **without re-checking the memory gate**. The
real work item is closing that bypass (§6.10), not filling in values that are
already filled.

## 5. Architecture

New invariant: **device performance policy**. It is placed in `inference/`
because `docs/rules/architecture-and-ownership.md` gives `inference/` the Kotlin
JNI boundary and the prompt/answer contract, and gives `app/src/main/cpp` model
lifecycle, `KvCache`, sampling, and token decode. No new top-level package is
created.

```
feature/ui ─┐                       (reads plan + mode only; never touches JNI)
            ├─ AppViewModel ─ WorkController
data/work ──┘        │
                     ├─ inference/DeviceProfile      (raw device facts)
                     ├─ inference/SpeedPlan          (decided configuration, pure data)
                     ├─ inference/SpeedGovernor      (resolve, session lifecycle, warm schedule)
                     └─ engine/LocalResearchEngine   (consumes SpeedPlan for limits + threads)
inference/NativeBridge ── cpp/performance_adpf.cpp   (ADPF session over TIDs)
                    └──── cpp/local_model_runtime.cpp (prepare/prefault entry points)
```

Ownership additions to record in `docs/rules/architecture-and-ownership.md` when
this ships:

| Area | Owns | Does not own |
| --- | --- | --- |
| `inference/` performance policy files | Device facts read, `SpeedPlan` decision, `PerformanceMode` mapping, governor lifecycle | Threads created by native, actual ADPF handles, UI rendering |
| `app/src/main/cpp` | ADPF session handle and TID registration, model prefetch, `llama_set_n_threads`, context capacity stability | Question selection, prompt content, product mode names |
| `data/work/WorkController` | When warming is allowed to run relative to an active turn | Policy values, native handles |

Dependency direction stays downward: `engine → inference → cpp`; `feature/ui →
AppViewModel → WorkController`. UI reads a rendered description of the plan; it
does not compute one. Split of responsibility for warming, so the two owners do
not overlap: `WorkController` decides **when** warming may run (foreground, no
busy turn, no transfer), `SpeedGovernor` decides **what** it runs and at what
cost, and native owns the actual load/prefault calls.

## 6. Components

### 6.1 `DeviceProfile` (Kotlin, `inference/DeviceProfile.kt`)

Read-once snapshot, injected-friendly for tests:

- `allowedCores: Int` — `CPU_COUNT(sched_getaffinity)` of the calling process,
  obtained natively (`NativeBridge.allowedCores()`), **replacing**
  `Runtime.getRuntime().availableProcessors()` in `NativeBridge.kt:67`. This is
  the fix for the measured over-threading collapse: a 4-core foreground cpuset
  must never produce 8 threads.
  **This reverses a recorded decision.** `docs/architecture.md:321-322` currently
  states "Threads are auto-detected from the device and clamped to 8, so an
  eight-core phone is not forced onto a four-thread policy." That rationale has
  never been measured: the only thread data in the repository is the 4-CPU profile
  where the clamp produced 442 s for 75 tokens (`docs/engineering-roadmap.md:305`),
  and `availableProcessors()` counts logical CPUs the scheduler may not let the
  process use, so it can overshoot the cores a turn can actually occupy. The slice
  that lands this **must amend `docs/architecture.md:321-322` in the same change**;
  leaving the old sentence in place would make the architecture doc describe a
  policy the code no longer implements.
- `totalMemoryBytes`, `appMemoryClassMb` (`ActivityManager`), free storage.
- `apiLevel`, `adpfSupported` (`APerformanceHint_getManager() != nullptr`).
- `thermalSupported` (`PowerManager.getCurrentThermalStatus`, API 29+; absent on
  minSdk 28 → treated as unknown, never as cool).
- `modelBytes` of the selected model, `libraryCount` of enabled libraries.

### 6.2 `SpeedPlan` (Kotlin, `inference/SpeedPlan.kt`)

Pure data: `threads: Int`, `answerTokenLimit: Int`, `contextCapacity: Int`,
`residentModel: Boolean`, `allowWarmup: Boolean`, `adpfEnabled: Boolean`,
`targetCycleNanos: Long`.

`SpeedGovernor.plan(profile, mode, thermal)` resolves it. Rules, in order, so
behaviour is engine-ruled and not prose-argued:

1. `threads`: `Saver` = `min(allowedCores, 2)`, `Automatic` = `min(allowedCores, 4)`,
   `Turbo` = **also 4 on every profile measured so far**, raised above 4 only when
   a wider device is actually measured. Never above `allowedCores` in any mode.
   Rationale is measured, not assumed: 130 ms/token at 1 thread, 79 ms at 2,
   64 ms at 4, and a collapse at 8 on a 4-core target
   (`docs/engineering-roadmap.md:305-307`). Both AVDs in this repository are
   configured `hw.cpu.ncore=4`, so `allowedCores` and the 4-thread optimum
   coincide here and **no configuration above 4 threads has ever been measured
   anywhere** — the clamp to 8 recorded in `docs/architecture.md:321-322` is an
   unmeasured assumption about wide phones, not a proven gain. `allowedCores` is
   therefore a ceiling that prevents over-subscription, not a target to fill.
2. `contextCapacity = kMaximumContextTokens` (4096) always — see §6.5.
3. `answerTokenLimit`: 200 (Automatic/Turbo), 160 (Saver). Today
   `MAX_RESEARCH_OUTPUT_TOKENS = 320` (`LocalResearchEngine.kt:16`).
4. `residentModel`/`allowWarmup` need real headroom, computed from the measured
   post-turn PSS rather than a guessed tier: residency is allowed only when
   `totalMemoryBytes − predictedPssBytes ≥ 2 GiB`, where `predictedPssBytes` is
   the measured 1.30 GiB for LFM2-1.2B and 1.89 GiB for Qwen3.5-2B (§1), and only
   when `totalMemoryBytes ≥ asset.compatibility.minimumRamMb` from the catalog
   (§6.10) — the governor consumes the published floor instead of inventing a
   second one. Warming additionally requires thermal ≤ `THERMAL_STATUS_LIGHT`
   (§8 covers the mid-turn case). `Turbo` on a device that fails either check
   falls back to `Automatic` behaviour and the Settings row says so.
5. `targetCycleNanos` per mode: 100 ms (Turbo), 200 ms (Automatic), 400 ms
   (Saver). These bracket the measured 50.3–92.3 ms/token, so the hint asks the
   governor to keep a token cycle inside what this runtime has actually achieved,
   instead of an invented frame budget.
6. `adpfEnabled` only when `adpfSupported`.

`PerformanceMode` (`AUTOMATIC`, `SAVER`, `TURBO`) persists in
`PreferencesStore` exactly like `themeMode`: a `stringPreferencesKey`, a
`Flow<PerformanceMode>` with a safe default, and one setter
(`data/preferences/PreferencesStore.kt:49-56` and the key at `:59` is the pattern).

`predictedPssBytes` is deliberately a measured table, not a formula: 1.30 GiB
(LFM2-1.2B) and 1.89 GiB (Qwen3.5-2B) are recorded, phi4-mini is not measured and
therefore cannot be a residency candidate until it is.

The fourth model changes the small-device tier, and it is verified — just not in
this branch: `.worktrees/offline-research-engine/android/models.lock.json` adds
`qwen25-0b5-q4km` at 491 400 032 bytes with `minimum_ram_mb` 2048 and
`recommended_up_to_memory_mb` 2048, i.e. explicitly the 2 GB class. Its own record
(`models.present.json` description) is that on the 2 GB profile it reached first
visible text in 40,5 s against 53,2–76,5 s for LFM2 on the same profile and
question — **but** it "spends the whole 320-token output budget before closing its
response document, so its turns fall back to cited digest lines instead of model
prose". Two design consequences: it is the residency-cheap option for small
devices, and it is a direct argument for §6.6's shorter bound, because a model that
cannot finish inside 320 tokens turns a speed choice into a quality loss. The
200-token bound must therefore be re-checked per model, not applied blindly: for
`qwen25-0b5` the recorded behaviour says the bound is already the binding
constraint, so the honest options there are a shorter prompt, a different writer,
or accepting digest-line answers.

### 6.3 `SpeedGovernor` (Kotlin, `inference/SpeedGovernor.kt`)

- `beginTurn(plan)` → opens the ADPF session over the calling thread's TID with
  `initialTargetWorkDurationNanos` from the plan, and returns a handle.
- `reportCycle(nanos)` → `APerformanceHint_reportActualWorkDuration`, rate-limited
  by `APerformanceHint_getPreferredUpdateRateNanos`.
- `adoptWorkerThreads()` → after the first compute, native code diffs
  `/proc/self/task` and calls `APerformanceHint_setThreads` with the ggml thread
  pool TIDs. `setThreads` **replaces** the session's thread set, so the call must
  pass the calling thread *and* the discovered pool threads together; a call with
  only the pool TIDs would silently drop the thread that runs decode. Fallback:
  session covers the calling thread only. The ggml pool is created inside
  llama.cpp (`ggml-cpu.cpp:684` exposes `ggml_threadpool_new`), so no
  vendored-source patch is attempted.
- `endTurn()` → closes the session; also closed on background transition.
- `warm(profile, plan, modelPath, libraries)` → runs only while no turn is busy.
- Thermal listener registered by the caller (`BlobsApplication`/`WorkController`)
  feeds `thermal` back in; a change at or above `MODERATE` re-plans mid-turn via
  `llama_set_n_threads`, which does **not** require a new context.

### 6.4 Warm path (`prefault → load → prefix prime → libraries`)

Trigger: app foreground, `WorkState.ready`, plan allows warming, and the
exclusivity predicate `!work.busy && !work.transferActive && !work.downloading`
(those three fields exist on `WorkState`) is true. No transfer or download may
overlap a prefault: they contend for the same flash bandwidth warming is trying
to exploit.
Sequence, each step cancellable the moment a real turn starts:

0. **Prime at the moment the file is hottest.** `PackageInstaller.install`
   activates a package by `staging.renameTo(target)` (`data/packages/PackageInstaller.kt:214`,
   with the `activated` flag at `:216`). Right after a model or library finishes
   writing, its bytes are already in the page cache — this is the cheapest
   possible priming point, and the first question a user asks after installing a
   model is exactly the 8.9–10.1 s cold turn. A successful activation therefore
   schedules the same steps 1–4 once, guarded by the exclusivity predicate below,
   instead of waiting for an idle window.
1. `posix_fadvise(fd, 0, 0, POSIX_FADV_WILLNEED)` on the GGUF. Most of the
   2.4–11.2 s "load" is page-cache misses on a 731 MB–1.28 GB file.
2. `NativeBridge.prepareModel(path)` → new JNI entry that calls the existing
   `load_model()` (`local_model_runtime.cpp:117-161`) without generating. Turns
   2+ already hit that cache; this moves the miss off the critical path.
3. Prefix prime: one `generate` with `limit = 1` over the byte-stable system
   prompt so `remember_prompt` stores a reusable prefix, using the fixed
   capacity from §6.5. The harness already proves this shape
   (`GenerationCostTest.kt:105`, `measure("warmup", …, tokenLimit = 1)`).
   Integrity rule for that call, because it is the only completion this design
   adds outside a question: its output is **discarded**. It must not reach
   `WorkState.text`, must not be persisted, must not be shown as prose, must not
   create or alter a turn, and must be cancellable by `NativeBridge.stop()` the
   same way a real turn is. `goal.md:13-16` fixes the turn path as one retrieval
   plus one completion **per question**, and that stays literally true — the
   priming call belongs to no question. `docs/bounty-contract.md:59` ("No new body
   is invented") is the rule this guards against breaking.
4. Library warm: for each enabled library, open once through a small
   `LibrarySqlite` connection cache (LRU by `PackageEntity.key`, sized to the
   enabled set, evicted on trim) and run one cheap `PRAGMA`/`SELECT` so the
   driver, header page, and FTS index are resident. This is what turns
   1323–2794 ms into the 176–335 ms steady state *before* the first question
   instead of after it.

Cost stated plainly: warming raises steady PSS toward the measured 1.30 GiB
(LFM2) / 1.89 GiB (Qwen). `Saver` never warms the model. `onTrimMemory` keeps releasing the model (`BlobsApplication.kt:17-24` →
   `ResearchWork.kt:324,343-344`), but the governor re-schedules warming when the
   app returns to foreground idle — today a trim is permanent until the next send
   tap.

### 6.5 KV and thread stability (native)

- `context_capacity()` stops choosing a bucket: with a fixed
  `capacity = kMaximumContextTokens`, it becomes a fit check that still throws
  `CONTEXT_FULL` when `prompt + output > capacity`. `prepare()` then keeps one
  live context across turns, so `reuse_prefix()` actually returns a nonzero
  prompt start. This is inside the recorded contract, not against it:
  `docs/architecture.md:336` already names the pipeline
  `prompt tokenization -> optional exact-prefix KV reuse -> decode -> cleanup`,
  and `:346-348` states reuse "is valid only for byte- and token-identical prompt
  prefixes. It is a performance optimization, never a" correctness guarantee. The
  change only makes that already-specified optional optimization reachable.
- Thread changes go through `llama_set_n_threads(ctx, n_threads,
  n_threads_batch)` (`llama.h:1098`) instead of re-`prepare()`; `threads_` stays
  equal to what the context was created with so the reset path is not taken.
- `GGML_LLAMAFILE ON` in `app/src/main/cpp/CMakeLists.txt:45` (currently forced
  `OFF`). `ggml/src/ggml-cpu/CMakeLists.txt:84-85` defines `GGML_USE_LLAMAFILE`
  and the kernels are already vendored under `ggml-cpu/llamafile/`. This targets
  prompt processing specifically, i.e. prefill, and must be re-measured with the
  citation probe because it changes numerics.

### 6.6 Answer bound

`MAX_RESEARCH_OUTPUT_TOKENS` becomes plan-supplied (200 default, 160 Saver)
instead of a file constant, still enforced as a hard native limit
(`blobs.cpp:67` validates `limit` 1..512). The 32 KiB byte bound stays.
Brevity is enforced by the bound, not by asking the model nicely.

### 6.7 Perceived latency (UI)

- Show sources while the turn is running. `TurnCard.kt:109-116` gates
  `TurnSources` behind `if (!running)` and `:90-92` gates `GroundingChain`
  likewise, while evidence is already in `work.evidence` (rendered into the card
  at `ChatScreen.kt:204`, counted at `TurnCard.kt:88`). `docs/bounty-contract.md:115-118`
  permits this exactly: evidence **may** be shown as reading material with a
  `pending` state, and **must not** be rendered as model prose or auto-resubmit.
  Implementation: render `TurnSources` when running with an explicit
  `pending` chip; keep `GroundingChain` (a claim about the answer) hidden until
  the answer exists.
- Optimistic question bubble. Today nothing echoes the question until
  `conversations.begin` inserts the RUNNING row (`ResearchWork.kt:78`), so the
  first frame of a first-ever turn is the `HomeIntro` empty state
  (`ChatScreen.kt:162`). Render the reserved turn's question from `WorkState`
  while the row is absent.
- Keep the 50 ms render throttle (`LocalResearchEngine.kt:17`) as is; it is not
  the problem.

### 6.8 Settings entry

The Settings surface follows the pattern that actually exists, rather than
inventing one. `SettingsScreen` is `ScrollPage { Column { SectionLabel(...) +
MenuRow(title, summary) { model.navigate(Page.X) } } }`
(`feature/settings/SettingsScreens.kt:66-118`), with `AppearanceScreen` (`:122`)
as the model for a detail page. So the change is: one `MenuRow` under
`R.string.settings_experience`, summary = the mode the user chose, navigating to a
new `Page.PERFORMANCE` screen that holds the three choices (Automatic / Saver /
Turbo) plus one status block showing what the governor really resolved — `threads`,
answer bound, context capacity, model residency yes/no, ADPF supported yes/no, and
thermal at last read. New strings go in `res/values/product.xml` in English per the
language rule. The status block is what makes the feature honest: it reports the
decision the device forced, not a promised speed. A demotion (e.g. Turbo refusing
to residency on a small device) is visible there rather than silently ignored.

Naming deviation, deliberate: the owner's wording for the middle mode was
"Hemat". `docs/rules/language.md` forbids Indonesian strings in product code and
new default UI copy, so the shipped label is **Saver** and the spec keeps
`SAVER` as the persisted enum value. Same meaning, English surface.

### 6.9 GPU / NPU

`snapdragonBackends` (`build.gradle.kts:16-17,36-38`, `cpp/CMakeLists.txt:33-38`)
stays the only place `GGML_OPENCL`/`GGML_HEXAGON` are built; `n_gpu_layers`
remains 0 in the default build (`local_model_runtime.cpp:131-135`), and
`ggml-vulkan` is left unbuilt. No deployment target, no benchmark, and no claim
is attached to this path in this change; it is recorded as the next experiment
once a phone-class device exists.

### 6.10 Catalog RAM floor and its bypass

The published floor stays the single source of truth; nothing in the governor
invents a parallel constant. Two changes:

- `recommendedModel` must not hand back a model that failed the gate. Today
  `PackageCatalog.kt:96-100` falls through to `availableModels.minBy {
  it.manifest.bytes }` when nothing passes `packageFitsStorage &&
  packageFitsMemory`. Replacement behaviour: return `null`, and let the caller
  route the user to the model page — the same outcome `AppViewModel.kt:232-234`
  already produces when no model is selectable. This is a signature change on a
  product-facing function, so it is called out here rather than buried in a
  slice.
- The governor reads `DownloadablePackage.compatibility.minimumRamMb`
  (`DownloadablePackage.kt:19,132`) as the residency precondition it already
  needs (§6.2 rule 4). For the three pinned models that floor is 4096/6144/8192
  MB, which is consistent with the measured post-turn PSS of 1.30/1.89 GiB: an
  LFM2 residency on a 4 GB device leaves ~2.7 GiB, and Qwen on a 6 GB device
  leaves ~4.1 GiB. The stricter of the two rules (published floor vs the
  2 GiB-headroom residency math) wins.

### 6.11 Retrieval fan-out and incremental evidence display (`data/library`)

Bounded parallelism exists only as insurance for the *very first turn*, before
warming has run. Three invariants bind it:

- **One retrieval pass per question stays true** (`goal.md:53-54`). The fan-out is
  intra-pass parallelism over `enabledLibraries` (`LibraryStore.kt:71-92`), not a
  second search, not a reranker, and not a query planner (the owner rejected
  planning on 2026-10-25).
- **A connection is not shared across threads.** `androidx.sqlite` connections are
  not thread-safe, so the §6.4 cache must hand each concurrent task one connection
  it owns for the duration, or fall back to sequential execution. A test must pin
  this, because it is the kind of defect that only appears on a 7-library shelf.
- **Displayed evidence belongs to this turn and may only grow or re-rank.**
  `retrieve()` needs every candidate before `selectEvidence()` can apply the global
  7-key ranking (`LibraryStore.kt:198-230`), so the model still consumes the final
  ranked set. A display-only callback (`onEvidenceProgress`) may show each
  library's best hit while the rest arrive — permitted by
  `docs/bounty-contract.md:115-118` as reading material with a `pending` state,
  never as model prose, never auto-resubmitted, and never a set that shrinks.

Concurrency is capped by the plan (≤ 4 library tasks), so a Turbo turn already
saturating cores does not also spawn seven SQLite readers.

### 6.12 Interfaces this design adds

Stated here so the implementation plan has one contract to work against, and so
each unit can be understood without reading its internals.

Kotlin, `inference/`:

```kotlin
internal data class DeviceProfile(
    val allowedCores: Int,           // from native, not availableProcessors()
    val totalMemoryBytes: Long, val appMemoryClassMb: Int,
    val apiLevel: Int, val adpfSupported: Boolean, val thermalSupported: Boolean,
    val modelBytes: Long, val libraryCount: Int
)

internal enum class PerformanceMode { AUTOMATIC, SAVER, TURBO }

internal data class SpeedPlan(
    val threads: Int, val answerTokenLimit: Int, val contextCapacity: Int,
    val residentModel: Boolean, val allowWarmup: Boolean,
    val adpfEnabled: Boolean, val targetCycleNanos: Long,
    val reason: String               // what decided this plan; shown in Settings
)

internal class SpeedGovernor {
    fun plan(profile: DeviceProfile, mode: PerformanceMode, thermal: ThermalLevel): SpeedPlan
    fun beginTurn(plan: SpeedPlan): TurnPerformance   // owns the ADPF session
    fun warm(modelPath: String, libraries: List<PackageEntity>, plan: SpeedPlan): Job
}
```

Native boundary, `NativeBridge` (three new `external` entries, no new Kotlin
policy inside them):

```kotlin
internal fun allowedCores(): Int                  // sched_getaffinity CPU_COUNT
internal fun prepareModel(path: ByteArray): Int   // load weights only; 0 = ok
internal fun prefaultModel(path: ByteArray): Int  // posix_fadvise WILLNEED
```

ADPF stays entirely in C++ (`cpp/performance_adpf.cpp`): `begin(tids, n,
target_ns)`, `report(session, actual_ns)`, `setThreads(session, tids, n)`,
`setPreferPowerEfficiency(session, enabled)`, `end(session)`, each null-guarded by
API level. Kotlin never sees a native handle.

Changed signatures, deliberately small:

- `LocalResearchEngine.run(request, onPhase, onEvidence, onAnswer)` keeps its
  shape; the token limit comes from the plan instead of the file constant, and a
  display-only partial callback is added with a no-op default so existing callers
  and tests are untouched.
- `LibraryStore.retrieve(packages, question, scope, maximumResults)` gains
  `concurrency: Int = 1` and `onPartial: (List<Evidence>) -> Unit = {}`, keeping
  every current call site valid.
- `PackageCatalog.recommendedModel(...)` returns `DownloadablePackage?` (§6.10) —
  the one genuinely breaking change, and callers already handle absence by
  routing to the model page.

### 6.13 Global prompt bound (`inference/ResearchPrompt.kt`)

Corrected 2026-10-26 against the code, because the first version of this section got the mechanism
wrong. **No global prompt bound has ever existed in code on any branch**, and neither did the constants
this section first cited:

```
$ git log --all --oneline -S"MAX_PROMPT_CHARACTERS"
f5eaab7 Blobs 0.4.0: …   # touches docs/architecture.md only
6e0d158 docs: …          # touches docs/architecture.md only
$ git grep -n "MAX_PROMPT_CHARACTERS\|MAX_SYSTEM_CHARACTERS" HEAD -- android/
(no output — the only hit repo-wide is docs/architecture.md:251)
$ grep -n "MAX_QUESTION_CHARACTERS" android/.../ResearchPrompt.kt        # both trees
13: const val MAX_QUESTION_CHARACTERS = 4_000
```

`docs/architecture.md:251` states that `ResearchPrompt.MAX_PROMPT_CHARACTERS` caps the built text at
6 000 characters. The code it describes was never written: `withContextBudget` (`:30-36`) is a
pass-through, and the only caps are per-field — `MAX_ANSWER_SOURCES = 6`,
`MAX_QUESTION_CHARACTERS = 4_000`, `MAX_SOURCE_EXCERPT_CHARACTERS = 2_000` — giving a theoretical
16 000 characters of question plus passages, bounded only implicitly by the 4 096-token context ceiling.
`release/0.4.0` differs from the feature branch in one number only, `MAX_ANSWER_SOURCES` 3 versus 6, so
this is not a regression introduced by going to six sources; it is an unimplemented documented
invariant. That changes the shape of the work: the task **adds** behaviour (and therefore needs the
drop rule, the floor rule and a test for each), and it must make `docs/architecture.md:251` true
instead of rewriting it. Given §7.3's measured ~15 ms per prompt character on the shelf path, a global
bound and making it plan-supplied remains the highest-leverage change in this design.
`goal.md:13-14` already names "a bounded prompt" as part of the fixed turn path, so
this is a contract fix rather than a tuning preference:

- `Automatic`/`Turbo` keep the assembled prompt under a stated character budget,
  and the budget **shrinks as the selected scope grows**, because that is where the
  per-character cost adds up fastest. **Wave A ships the budget only as one shared
  constant** (`MAX_PROMPT_CHARACTERS = 6_000`, applied by `ResearchPrompt.plan`);
  the scope-dependent and mode-dependent budgets are Wave C's `SpeedPlan` work.
- `Saver` uses a smaller budget and 3 sources — per §7.3 the single most effective
  speed lever available on a shelf. Deferred to Wave C, same reason.
- Trimming is deterministic (same input, same prompt) and recorded:
  `prompt_characters` already flows into `ResearchUsage`, so every record shows what
  the model was actually given.
- Panel/answer agreement: a passage listed in the turn's `evidence` must be in the
  prompt the model saw. If the budget cannot carry all of them, evidence is
  trimmed **with** the panel rather than after it, so a reader never clicks a
  source the answer never had. Implemented: `LocalResearchEngine` calls
  `ResearchPrompt.carriedSources` right after retrieval and publishes that set to
  `onEvidence`, so panel, prompt, gate and stored turn read one list.
- **Never trim to zero passages.** `docs/bounty-contract.md` (as edited
  2026-10-25) narrows the claim space: a completion for a turn that received no
  passage is offered only `unsourced` and its answer text cannot contain a marker
  at all. A budget that shrank a 6-source turn down to no passages therefore
  changes what the turn is allowed to *claim*, which is a product-visible
  consequence, not a performance detail.
  **Implemented differently than first specified.** This section said "if the budget
  cannot carry at least one passage alongside the question, the turn must not run".
  Measured against the real caps that is wrong: `MAX_QUESTION_CHARACTERS = 4_000` plus
  one 2 000-character excerpt already exceeds a 6 000 budget, so the refusal would have
  converted slow answers into failed turns. What ships is a shedding order instead of a
  stop: retained history goes first (300+500 per pair), then the ranked tail, and one
  passage always survives when retrieval returned any. An empty passage set can now only
  come from a retrieval miss, which is the case `bounty-contract.md` already governs.

## 7. Latency budget (arithmetic, not aspiration)

The cost artifacts expose the exact structure. With `first_token_ms` measured
after the process-global model cache is warm, and
`elapsed_ms ≈ first_token_ms + tokens × ms_per_generated_token`, the recorded
runs reconcile to within their own rounding.

**Profile these six rows belong to:** `release/0.4.0` builds, one library
(`us-preparedness-guidance-2026-10.2`), 3 sources, 4 threads, 4-core emulator,
Qwen3.5-2B and LFM2-1.2B. They are the narrow-profile baseline; §7.3 shows the
`feat/offline-research-engine` shelf configuration costs roughly 8× more per
prompt token than these rows, so nothing below is transferable to that build.

| Artifact · run | model | first_token | tokens | ms/token | elapsed | check |
| --- | --- | --- | --- | --- | --- | --- |
| `cost-app-003` · `load` (20-char prompt) | Qwen | 3029 | 33 | 57.8 | 4938 | 3029 + 32×57.8 = 4879 |
| `cost-app-003` · `turn1-grammar` (1590 chars, 3 passages) | Qwen | 5012 | 79 | 63.1 | 9999 | 5012 + 78×63.1 = 9934 |
| `cost-app-003` · `turn2-grammar` (1569 chars) | Qwen | 5410 | 109 | 72.9 | 13356 | 5410 + 108×72.9 = 13283 |
| `cost-lfm2-001` · `load` | LFM2 | 2412 | 3 | 55.0 | 2577 | 2412 + 2×55 = 2522 |
| `cost-lfm2-001` · `turn1-grammar` | LFM2 | 4317 | 71 | 50.3 | 7890 | 4317 + 70×50.3 = 7838 |
| `cost-lfm2-001` · `turn2-grammar` | LFM2 | 3947 | 120 | 52.0 | 10188 | 3947 + 119×52 = 10135 |

Read that table as the budget, because it says something the journey numbers
hide:

1. **A warm turn is ~5 s of `first_token` before any answer text exists.** A
   20-char prompt costs 2.4–3.0 s including the load; a 1590-character prompt
   with 3 passages costs 4.3–5.4 s with the model already cached. So the work
   between "send" and "first character" is prefill plus setup, and it is the
   single largest warm-path item.
2. **Turn 2 is not faster than turn 1** (Qwen 5410 vs 5012; LFM2 3947 vs 4317 —
   within noise, and both prompts are the same size). The prefix cache is
   confirmed dead, matching §1.
3. **The 320 → 200 token cut does not move the median.** Recorded answers are
   71–120 tokens; the cap only bites the run-to-limit tail (1660–1790 character
   answers measured at 26.5–29.5 s, `docs/architecture.md:550-565`). It is
   insurance and worst-case bound, not a typical-turn win. This is stated so the
   owner is not sold a saving that is not there.
4. **Model choice is the real median lever**: same artifact, same prompt,
   LFM2 7.89 s vs Qwen 9999 ms end to end, and 50.3 vs 63.1 ms/token. Already a
   pinned model in this checkout; selection/default is the owner's publication
   and licence call, not engineering.

Savings attributable to each slice, on the warm path (Qwen, 1590-char prompt,
4 threads, emulator):

| Slice | Removes | Confidence |
| --- | --- | --- |
| §6.4 warm path | 2.4–11.2 s of load, and 1.3–2.8 s of cold retrieval, from turn 1 only | high — the costs are directly measured |
| §6.5 fixed capacity + prefix reuse | up to ~1.4–2.0 s of prefill — the premise is **verified in code**, not assumed: `systemPromptBytes` is computed once from `ResearchPrompt.system` (`LocalResearchEngine.kt:76`) and passed unchanged every turn (`:143`), so the template-plus-system prefix really is byte-identical and only the capacity churn throws it away | magnitude still to measure |
| §6.5 `GGML_LLAMAFILE` | unknown share of the remaining prefill (upstream reports 2–3× prompt processing on Android ARM) | **unmeasured on this build** |
| §6.7 evidence-on-screen | 0 s of real latency, ~5 s of *waiting with something to read* | high, and it is the only perceived-only row |
| §6.6 shorter bound | tail only | high |

Residual that no slice touches: the 2.0 s gap between a 20-char prompt (2.4–3.0 s)
and a warm tiny prompt is not accounted for by prefill alone, so part of
`first_token` is setup (template application, double tokenization,
`llama_tokenize` sizing, grammar parse, two sampler chains —
`local_model_runtime.cpp:198-248`, `:300-343`). Today the repo cannot see this
because `first_token_ms` is the only native timer. **Slice 0 below exists to
close that blind spot before any target is claimed.**

Target verdict, from the arithmetic: **≤ 2.5 s warm first text is conditional.**
It requires prefix reuse to recover ≈ 1.7 s *and* llamafile to cut the remaining
prefill by roughly half (≈ 5.0 → 3.3 → ~1.8 s), plus warm retrieval. If either
assumption fails, the honest reachable gate on this profile is ≈ 3.5–4.0 s —
still a large win over 5.3–7.3 s, and the spec records that fallback rather than
pretending the 2.5 s is free.

### 7.1 Shelf arithmetic: why "sources ≤ 1 s" is a caching fix, not a parallelism fix
The 7-library figure and the one-library figure look contradictory and are not:

| Observation | Value | Where |
| --- | --- | --- |
| Retrieval, one library, warm turns | 17–41 ms | `journey-current-001.json:61` |
| Retrieval, first turn after install | 1323 / 2211 / 2794 ms | `journey-current-003.json:61`, `journey-current-002.json:61`, `lfm2-journey-001.json:61` |
| Retrieval, 7-library shelf | 3.7 / **11.6** / 16.1 s | `work/demo-30s-blobs.md:15` (untracked) |

Two independent shelf measurements now agree on the per-library cost: the untracked
note gives `11.6 s ÷ 7 ≈ 1.66 s` per library, and the shelf suite's own worst case
gives `16 113 ms ÷ 9 ≈ 1.79 s` per library. Both land inside the measured cold-open
range (1 323–2 794 ms), which is the point: the shelf number is **cold opens added
together**, not queries' worth of work — warm, one library costs tens of milliseconds.
The
consequence for the design is decisive — §6.4 steps 0 and 4 (prime at activation,
plus a per-library connection cache) are what make a 7-library shelf answer in
≈ 7 × 20–40 ms ≈ 0,15–0,3 s, and the ≤ 1 s target is reachable by caching alone.

Bounded parallelism stays in the design, but demoted to what it actually is:
insurance for the *very first turn*, before warming has run. It is specified as
`§6.11` above, and it must not be presented as the reason sources get fast.

### 7.2 Turn timeline

Before (cold first turn, one library, Qwen, emulator):
`send → reserve → Room begin → retrieval 1323–2794 ms → load 2.4–11.2 s →
prefill+setup 4.3–5.4 s → first text at 8.9–10.1 s → decode to 11.3–15.7 s`

After (target, assumptions above holding):
`send → reserve → (model resident, prefix primed, libraries open)
→ retrieval ≤ 300 ms → evidence on screen ≤ 1 s → prefill of new passages only →
first text ≤ 2.5 s (fallback 4.0 s) → decode ≤ 200 tokens → end to end ≤ 8 s`

### 7.3 Attribution of the shelf gap: prompt size, not memory

`first_visible_text_elapsed_ms − retrieval_elapsed_ms` for the ten `COMPLETE`
cases in `eightg-1-ui-report.json`, plotted against the recorded excerpt size
(summed from each case's own `evidence` array), fits a straight line:

```
time to first text after retrieval ≈ 12.6 s + 15.3 ms × prompt characters
```

| excerpt chars | measured gap | fit |
| --- | --- | --- |
| 1 222 | 31.3 s | 31.2 s |
| 1 468 | 32.7 s | 35.1 s |
| 2 765 | 53.6 s | 54.8 s |
| 3 016 | 53.5 s | 58.7 s |
| 3 454 | 63.6 s | 65.5 s |
| 4 073 | 74.0 s | 75.0 s |
| 5 113 | 95.4 s | 90.7 s |
| 5 688 | 96.7 s | 99.7 s |
| 5 915 | 108.5 s | 103.2 s |
| 6 005 | 104.3 s | 104.6 s |

Three things follow, and they change what this design prioritises:

1. **It is not memory.** The same case costs the same on both RAM profiles once
   retrieval is subtracted: `compare-bangkok-hanoi` 75,0 s on 2 GB versus 74,0 s on
   8 GB, and `synth-japan-rail-week` 51,5 s versus 52,3 s. §10 risk 9 H1 is dead.
2. **Within one measurement path the cost is linear; between paths it differs
   4–8×, and that gap is the real mystery.** Pooling every artifact that carries a
   prompt size and a first-text time gives two separate populations, not one curve:

   | Path | prompt chars | time to first text | ms per char |
   | --- | --- | --- | --- |
   | cost harness (`GenerationCostTest`, direct engine call, model warm) | 1 569 – 1 590 | 3 947 – 6 612 ms | 2.5 – 4.2 |
   | cost harness, first call of a process (includes load) | 20 | 2 412 – 11 207 ms | n/a (load) |
   | shelf UI (`eightg-1`, 9 libraries) | 1 222 – 6 005 | 31.3 – 104.3 s after retrieval | ≈15.3 (fit) |

   Three UI populations exist, and they do not agree:

   | Population | harness | libraries | sources | prompt chars | net wait | ms per char |
   | --- | --- | --- | --- | --- | --- | --- |
   | `release/0.4.0` journeys (`FollowUpJourneyTest`) | app state flow | 1 | 3 | 998 – 2 581 | 2.9 – 9.0 s | **2.1 – 5.6** |
   | `feat/offline-research-engine` shelf suite (`PublicResearchWorkflowTest`, Compose `waitUntil`) | real Compose tree | 9 | 6 | 1 222 – 5 995 | 31.4 – 108.5 s | **16.1 – 25.7** |
   | `GenerationCostTest` direct engine call | none | 1 | 3 | 1 569 – 1 590 | 3.9 – 6.6 s | 2.5 – 4.2 |

   The release journeys and the shelf suite are both end-to-end app paths, both on
   4-core emulators, both with LFM2 1.2B — and at comparable prompt size the shelf
   suite waits **~8× longer per character**. Prompt size, model, cores and RAM are all
   eliminated as explanations by those three rows; what is left is a mix of (a) real
   prompt content the shelf records never stored (`prompt_characters` is absent there,
   while the release journeys record it), (b) the six-source prompt structure and
   grammar on that branch, and (c) the capacity bucket those prompts push the request
   into. Measurement style was the obvious third theory and it is **closed** further
   down this section: the shelf record's own two independent timers agree to 0.2 %.

   So the 15.3 ms/char fit is **descriptive of the shelf suite only**, and the
   ~8× cross-population gap is the single most important open question in this
   design, ahead of everything in §6 — it is a question about **which build ships**,
   not about which stop-watch is correct. §11 asks the owner to treat that as decision
   one, and slice 0 exists to settle it before any other slice is scheduled.

   Convexity inside either path is not measurable from this data.

   **One caveat that changes how the fit is read.** The ten shelf cases have no
   `prompt_characters` field at all — the fit above uses *excerpt* characters as the
   independent variable, so the system prompt (≤1 400), the question (≤4 000 on that
   branch), source headers and envelope markup, and the retained earlier questions
   (`ResearchPrompt.kt:123` — questions only, not answers) are all silently inside
   the 12.6 s intercept. Two consequences. First, cost per *actual* prompt character
   is lower than 15.3 ms; the number that matters for the product lever is the one
   measured, because the bound controls excerpt characters directly. Second, the
   intercept is a real floor: even an answer with **zero passages** waits ~12.6 s on
   this path, five times the §2 target, so the floor has to be attacked by slices 1
   and 3 (no per-turn context rebuild, no per-turn load) and by whatever explains the
   cross-population gap in item 2 above — bounding the prompt alone cannot reach 2.5 s.
   Projected from the shelf fit: 6 sources ≈ 74 s, 3 sources ≈ 43 s, 1 source ≈ 28 s,
   0 sources ≈ 12.6 s.

   The shelf runner and the app agree with each other, so the wait is real. Each
   shelf record carries two independent measurements of the same moment: the app's own
   `first_visible_text_elapsed_ms` (timed inside `ResearchWork` from submit) and the
   Compose-tree `first_observed_work_text_ms`. They differ by 244 ms on a 109.7 s case
   (0.2 %), so "the harness waited differently" is **eliminated** — the app really
   spends that long before producing its first character.

   Also eliminated: a hidden second generation pass. The branch's engine change
   (`git diff release/0.4.0 HEAD -- engine/LocalResearchEngine.kt`) adds only a
   terminal `CitedDigest.gate(...)` after `StructuredAnswer.decode` — one call after
   generation ends, replacing the answer text, and it is skipped when cancelled.
   `runtime.generate` still has exactly one call site, so `generationAttempts` stays 1.

   Still open: why the branch path costs ~8× more per prompt token than the release
   path with the same model (≈55 ms/token of prompt on the branch versus ≈5 ms/token
   on the release journeys) when prompt size, model, cores, RAM and build flags are
   all accounted for. Remaining candidates are the six-source prompt structure, the
   capacity bucket that prompt pushes the request into (2 120 tokens → the 4 096
   bucket, versus 1 420 → 2 048), and whatever the `prompt_characters` that the shelf
   records never stored would show. Slice 0's paired run settles it.

   Convexity inside either path is not measurable from this data. Settled by slice 0:
   one identical prompt and one identical library set run through **both** runners —
   the state-flow wait (`FollowUpJourneyTest`) and the Compose-tree wait
   (`PublicResearchWorkflowTest`) — with `prompt_characters` recorded on both, so the
   8× difference gets attributed to prompt content, the six-source grammar structure,
   or harness, instead of being assumed away.
3. **The branch that produced these runs has no prompt budget** — and neither has any
   other Blobs tree: `MAX_PROMPT_CHARACTERS` was never implemented (§6.13), so what the
   model receives is bounded only per-field: `MAX_ANSWER_SOURCES = 6`,
   `MAX_QUESTION_CHARACTERS = 4_000`,
   `MAX_SOURCE_EXCERPT_CHARACTERS = 2_000` — a theoretical 16 000 characters of
   question plus passages, bounded only implicitly by the 4 096-token context
   ceiling. `goal.md:13-14` names "a bounded prompt" as part of the fixed turn
   path, so implementing a global bound is a **contract fix, not a tuning choice**.

The 12.6 s intercept cannot be decomposed from recorded data alone — it bundles
load, template, double tokenize, context `prepare`, grammar and sampler setup, and
the fixed part of the prompt (the 946-character system block, measured from
`ResearchPrompt.system` at `81af8c7`, plus question). Slice 0's
timers exist to split exactly that number.

### 7.4 What this makes the design optimise for

With a linear prompt-cost law, the levers rank by characters removed, not by
milliseconds shaved:

| Lever | Prompt tokens removed | First-text effect on the shelf path |
| --- | --- | --- |
| Global prompt bound (§6.13): 6 → 3 sources | ~half of a shelf prompt | ≈ −45 s at measured 15.3 ms/char |
| Prefix reuse of the byte-stable system prompt (§6.5) | ~350–400 of every prompt | removes a fixed share plus the rebuild floor |
| `GGML_LLAMAFILE` (§6.5) | 0 | multiplies the remaining prefill rate; value unproven here |
| Answer bound 320 → 200 (§6.6) | 0 | tail only, per §7 item 3 |
| Warm path (§6.4) | 0 | removes the load share of the 12.6 s intercept |

The honest headline for the owner, stated with its condition attached: **within the
shelf suite's own measurements, prompt size is the biggest controllable term** —
~15 ms per passage character, so three sources instead of six is roughly 45 seconds.
Whether a real user pays that is not yet established: the release-branch journey
harness measured the same model and core count at 2.1–5.6 ms per character
(§7.3 item 2), an ~8× spread that has no owner yet. Slice 0 exists to assign it before
this design spends effort on the wrong term. Either way, six sources and sub-10-second
shelf answers cannot both be true at 15 ms/char, and no governor changes that
arithmetic — so §11 Q1 is a product decision about what a shelf answer must contain,
not a performance preference.

## 8. Error handling and fallback

| Condition | Behaviour |
| --- | --- |
| ADPF absent / `getManager()` null / API < 33 | `adpfEnabled = false`; everything else still works; Settings status reads "not supported on this device" |
| `setThreads` unsupported (API < 34) | Session covers the calling thread only; no retry loop |
| `/proc/self/task` diff finds no pool TIDs | Same as above; never a failure of the turn |
| ADPF call returns error | Session dropped for that turn; generation unaffected (hint is advisory) |
| Warming interrupted by a user turn | Warm job cancelled at the next step boundary; the turn owns the runtime; no queued re-entry while busy |
| `prepareModel` fails | Turn proceeds exactly as today (lazy load inside generate) |
| `posix_fadvise` fails | Ignored; it is only a hint to the page cache |
| Thermal ≥ MODERATE | Re-plan via `llama_set_n_threads`; ≥ SEVERE drops residency and uses the 160 bound |
| `onTrimMemory` | Release model as today, mark residency false, re-warm on next foreground idle |
| Context too large for prompt | `CONTEXT_FULL` unchanged; the turn fails loudly rather than silently re-bucketing |
| Fixed 4096 context allocation fails | Fall back to the previous bucket computation once, log the plan, continue |
| A library cannot be opened during retrieval | Today any exception ends the turn. Design: per-library failure is isolated — that library contributes no candidates, the turn proceeds, and a notice names the package that failed. Losing a turn because one of seven optional libraries is unreadable is not acceptable behaviour for a research app |
| A concurrent library task throws | Cancel the siblings, re-enter the sequential path once, and drop any half-published partial list rather than showing it as final |
| Priming fails right after package activation | Install still reports success. Priming is best-effort and reschedules on the next foreground idle; a user must never see a failed install because a hint failed |
| Cached connection outlives its package (removed or upgraded) | Evict on removal and on version change; a stale connection must never serve a turn |
| `allowedCores()` returns 0 or errors | Treat as 1, fall back to the existing processor-count formula, and record the fallback in the plan `reason` |
| Budget cannot fit question + minimum passages | Drop whole passages **and** their evidence entries together, longest first, so the sources panel always matches what the model read; never half-truncate a passage into a non-quote |
| Budget would leave **zero** passages | Do not run the completion: shrink `answerTokenLimit` first, then report a scope problem. A passage-less turn is contractually unable to carry a marker or claim `sourced` (`docs/bounty-contract.md`), so trimming to zero would trade speed for a different product promise |
| Budget cannot fit the question itself | Fail the turn as invalid input before `generate`, which is exactly what already happens today: `ResearchWork.kt:348` rejects a question longer than `MAX_QUESTION_CHARACTERS` and `AppViewModel.kt:184` truncates the draft to it — no prompt is built by slicing the user's question mid-sentence |
| Budget + `answerTokenLimit` exceeds the fixed context | Reduce the passage count until `prompt_tokens + answerTokenLimit ≤ contextCapacity`; only if even one passage fails does the turn report `CONTEXT_FULL` as it does today |

## 9. Evidence and testing

Repo rules bind here, and one of them is in tension with this plan:
`docs/rules/testing-and-evaluation.md` — "A native change needs the relevant ABI
build and target-device acceptance when it changes loading, decoding,
cancellation, KV cache, or memory behaviour." This design changes loading, KV
cache, and memory behaviour, and `docs/engineering-roadmap.md:308-313` records
that no phone-class device can be attached and that the owner confirmed this on
2026-10-24. Consequence, stated instead of hidden: slices 0–4 and 3b can be measured on
the existing emulator profile, and **any release claim about real-device speed
stays blocked** until device acceptance runs. The plan does not convert emulator
numbers into phone claims.

Contract tests (JVM/unit, no device):
- `SpeedGovernor` policy table: allowed-cores → threads, including "8 threads
  never on a 4-core cpuset", thermal re-plan, `Turbo` demotion under the memory
  floor, and mode mapping from persisted `PerformanceMode`.
- Retrieval failure isolation: one unreadable library among several still returns
  evidence from the rest and a named notice, and a fan-out run never shares one
  connection across two tasks.
- `context_capacity` fit check: fixed capacity, `CONTEXT_FULL` still raised,
  prefix start reported as a value a test can assert.
- Prompt bound: plan-supplied token limit reaches `generate` and the 32 KiB
  byte bound still aborts the sink.
- `LibrarySqlite` connection cache: eviction on trim, correctness unchanged,
  no connection reused across a package version change.
- `recommendedModel` honouring the published floor: a device below every
  `minimum_ram_mb` gets no recommendation instead of the smallest model, and the
  existing `PackageCatalogTest` (which already writes
  `compatibility.minimum_ram_mb = 8192`, `:63`) is extended to assert the
  no-fallback behaviour.
- Prompt bound (§6.13): assembled prompt never exceeds the plan budget at 1, 3 and 6
  sources; trimming removes evidence **with** the passage, so `evidence.size` and what
  the model saw always agree; the grammar's marker set is rebuilt for the reduced
  source count (`StructuredAnswer.grammarFor`) rather than admitting markers for a
  passage that got cut; and a question too long to leave room for any passage fails
  before `generate`.

Instrumented acceptance (androidTest, existing harness):
- `FollowUpJourneyTest` and `AppJourneyPerformanceTest` re-run with the same
  model/library pins so first-visible-text and end-to-end are comparable to the
  artifacts cited above; a cold-start variant that measures the very first turn
  after install, which is the case this design targets.
- `PromptCitationProbeTest` re-run after `GGML_LLAMAFILE` and after the shortened
  bound, because both can change answer content and marker density.
- Commit the missing config for `MultiLibraryRetrievalScalingTest` (it asserts a
  pushed `retrieval-benchmark-multi-library.json` at `:35`, named at `:324`, that
  has never been in the repo) built from the tracked case file
  `corpus/evaluation/cases/multi-library-retrieval-scaling-v6.json` (9 libraries),
  so per-library scaling finally has numbers instead of a single untracked note.
- Reports pulled immediately (the suite uninstalls the app at exit).

UI:
- A parity/journey check that sources are visible while a turn is RUNNING, that
  the `pending` chip is present, and that no model prose is attributed to
  evidence — this is the `bounty-contract.md:115-118` boundary, so it needs a
  test, not a comment.
- A check that the question bubble appears before the RUNNING row exists.

**Emitter wiring, verified to be small.** This is not new capability, only plumbing —
the helper already exists and is used by three other captures:

| Capture | Already records | Missing | Fix |
| --- | --- | --- | --- |
| `FollowUpJourneyTest` | `installed_apk_sha256` (`:59`), `model_sha256`, `library_sha256`, `inference_threads` (`:63-65`) | `device_acceptance`, `abi` | two `.put` calls |
| `GenerationCostTest` | `installed_apk_sha256` (`:56,:154`), `prompt_characters` (`:60`), `threads`, `available_processors`, `output_token_limit` | `device_acceptance`, `fingerprint`/`api` | two to three calls |
| `PublicResearchWorkflowTest` (the shelf suite) | `fingerprint`, `api`, `airplane_mode`, `selected_model`, a `packages` array (`:106-118`) | **`installed_apk_sha256`, `inference_threads`, `output_token_limit`, `device_acceptance`, and per case `prompt_characters` / `source_count` / `history_pair_count` / `generation_attempts`** | add `.put("installed_apk_sha256", installedApkSha256(context))` and copy the four values from `container.work.lastPerformanceSnapshot`, which the runner already reads (`:209-211`) |

The per-case fields come from an object the harness already holds, so the shelf suite's
inability to answer §7.3's 8× question is one missing block of `.put` calls, not a
missing measurement. Slice 0 is still required, because `lastPerformanceSnapshot`
itself has no prefill/setup breakdown.

### 9.1 Record contract per slice

`goal.md:67-69` forbids claiming Blobs is fast without a reproducible record, and
the cost artifacts are close to that schema (`run_id`, `application_id`,
`installed_apk_sha256`, `model_sha256`, `library_sha256`, `threads`,
`available_processors`, `output_token_limit`, per-run `first_token_ms` /
`elapsed_ms` / `ms_per_generated_token` / `decoded_document`). **They are not
complete, and this is a real gap, not a formality:** the journey and cost emitters
record no device identity at all. Only the single-shot `answer-*` captures carry
`fingerprint` (`google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574…`),
`api` (36) and `airplane_mode_enabled`. So before any slice gate is scored, the
emitters gain the fields the objective demands:

- device identity: `fingerprint`, `api`, `abi`, `airplane_mode_enabled`
- governor state: `allowed_cores`, the resolved `SpeedPlan` verbatim (threads,
  token bound, capacity, residency, adpf), `performance_mode`, and
  `adpf_supported`
- **prompt shape per turn**: `prompt_characters`, `source_count`,
  `history_pair_count`. The app already carries all three — `WorkPerformanceSnapshot`
  holds `sourceCount`/`promptCharacters`/`historyPairCount` (`WorkPerformanceSnapshot.kt:4-11`)
  and the snapshot is built at `ResearchWork.kt:282-291` — but the shelf reports never
  emitted them: `eightg-1-ui-report.json` has no `prompt_characters` key at all, which
  is precisely why §7.3 can only fit against excerpt characters and cannot separate
  prompt cost from path cost today.
- thermal at turn start and end, plus PSS at first text and after the turn
  (the existing 1 s sampler in `AppJourneyPerformanceTest.kt:113-120`)
- **`device_acceptance`**, written as `unverified` on every emulator record and
  changed only by a phone-class run. The brief asks for the phone status to be
  *written as unverified*, which is a field, not a sentence in a document — and the
  codebase already uses this vocabulary for a different axis (`grounding=UNVERIFIED`
  in the `answer-*` artifacts), so the shape is consistent rather than invented.
  While `docs/engineering-roadmap.md:308-313` records no phone-class device, every
  speed record this design produces must carry `device_acceptance: unverified`.

Each slice therefore ships with a named run id and a *paired* baseline, never a
bare number:

| Slice | Record required | Compared against | Gate metric |
| --- | --- | --- | --- |
| 0 instrumentation | `cost-timers-<n>` | same APK, before/after timers only | timers sum to `elapsed_ms` within 5 % |
| 1 cache correctness | `cost-reuse-<n>` | `cost-app-003` (Qwen, 4 threads, 1590-char prompt) | turn-2 `first_token_ms` < turn-1, target ≥ 30 % lower than the measured 5410 |
| 2 governor + catalog floor | unit records + `cost-cores-<n>` | `cost-baseline-001` (the 8-thread collapse) | threads ≤ `allowedCores`; no run above the 4-thread ms/token |
| 3 warm path | `journey-warm-<n>` | `journey-current-003` (cold turn 1 at 8921 ms) | turn-1 first text ≤ 3.0 s; PSS recorded at first text and after turn |
| 4 perceived | `UiParityTest` capture + `answer-<n>` with `evidence_ready_state_ms` | `answer-qwen-pkg-002.json:43` (63 ms) | sources rendered while status is `RUNNING`, chip present, no prose attributed |
| 5 answer bound | `prompt-citation-probe-<n>` | `prompt-citation-probe-qwen-001` / `-lfm2-002` | marker count not worse than baseline; worst-case e2e ≤ 200 × ms/token |
| 6 settings | `WorkFlowTest` + screenshot parity | current | mode persists; status line reports the real plan |
| 7 llamafile | `cost-llamafile-<n>` paired with a re-run **of the current tree** (batch-512 + AVX2 flags), not `cost-app-003` | identical APK except that flag | prefill timers, plus held-out answer suite unchanged in decode/support |
| 8 ADPF | `adpf-support-<n>` | none | records `supported` / `not supported` only; **no speed claim is permitted in this slice** |

Rules that keep the records honest, all lifted from existing repo precedent:

- Same tool, same device, same model, same library, or the comparison is a method
  error (`docs/architecture.md:436-439` records exactly that mistake). The record
  must also name the **native build state** (batch size, enabled CPU features,
  thread cap), because the tree currently carries uncommitted prefill and thread
  changes (§10 risk 8); a baseline from before them is not a valid pair.
- Reports are pulled immediately after each run (the suite uninstalls the app and
  deletes staged packages with it).
- Numbers land under `corpus/evaluation/results/` (untracked by design) and only
  the summary line goes into `docs/architecture.md` / `docs/engineering-roadmap.md`
  — no second status document (`docs/rules/workspace-hygiene.md`).
- One device-heavy run at a time, with `/proc/loadavg` and free memory checked
  first; the 2026-10-24 `INSTRUMENTATION_ABORTED` event is the precedent.
- Emulator records stay labelled as this profile's observation. A phone claim
  requires a phone-class device, which `docs/engineering-roadmap.md:308-313`
  records as unavailable.

## 10. Risks

1. **Memory pressure.** Residency at 1.30–1.89 GiB is the single biggest risk;
   the 2 GB profile already degrades to 82–86 s per turn and stops. Mitigation:
   the residency headroom rule plus the published `minimum_ram_mb` floor (§6.10),
   the `Saver` default on small devices, and re-warm after trim rather than
   pre-load at any cost. The floor values themselves are already in the catalog
   (4096/6144/8192 MB); what is missing is `recommendedModel` actually respecting
   them instead of falling through to the smallest model
   (`PackageCatalog.kt:96-100`).
2. **ADPF gains are unprovable on this host.** The emulator has no vendor
   governor, so the hint session will be coded, exercised, and reported as
   "supported / not supported" — never as "faster".
3. **Shorter answers reduce citation-marker density**, already recorded as 1/6
   (Qwen) and 2–3/6 (LFM2) (`docs/architecture.md:441-455`, `:566-583`).
   Disclosure comes from `AnswerGrounding`, not from the model's claim, so the
   badge stays honest, but the count must be re-measured, not assumed.
4. **`GGML_LLAMAFILE` changes numerics.** It must land with a held-out answer
   suite re-run, per the testing rule about prompt/model changes.
5. **Fixed 4096 context costs KV memory** even for tiny prompts. Bounded: it is
   one Q8_0 context, and the alternative (bucket churn) is the measured bug.
6. **Warming competes with the user's perception of idle battery/heat.** The
   thermal gate and `Saver` mode are the answer; a `prefetch as soon as the app
   opens, always` policy is deliberately not part of this design.
7. **Concurrent sessions move the catalog underneath this spec.** This checkout
   has three pinned models; the published catalog is reported at revision
   2026102502 with a fourth, smaller model. The residency table (§6.2) must be
   re-read against the locks at implementation time rather than copied from this
   document.
8. **The tree is moving underneath this design.** As of 2026-10-25 the working
   tree carries uncommitted native edits from a parallel session that are **not**
   in the artifacts cited here: x86_64 `GGML_AVX/AVX2/FMA/F16C/BMI2` enabled
   (`cpp/CMakeLists.txt`), `kKvDecodeBatchTokens` 256 → **512** (`kv_cache.h`) — a
   prefill-batch accelerator, a different mechanism from llamafile — and the JNI
   thread guard raised from `threads > 4` to `threads > blobs::kMaximumThreads`
   (8), which is what makes a Turbo tier above 4 threads possible at all.
   Consequences: (a) every line citation in this document is working-tree, not
   HEAD, and must be re-derived once those edits land; (b) the §9.1 baselines
   predate them, so a slice could be credited with speed someone else already
   bought; (c) llamafile's marginal value must be measured **against the batch-512
   + AVX2 build**, never against `cost-app-003`. The behaviour this design targets
   is unchanged by those edits — `context_capacity()` still buckets and
   `KvCache::prepare()` still resets on any capacity or thread change, both
   re-verified in the current tree. (d) The harness this spec extends —
   `GenerationCostTest.kt`, `FollowUpJourneyTest.kt`, `PromptCitationProbeTest.kt` —
   is **untracked** in git right now, so the slice-0 timers and the §9.1 record
   fields land on a test surface that itself has not been committed yet. Confirm
   those files are committed before adding to them, or the work mixes into someone
   else's uncommitted change.
9. **The ~95 s shelf gap is unexplained, and the design must not pretend
   otherwise.** One hypothesis has already been eliminated with evidence, which
   matters more than adding another guess:
   - **H0 — eliminated: the slow runs were not built with the new flags.** The
     shelf runs come from a **clean tree** at `81af8c7` on
     `feat/offline-research-engine`, whose committed `CMakeLists.txt:45,57` still
     has `GGML_LLAMAFILE OFF` and `GGML_SSE42 ON` with **no AVX2/AVX/FMA/BMI2**,
     `kv_cache.h:10` still `kKvDecodeBatchTokens = 256`, and `NativeBridge.kt:5-6`
     still `coerceIn(1, MAX_INFERENCE_THREADS)` on a 4-core AVD — identical to
     `release/0.4.0` HEAD for all three. So the AVX2 / batch-512 / thread-cap
     edits that are uncommitted in the main checkout cannot explain a slowdown in
     runs that did not use them, and the fast journey runs share the same flags.
     **Same build, same cores, same model, 10× the wait** is the actual puzzle.
   - **H1 — eliminated.** Same case, both RAM profiles, once retrieval is
     subtracted: `compare-bangkok-hanoi` 75,0 s on 2 GB versus 74,0 s on 8 GB, and
     `synth-japan-rail-week` 51,5 s versus 52,3 s. Memory pressure would not
     reproduce across a 4× RAM difference, and three repeat runs of the 2 GB case
     land within ±0.2 % (76 541 / 76 618 / 76 343 ms). Policy re-loading is also
     excluded: `ResearchWork.stop()` releases the model only on user stop or trim
     (`ResearchWork.kt:313-324,343-345`) and every shelf case is `COMPLETE`. A
     `load_model` cache-hit flag is still worth recording, but as a sanity check,
     not as a theory.
   - **H3 — leading explanation, with the shape claim retracted.** §7.3 fits
     ~15.3 ms per excerpt character across the ten shelf cases (internally linear,
     ±10 %) and a ~25 s-per-comparable-prompt gap to the direct-engine path, which
     pays ~3 ms/char. Both are real measurements; **which of them is the prompt's
     fault is not yet known**, and the earlier "superlinear" reading in this
     document was a comparison across two different paths, not a curve. The shelf
     path is fixed by §6.13 (fewer characters), §6.5 (stop re-reading the system
     prefix and stop rebuilding context) and §6.5/`GGML_LLAMAFILE` (raise the
     prefill rate). Settled by: a per-chunk prefill timer at several prompt lengths,
     run in **both** paths.
   - **H2 — still real, but it explains the intercept, not the slope.** Shelf
     prompts vary in length, the capacity bucket follows them, and `prepare()`
     calls `reset()` on any capacity or thread change (`kv_cache.cpp:38-43`), so
     the context and its saved prefix are rebuilt per turn — plausibly part of the
     12.6 s floor. Settled by: record reuse/prepare/realloc and the chosen bucket
     per turn.
   - **H4 — per-turn sampler and grammar construction** (GBNF parse, two chains,
     penalty arrays sized to the 151 936-entry vocabulary allocated per turn,
     `local_model_runtime.cpp:321`). Deterministic and vocabulary-sized, so it
     cannot be dismissed by intuition. Settled by: sampler-init timer.
   - **H5 — cost outside `generate`, narrowed.** The two independently recorded
     timers in the same shelf record (app-side 109 731 ms vs Compose-side
     109 975 ms) prove the wait happens inside the app, but they cannot say whether it
     is the model call or the Kotlin around it. Surviving candidates: evidence
     serialization for six sources, the Room checkpoint writes, and the six-source
     prompt structure. Settled by: timing the Kotlin segments of a turn separately
     from `generate`, in the same paired run.
   Record gap that blocks answering this from existing data: `eightg-1` and the
   `run*` shelf reports carry `fingerprint` and `api` but **no
   `installed_apk_sha256`**, so the build behind them had to be reconstructed from
   git state instead of the artifact — which is precisely the pairing §9.1 requires
   and why slice 0 must re-run the shelf with digests recorded.

## 11. Open questions for the owner

**Status 2026-10-26:** the owner reviewed nothing in this section, so the "Default if
silent" column below is now the adopted decision, recorded as **agent-adopted on owner
silence** rather than owner-approved. Any of them can still be vetoed; each veto changes
only the named rule. This distinction is kept visible because `docs/rules` makes
unverified claims inadmissible, and a decision the owner never made is not owner
approval.

Resolved on 2026-10-25 and adopted as defaults, veto any of them and only the
named rule changes: the 200-token bound in every mode (§6.6, approved); residency
under the §6.2 headroom rule, never in `Saver`, released after 5 idle minutes; the
4.0 s warm fallback (§2); LFM2-as-default stays out of scope (§3).

Genuinely open, in the order that blocks work. Each one carries the recommendation
and the default that gets adopted if the owner stays silent, so reviewing this spec is
one approve/reject pass rather than an essay:

| # | Decision | Recommendation | Default if silent |
| --- | --- | --- | --- |
| 1 | Which build ships: bounded 3-source or uncapped 6-source | Ship the bounded budget; keep six sources only where a scope-selected library genuinely needs them | Restore a global budget (slice 1a) and treat §2's narrow-profile targets as the product targets |
| 2 | `qwen25-0b5-q4km`: answer writer or digest writer on 2 GB | Treat it as the 2 GB digest writer it already behaves like; do not make it the default writer elsewhere | Keep it selectable, exclude it from any speed or quality claim, and leave default selection untouched |
| 3 | Which branch carries the speed work | `feat/offline-research-engine`, after its uncommitted native edits are landed or dropped | Same as recommendation; no code in either tree until then |

1. **Which configuration is the product the owner is shipping?** The harness-style
   theory is closed — the app's own timer and the UI observation agree to 0.2 %
   (§7.3), so the shelf wait is real inside the app. What is left is that the
   **branch** configuration (9 libraries, 6 sources, no prompt budget) is ~8× more
   expensive per prompt token than the release configuration (1 library, 3 sources,
   bounded) on the same model and cores. So the question is not "which measurement is
   the user" but "**which build is the product**": if six uncapped sources is what
   ships, then first text in the tens of seconds is the thing to fix, and slices 0, 1
   and 1a are the whole project. If not, the narrow-profile targets in §2 stand.
2. **`qwen25-0b5-q4km` cannot close its document inside 320 tokens** and its own
   catalog description says such turns fall back to cited digest lines. Options:
   shorten the prompt for that tier, accept digest-line answers as the 2 GB
   behaviour, or treat it as a residency/digest writer rather than an answer
   writer. This decides whether the 0.5 B model helps the speed goal or trades it
   away.
3. **Which branch carries the speed work?** Recommendation, based on what is where:
   `feat/offline-research-engine` is ahead — it owns the fourth model
   (`2135b8c`), the four-model catalog (`81af8c7`), and the engine fix
   `0c92a82 "an uncited answer is replaced even when tokens ran out"`, and the
   shelf runs that expose the ~95 s were produced from its clean tree. The speed
   slices touch `local_model_runtime.cpp`, `kv_cache.*` and `CMakeLists.txt`, which
   the main checkout currently carries as **uncommitted** edits (AVX2, batch-512,
   thread cap). Target the feature branch, and land or discard those uncommitted
   native edits first, or every slice here inherits someone else's work-in-progress.
   That engine fix also interacts with §6.6: a model that exhausts its token budget
   already gets replaced by digest lines, so halving the bound to 200 changes how
   often that path fires — re-check the citation behaviour after it, not before.

## 12. Slices (ordered, each independently provable)

This spec spans three separable deliverables, and §7.3 changed their relative
value. Each wave gets its own implementation plan rather than one giant one:

- **Wave A — make the answer stop eating the prompt:** slices 0, 1, 1a. Native
  attribution, cache correctness, prompt bound. Highest measured value, smallest
  surface, no UI.
- **Wave B — make the wait visible:** slices 3, 3b, 4. Warm path, retrieval
  insurance, evidence-on-screen and the optimistic bubble. Independent of Wave A.
- **Wave C — the governor product:** slices 2, 5, 6, 7, 8, 9. Adaptive policy,
  answer bound, Settings, llamafile, ADPF, records. Only Wave C's `Turbo` tier
  needs a device, and §10 says so.

Nothing in this ordering depends on GPU work, and GPU (§6.9) stays outside all
three waves.

0. **Instrument the native gap** — add timers inside `generate` separating
   template application, tokenization, `context_capacity`/`prepare`, grammar
   parse, sampler-chain construction, prefill chunks, and first decode, and
   surface them in the existing cost artifact (`GenerationCostTest`). Without
   this, §7's 2.0 s of unattributed setup is guesswork and every later slice is
   graded on a number it cannot explain. No behaviour change, no claim.
   **Run it on a full-shelf profile too, not only the journey profile**: §1.1 shows
   a ~95 s unexplained gap that only exists with nine libraries installed, and it
   is 10–20× larger than anything the narrow profile can see. The test for this
   slice is that H1–H5 (§10 risk 9) resolve — one of them is right, and which one
   decides what the rest of the design does.
   **And pair the harnesses:** one identical prompt, one identical library set,
   measured through the state-flow wait (`FollowUpJourneyTest`) and the Compose-tree
   wait (`PublicResearchWorkflowTest`) in the same build, with `prompt_characters`
   recorded on both. §7.3 shows a ~8× spread between harnesses on the same model and
   core count, and until that is attributed, no first-text target in this document is
   trustworthy.
   The timers must close two equations the artifacts already give, so the result
   is falsifiable rather than descriptive: with `L` = weight load, `S` = setup
   (template + tokenize + context prepare + grammar parse + two sampler chains),
   `P₀` = prefill of a 20-character prompt and `P₁` = prefill of the real 1590-
   character prompt, Qwen `cost-app-003` says `L + S + P₀ = 3029 ms` and
   `S + P₁ = 5012 ms`. Today neither `L` nor `S` is separable. If `S` turns out to
   exceed ~1 s, then setup — not prefill — is the first optimization target, and
   slices 1 and 7 get re-sequenced accordingly.
1. **Cache correctness** — fixed context capacity + `llama_set_n_threads`, no
   policy layer. Provable today against turn-2 `first_token_ms` 5410 vs 5012.
1a. **Prompt bound (§6.13)** — restore the global character budget and make it
   plan-supplied, shrinking with scope size. §7.3 ranks this as the highest-value
   change in the whole design on a shelf profile: at ~15 ms per prompt character,
   6 → 3 sources is roughly 45 seconds of waiting removed per turn, which no
   per-token optimization on this hardware comes close to.
   It is also a contract fix — `goal.md:13-14` requires a bounded prompt.
2. **Thread safety against over-subscription** — `allowedCores` replaces
   `availableProcessors()`; `DeviceProfile` + `SpeedPlan` + `SpeedGovernor`
   resolve a plan even with no Settings UI yet. Includes the §6.10 catalog
   bypass fix, because the plan's residency rule consumes that floor, and it
   amends `docs/architecture.md:321-322` in the same change (§6.1 reversal).
3. **Warm path** — prime at package activation (§6.4 step 0, the moment the page
   cache is already hot), fadvise, `prepareModel`, prefix prime, library
   connection cache, governor-triggered on foreground idle with trim/re-warm. This
   is also the slice that owns the shelf result: the §7.1 arithmetic says
   connection reuse plus warm opens, not parallelism, is what brings a 7-library
   retrieval from ~11.6 s into the hundreds of milliseconds.
3b. **Fan-out insurance** — bounded per-library parallel retrieval with
   thread-confined connections and a display-only `onEvidenceProgress`, for the
   first turn before warming has run (§6.11). Independent of the ADPF and GPU
   work, and it must be recorded as a cold-first-turn fix only.
4. **Perceived latency** — evidence-on-screen while RUNNING with `pending`,
   optimistic question bubble. Cheapest user-visible win; independent of 1–3.
5. **Answer bound** — plan-supplied 200/160 tokens, with the citation probe
   re-run attached.
6. **Settings "Speed" section** — Automatic/Saver/Turbo plus status line.
7. **`GGML_LLAMAFILE`** — build flag with held-out suite and cost re-measure.
8. **ADPF session** — hint plumbing, marked supported/not-supported; no claim.
9. **Durable records** — roadmap/architecture updates naming the emulator
   numbers, the phone-device blocker, and the scaling config; GPU variant stays
   unmeasured.

## 13. Non-goals

No semantic retrieval or reranker, no agent loop, no second completion per
question, no speculative decoding (the vendored pin has no
`llama-speculative*`), no hosted inference, no `sched_setaffinity`, no
user-facing core-count sliders, and no phone performance claim in this change. No
second generation pass and no retry loop: §7.3 confirms the branch's digest gate is a
terminal replacement after one `generate`, and this design keeps exactly one
completion per question. No new test infrastructure either — §9's record gap is closed
by `.put` calls against values the harnesses already hold, not by building a runner.
