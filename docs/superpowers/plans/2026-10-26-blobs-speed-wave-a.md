# Blobs SpeedGovernor — Wave A (attribution, cache correctness, prompt bound) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** make every stage of one research turn measurable, stop the KV context from being rebuilt every turn, and put a global budget back on the prompt the model is given.

**Architecture:** three layers, each with one responsibility. Native (`app/src/main/cpp`) owns stage timers, model load, context capacity and prefix reuse. `inference/` owns the prompt budget as a computed, deterministic value. `androidTest` captures the numbers into a JSON record whose fields name the device and the installed APK. No new package, no ADPF, no threads policy, no UI — those are Waves B and C.

**Tech Stack:** Kotlin 2.x + Jetpack Compose, llama.cpp (vendored pin `620d2225…`) behind JNI, JUnit 4 unit tests, Android instrumented tests, `corpus/evaluation/results/*.json` capture reports.

**Spec:** [docs/superpowers/specs/2026-10-25-blobs-speed-governor-design.md](../specs/2026-10-25-blobs-speed-governor-design.md) — §7.3 (the open question this wave answers), §6.5, §6.13, §9, §9.1, slices 0/1/1a.

**Wave scope:** this plan is Wave A only. Wave B (warm path, fan-out insurance, evidence-on-screen) and Wave C (governor, ADPF, Settings, llamafile, records) each get their own plan after Wave A's numbers land, because §7.3 says the biggest cost term is still unattributed and the outcome can re-sequence them.

## Global Constraints

- `minSdk = 28`, `compileSdk = 37`, `ndkVersion = "27.1.12297006"`, ABI filters `arm64-v8a`, `x86_64` (`android/app/build.gradle.kts:12,14,27-33`).
- English only for source, identifiers, comments, developer docs and new default UI copy (`docs/rules/language.md`). No Indonesian strings in the app or repo docs.
- A native change that alters loading, decoding, cancellation, KV cache or memory behaviour needs the relevant ABI build **and** target-device acceptance (`docs/rules/testing-and-evaluation.md`). No phone-class device exists (`docs/engineering-roadmap.md:308-313`), so every number produced here is emulator-scoped and every record must carry `device_acceptance: "unverified"`.
- One retrieval pass and one completion per question, on a bounded prompt (`goal.md:13-16`). No retries, no agent loop, no second completion — including for warming (this wave adds none).
- Never claim the app is fast without a record naming APK digest, model, library, device, OS, configuration and query suite (`goal.md:67-69`).
- The gate for the whole project is `bash ./gradlew verify` run from `android/`; `connectedDebugAndroidTest` is the device gate and excludes `@ManualCheck` captures (`android/app/build.gradle.kts:26`).
- Reports are pulled off the device the moment a run ends; the suite uninstalls the app and deletes staged packages with it.
- `local_model_runtime.cpp`, `kv_cache.*`, `blobs.cpp` and `cpp/CMakeLists.txt` currently carry **someone else's uncommitted edits** (AVX2 flags, `kKvDecodeBatchTokens` 256→512, native thread cap 4→8). Task 1 exists to settle that before any code is written.

---

### Task 1: Establish a clean, attributable tree

**Files:**
- Inspect: `android/app/src/main/cpp/CMakeLists.txt`, `android/app/src/main/cpp/kv_cache.h`, `android/app/src/main/cpp/local_model_runtime.cpp`
- Create: `docs/superpowers/plans/2026-10-26-wave-a-tree-baseline.md`

**Interfaces:**
- Consumes: nothing.
- Produces: a recorded baseline (branch, commit, and the exact diff of native files) that every later task's before/after comparison is allowed to cite, plus a decision on where the wave's commits land.

- [ ] **Step 1: Capture the current state**

```bash
cd D:/rimbara/blobs
git rev-parse --abbrev-ref HEAD
git log --oneline -1
git status --porcelain android/app/src/main/cpp
git diff -w --ignore-blank-lines --stat -- android/app/src/main/cpp
git -C .worktrees/offline-research-engine log --oneline -1
```

- [ ] **Step 2: Write the baseline note**

Create `docs/superpowers/plans/2026-10-26-wave-a-tree-baseline.md` containing: current branch and HEAD; the native diffstat from Step 1; and one line per foreign edit that touches this wave's files, in this shape:

```markdown
- cpp/CMakeLists.txt: GGML_AVX/AVX2/FMA/F16C/BMI2 enabled for x86_64 — uncommitted at <commit> + working tree
- cpp/kv_cache.h: kKvDecodeBatchTokens 256 -> 512 — uncommitted
- cpp/blobs.cpp: thread guard `> 4` -> `> blobs::kMaximumThreads` (8) — uncommitted
Baseline for before/after comparison: <commit sha> + these three edits, or a commit that contains them.
```

- [ ] **Step 3: Decide the commit target and record it in the same file**

Append one line: `Wave A commits land on: <branch>`. `feat/offline-research-engine` is the recommendation (it already owns the fourth model, the four-model catalog and the terminal digest gate, and the shelf captures came from its clean tree). If the uncommitted native edits are not committed first, **stop** and ask the owner — editing those four files while they are dirty mixes two people's work in one commit.

- [ ] **Step 4: Verify the tree is safe to touch**

Run: `git status --porcelain android/app/src/main/cpp`
Expected: empty, or only the three foreign edits, now committed (re-run `git log --oneline -1 -- android/app/src/main/cpp` and see them in a commit). If the files are still dirty, do not continue.

---

### Task 2: Per-stage native timers, exposed to the capture report

**Files:**
- Modify: `android/app/src/main/cpp/local_model_runtime.h` (add a timings struct + accessor)
- Modify: `android/app/src/main/cpp/local_model_runtime.cpp` (`generate`, `load_model`, the prefill loop near `:260-297`, the sampler build near `:300-343`)
- Modify: `android/app/src/main/cpp/blobs.cpp` (new `lastTimingsNative` entry point)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/inference/NativeBridge.kt` (Kotlin boundary + data class)
- Modify: `android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt` (record timings per labelled run)
- Test: the instrumented capture itself is the test (no JVM-visible behaviour changes); the assertion is arithmetic on its output

**Interfaces:**
- Consumes: `blobs::generate(request, sink)` as it exists.
- Produces: `data class NativeTurnTimings(loadMs, templateMs, tokenizeMs, contextPrepareMs, grammarMs, samplerMs, prefillMs, firstTokenMs, modelWasCached, reusedPrefixTokens, promptTokens, contextCapacity)` — all `Long`/`Int`, missing values are `-1`. Kotlin signature: `internal fun lastTurnTimings(): NativeTurnTimings` on `NativeBridge`. JSON keys added to every cost run: `timings` (same names, lower snake case), `model_cached`, `reused_prefix_tokens`, `context_capacity`.

- [ ] **Step 1: Add the timing struct to the native layer**

In `local_model_runtime.h`, next to `kMaximumThreads`:

```cpp
struct GenerationTimings {
    long long load_ms;
    long long template_ms;
    long long tokenize_ms;
    long long context_prepare_ms;
    long long grammar_ms;
    long long sampler_ms;
    long long prefill_ms;
    long long first_token_ms;
    bool model_cached;
    int reused_prefix_tokens;
    int prompt_tokens;
    int context_capacity;
};

// Timings of the most recent generate() on this process; zeroed before each call.
GenerationTimings last_generation_timings();
```

- [ ] **Step 2: Fill it in around the existing stages**

In `local_model_runtime.cpp`, add a monotonic helper and stamp the stages that already exist — do not restructure them:

```cpp
long long elapsed_ms(const std::chrono::steady_clock::time_point& from) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - from).count();
}
```

`Timings timings{};` is assigned at function scope in `generate()`: around `load_model(...)` (`:206`, set `load_ms` and `model_cached` from the cache-hit branch of `load_model` — have `load_model` take a `bool* cached` out-parameter), around `render_prompt` (`:209`, `template_ms`), around both `llama_tokenize` calls (`:211-233`, one `tokenize_ms` covering both), around `kv_cache.prepare(...)` (`:237-243`, `context_prepare_ms`), around `llama_sampler_init_grammar` (`:311`, `grammar_ms`), around the two chain constructions (`:303-338`, `sampler_ms`), around the prefill chunk loop (`:273-297`, `prefill_ms`), and keep `first_token_ms` as the existing time-to-first-sunk-token. Record `reused_prefix_tokens` from `prompt_start` returned by `reuse_prefix` (`:248`), `prompt_tokens` from `token_count`, and `context_capacity` from the value handed to `prepare()`.

- [ ] **Step 3: Expose it over JNI**

In `blobs.cpp`, mirror the existing `extern "C"` style:

```cpp
extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_blobs_app_inference_NativeBridge_lastTimingsNative(JNIEnv *env, jobject) {
    const auto timings = blobs::last_generation_timings();
    const jlong values[] = {
        timings.load_ms, timings.template_ms, timings.tokenize_ms,
        timings.context_prepare_ms, timings.grammar_ms, timings.sampler_ms,
        timings.prefill_ms, timings.first_token_ms,
        timings.model_cached ? 1 : 0, timings.reused_prefix_tokens,
        timings.prompt_tokens, timings.context_capacity,
    };
    const auto result = env->NewLongArray(static_cast<jsize>(std::size(values)));
    if (result) {
        env->SetLongArrayRegion(result, 0, static_cast<jsize>(std::size(values)), values);
    }
    return result;
}
```

In `NativeBridge.kt`, declare `private external fun lastTimingsNative(): LongArray?` and add this **as a member of the class** — both cost-test methods already hold `val bridge = NativeBridge()` (`GenerationCostTest.kt:38,137`), so the call site in Step 4 needs no new wiring:

```kotlin
internal data class NativeTurnTimings(
    val loadMs: Long, val templateMs: Long, val tokenizeMs: Long,
    val contextPrepareMs: Long, val grammarMs: Long, val samplerMs: Long,
    val prefillMs: Long, val firstTokenMs: Long,
    val modelWasCached: Boolean, val reusedPrefixTokens: Int,
    val promptTokens: Int, val contextCapacity: Int,
)

// The data class is declared at file scope; this belongs to the class body.
internal fun lastTurnTimings(): NativeTurnTimings? = lastTimingsNative()?.let { values ->
    if (values.size < 12) null else NativeTurnTimings(
        values[0], values[1], values[2], values[3], values[4], values[5],
        values[6], values[7], values[8] == 1L,
        values[9].toInt(), values[10].toInt(), values[11].toInt(),
    )
}
```

- [ ] **Step 4: Emit the timings in the cost capture**

In `GenerationCostTest.kt`, inside the block that already builds each labelled run object (the one that writes `first_token_ms` and `ms_per_generated_token`), add:

```kotlin
bridge.lastTurnTimings()?.let { timings ->
    run.put("timings", JSONObject()
        .put("load_ms", timings.loadMs).put("template_ms", timings.templateMs)
        .put("tokenize_ms", timings.tokenizeMs)
        .put("context_prepare_ms", timings.contextPrepareMs)
        .put("grammar_ms", timings.grammarMs).put("sampler_ms", timings.samplerMs)
        .put("prefill_ms", timings.prefillMs).put("first_token_ms", timings.firstTokenMs))
    run.put("model_cached", timings.modelWasCached)
    run.put("reused_prefix_tokens", timings.reusedPrefixTokens)
    run.put("prompt_tokens", timings.promptTokens)
    run.put("context_capacity", timings.contextCapacity)
}
```

Also add `.put("device_acceptance", "unverified")` to that report's top level, next to `application_id`.

- [ ] **Step 5: Build, run, and check the arithmetic on the device**

Run: `cd android && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`
Then install only the test APK (keeps staged models) and name the class:

```bash
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class dev.blobs.app.GenerationCostTest#<method> \
  dev.blobs.app.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: the run completes, and pulling the report shows a `timings` object on every run.

- [ ] **Step 6: Assert the decomposition is honest**

Pull the report into `corpus/evaluation/results/` immediately, then check:

```bash
python3 - <<'PY'
import json,glob
p=sorted(glob.glob('corpus/evaluation/results/app-prompt-cost-*.json'))[-1]
d=json.load(open(p,encoding='utf-8'))
ok=True
for r in d['runs']:
    t=r.get('timings') or {}
    if not t: print('NO TIMINGS', r['label']); ok=False; continue
    parts=sum(v for v in t.values() if isinstance(v,int) and v>=0)
    total=r['elapsed_ms']
    print(f"{r['label']:16} sum={parts:7} elapsed={total:7} "
          f"load_cached={r.get('model_cached')} reuse={r.get('reused_prefix_tokens')} "
          f"cap={r.get('context_capacity')}")
    if parts > total * 1.20: print('  stage sum exceeds elapsed by >20%'); ok=False
print('OK' if ok else 'CHECK FAILURES')
PY
```

Expected: `OK`, `model_cached` false on the first labelled run and true afterwards, and a `context_capacity` that is the same number on every run of one model — if it differs, that is Task 3's bug reproduced with a number attached.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/cpp android/app/src/main/kotlin/dev/blobs/app/inference/NativeBridge.kt \
        android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt
git commit -m "feat(native): report per-stage timings for one local completion"
```

---

### Task 3: One stable context capacity, real prefix reuse, thread changes without a rebuild

**Files:**
- Modify: `android/app/src/main/cpp/local_model_runtime.cpp:70-81` (`context_capacity`) and its call site `:237`
- Modify: `android/app/src/main/cpp/kv_cache.cpp:38-43` (`prepare`) and add a thread-only change path
- Modify: `android/app/src/main/cpp/kv_cache.h` / `.cpp` (keep `threads_` creation value separate from the requested value)
- Test: `android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt` (reuse assertion via Task 2's fields)
- Test: `android/app/src/test/kotlin/dev/blobs/app/inference/InferenceContextPolicyTest.kt` (already exists — extend the Kotlin-side expectation)

**Interfaces:**
- Consumes: Task 2's `reused_prefix_tokens`, `context_capacity`, `first_token_ms`.
- Produces: `blobs::context_capacity(int prompt_tokens, int output_tokens, int fixed_capacity)` returning `fixed_capacity` when the request fits and throwing `CONTEXT_FULL` otherwise; a `KvCache` that reuses its context across turns and applies thread changes with `llama_set_n_threads` instead of reallocating. No new Kotlin-facing thread setter in this wave.

- [ ] **Step 1: Write the failing expectation on the device**

Extend the cost capture's own assertions (the file already writes labelled runs) with a paired pair, `turn1-grammar` and `turn2-grammar` on prompts of the same size. Run it before changing native code and record the numbers: expected today, from `app-prompt-cost-cost-app-003.json`, `turn2 first_token_ms 5410` vs `turn1 5012` and `reused_prefix_tokens 0` — i.e. the failure state.

- [ ] **Step 2: Make the capacity a fit check, not a bucket chooser**

```cpp
int context_capacity(int prompt_tokens, int output_tokens, int fixed_capacity) {
    const int required = prompt_tokens + output_tokens;
    if (required > fixed_capacity) {
        throw std::runtime_error("CONTEXT_FULL");
    }
    return fixed_capacity;
}
```

At the call site, pass the already-pinned Kotlin constant down: `const int capacity = context_capacity(token_count, request.output_token_limit, kMaximumContextTokens);` and keep the existing `CONTEXT_FULL` behaviour so an oversized request still fails loudly rather than silently re-bucketing. `kMaximumContextTokens` is 4096, matching `LOCAL_RESEARCH_CONTEXT_TOKENS` (`inference/InferenceContextPolicy.kt:7`) — the Kotlin layer already wants one fixed capacity; this makes native agree with it.

- [ ] **Step 3: Stop thread equality from silently dropping the cache**

`KvCache::prepare` treats "threads differ" exactly like "model differs" — it frees the
context (`kv_cache.cpp:38-43`). No turn changes the thread count today, so this is
latent, but the fixed-capacity change in Step 2 is the moment the two meanings should
come apart, and Wave C's thermal back-off is what makes it live. Do it without adding an
API nothing calls yet.

In `kv_cache.h`, split the one field into two:

```cpp
int threads_ = 0;             // threads the context was created with
int requested_threads_ = 0;   // threads the most recent prepare() asked for
```

In `KvCache::prepare`, make the reuse condition `context_ && model_ == model &&
capacity_ == capacity` — dropping `threads_ == threads` — and when a live context is
reused with a different requested thread count, call
`llama_set_n_threads(context_.get(), threads, threads)` once, then update
`requested_threads_`. On a freshly created context, set both fields to `threads`.

No new JNI entry point and no `NativeBridge` method: the only caller in this wave passes
the same per-process count every turn, so a setter would be unreachable code. Wave C's
`SpeedPlan` task adds `setThreads` at the moment a policy actually changes the value.

- [ ] **Step 4: Run the pair again and require the reuse to show up**

Rebuild and re-run the same class as Task 1 Step 5, then:

```bash
python3 - <<'PY'
import json,glob
d=json.load(open(sorted(glob.glob('corpus/evaluation/results/app-prompt-cost-*.json',key=__import__('os').path.getmtime))[-1],encoding='utf-8'))
runs={r['label']:r for r in d['runs']}
first,second=runs.get('turn1-grammar'),runs.get('turn2-grammar')
print('cap',first.get('context_capacity'),second.get('context_capacity'))
print('reuse turn1',first.get('reused_prefix_tokens'),'turn2',second.get('reused_prefix_tokens'))
print('first_token turn1',first['first_token_ms'],'turn2',second['first_token_ms'])
assert first.get('context_capacity')==second.get('context_capacity'), 'capacity still moving'
assert (second.get('reused_prefix_tokens') or 0) > 0, 'prefix still not reused'
assert second['first_token_ms'] < first['first_token_ms'], 'turn 2 not faster'
PY
```

Expected: all three assertions pass. If `reused_prefix_tokens` is non-zero but `first_token_ms` did not drop by at least 20 %, the reuse is working and the cost is elsewhere — write that conclusion into the baseline note from Task 1, because it re-rates Wave B against Wave C.

- [ ] **Step 5: Keep the JVM-side expectation honest**

In `InferenceContextPolicyTest.kt`, add a test that pins the two sides together so a future change to one cannot silently diverge from the other:

```kotlin
@Test
fun runtimeCapacityIsOneFixedValueSharedWithTheNativeCeiling() {
    assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, contextWindowForRam(2L * 1024 * 1024 * 1024))
    assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, contextWindowForRam(16L * 1024 * 1024 * 1024))
}
```

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.inference.InferenceContextPolicyTest"`
Expected: PASS.

- [ ] **Step 6: Regression-run the full JVM gate and commit**

Run: `cd android && ./gradlew verify`
Expected: PASS, including `NativeInferenceTest` compile and the existing streaming/cancellation suites.

```bash
git add android/app/src/main/cpp android/app/src/main/kotlin/dev/blobs/app/inference \
        android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt \
        android/app/src/test/kotlin/dev/blobs/app/inference/InferenceContextPolicyTest.kt
git commit -m "fix(engine): keep one context capacity so the KV prefix is actually reused"
```

- [ ] **Step 7: Amend the durable docs in the same commit series**

`docs/architecture.md:336` and `:346-348` describe optional exact-prefix reuse; state in that section that the context is now created once at a fixed capacity, and add the measured `turn1`/`turn2` numbers from Step 4. Do not touch `docs/architecture.md:321-322` here — that sentence is the thread policy, owned by Wave C.

---

### Task 4: Put a global budget back on the prompt

**Files:**
- Modify: `android/app/src/main/kotlin/dev/blobs/app/inference/ResearchPrompt.kt` (`companion object` bounds, `withContextBudget`, `create`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/engine/LocalResearchEngine.kt:131-137` (pass the budget)
- Test: `android/app/src/test/kotlin/dev/blobs/app/inference/ResearchPromptTest.kt`

**Interfaces:**
- Consumes: `LibraryStore.retrieve` output (`List<Evidence>`), the existing per-field caps `MAX_SOURCE_EXCERPT_CHARACTERS = 2_000` (`ResearchPrompt.kt:102`) and `MAX_QUESTION_CHARACTERS = 4_000` (`:13`), and the escaper `untrustedReferenceText` (`:97-100`). There is **no** `MAX_SYSTEM_CHARACTERS` in the codebase — do not reference one.
- Produces: `const val MAX_PROMPT_CHARACTERS = 6_000`, `const val MINIMUM_SOURCES_FOR_ANSWER = 1`, and
`fun <T> withContextBudget(question: String, query: String, history: List<Pair<String, String>>, sources: List<Evidence>, promptCharacterBudget: Int = MAX_PROMPT_CHARACTERS, generate: (ResearchPrompt) -> T): T`. `ResearchPrompt.sources` becomes the exact list the prompt carried, so callers persist the same set they showed the model.

`MAX_PROMPT_CHARACTERS` has never existed in code on any branch — `git log --all -S"MAX_PROMPT_CHARACTERS"` returns only two `docs/architecture.md` commits. This task implements a documented invariant for the first time, so it is a behaviour change: the budget, the drop rule, and `docs/architecture.md:251` all get written down in the same change.

- [ ] **Step 1: Write the failing tests**

Add to `ResearchPromptTest.kt`:

```kotlin
private fun passage(index: Int, body: String) = Evidence(
    id = "reference/p$index",
    title = "Passage $index",
    publisher = "Reference",
    url = "local:p$index",
    license = "CC0",
    updated = "2026-01-01",
    saved = "2026-01-02",
    section = "Overview",
    text = body,
    edition = "test"
)

@Test
fun assembledPromptStaysInsideTheCharacterBudget() {
    val sources = (1..6).map { passage(it, "T".repeat(1_900) + it) }
    var seen = 0
    ResearchPrompt.withContextBudget(
        question = "Compare the six",
        query = "Compare the six",
        history = emptyList(),
        sources = sources,
    ) { prompt ->
        seen = prompt.text.length
        assertTrue("prompt was $seen characters", seen <= ResearchPrompt.MAX_PROMPT_CHARACTERS)
        assertTrue(prompt.sources.size < sources.size)
    }
    assertTrue(seen > 0)
}

@Test
fun trimmingKeepsARankedPrefixSoCitationNumbersStayMeaningful() {
    val sources = (1..6).map { passage(it, "T".repeat(1_900) + it) }
    ResearchPrompt.withContextBudget(
        question = "Compare the six", query = "Compare the six",
        history = emptyList(), sources = sources,
    ) { prompt ->
        val kept = prompt.sources
        assertTrue("expected a trimmed prefix, got ${kept.size} of ${sources.size}", kept.size < sources.size)
        assertEquals(sources.subList(0, kept.size), kept)
        for (index in 1..kept.size) {
            assertTrue("missing S$index", prompt.text.contains("<source id=\"S$index\">"))
        }
        assertFalse(prompt.text.contains("<source id=\"S${kept.size + 1}\">"))
    }
}

@Test
fun aMarkerBeyondTheCarriedPrefixIsNotCountedAsACitation() {
    val sources = (1..6).map { passage(it, "T".repeat(1_900) + it) }
    ResearchPrompt.withContextBudget(
        question = "Compare the six", query = "Compare the six",
        history = emptyList(), sources = sources,
    ) { prompt ->
        val carried = prompt.sources.size
        val answer = "The first subject differs. [S$carried] The second does too. [S${carried + 1}]"
        val cited = AnswerText.citedSourceIndices(answer, carried)
        assertEquals(setOf(carried - 1), cited)
    }
}

@Test
fun trimmedPassagesLeaveEvidenceWithThem() {
    val sources = (1..6).map { passage(it, "x".repeat(1_900) + it) }
    ResearchPrompt.withContextBudget(
        question = "Compare the six", query = "Compare the six",
        history = emptyList(), sources = sources,
    ) { prompt ->
        for (source in prompt.sources) {
            assertTrue(prompt.text.contains(source.title.take(16)))
        }
    }
}

@Test
fun budgetTooSmallRefusesTheCompletionInsteadOfGoingSourceless() {
    val sources = listOf(passage(1, "x".repeat(1_900)))
    var calls = 0
    try {
        ResearchPrompt.withContextBudget(
            question = "One source", query = "One source", history = emptyList(),
            sources = sources, promptCharacterBudget = 64,
        ) { calls++; it }
        throw AssertionError("expected refusal")
    } catch (expected: IllegalArgumentException) {
        assertEquals(0, calls)
        assertTrue(expected.message!!.startsWith("PROMPT_BUDGET"))
    }
}
```

The helper mirrors the inline `Evidence(...)` construction already at `ResearchPromptTest.kt:38-49`; add it once as a private member of the test class rather than changing the existing tests.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.inference.ResearchPromptTest"`
Expected: FAIL — `assembledPromptStaysInsideTheCharacterBudget` reports a prompt over 6 000 characters, the prefix test fails because today `create` carries all six and returns `sources = sources` (`:82`), and the refusal test does not compile against a `promptCharacterBudget` parameter.

- [ ] **Step 3: Enforce the budget by removing whole passages**

In `ResearchPrompt.Companion`, add `const val MAX_PROMPT_CHARACTERS = 6_000` and
`const val MINIMUM_SOURCES_FOR_ANSWER = 1`, and give `withContextBudget` (`:30-36`, currently a
pass-through that applies no budget at all) and `create` (`:38-85`) a
`promptCharacterBudget: Int = MAX_PROMPT_CHARACTERS` parameter.

`create` appends, in order: up to two history pairs (`:47-59`), one `<source id="S{index+1}">` block
per source built from the escaped title, section and date plus `excerpt(source.text,
MAX_SOURCE_EXCERPT_CHARACTERS)` (`:60-73`), an optional conversation-topic line (`:74-79`), and the
question tail (`:80`). Cost each source by the strings the prompt would actually carry for it,
**measured after `untrustedReferenceText`**, because `&`/`</>` escaping makes the rendered block longer
than the raw fields and a budget computed on raw lengths under-counts exactly the passages most likely
to be dropped:

```kotlin
private fun blockCharacters(source: Evidence): Int =
    untrustedReferenceText(source.title).length +
        untrustedReferenceText(source.section).length +
        untrustedReferenceText(source.updated).length +
        // the tags, "Section: ", "Source date: " and newlines the block appends
        "<source id=\"S99\">\n\nSection: \nSource date: \n</source>\n".length +
        untrustedReferenceText(excerpt(source.text, MAX_SOURCE_EXCERPT_CHARACTERS)).length

private fun envelopeCharacters(question: String, query: String, history: List<Pair<String, String>>): Int =
    render(question, query, history, emptyList()).text.length

/** Sources arrive ranked best-first, so a budget keeps a prefix — never a middle. */
private fun keptCount(
    blockCharacters: List<Int>,
    fixedCharacters: Int,
    budget: Int,
): Int {
    var total = fixedCharacters
    var kept = 0
    for (characters in blockCharacters) {
        if (total + characters > budget) break
        total += characters
        kept++
    }
    return kept
}
```

`render` is not a helper that exists yet: today's `create` (`:38-85`) *is* the renderer. Split it so the
budget can measure the envelope without re-entering the trimming path — make the current body
`private fun render(question, query, history, sources): ResearchPrompt`, then have `create` call
`plan(...)` and `render(question, query, history, sources.take(kept))`. Without that split,
`envelopeCharacters` calls `create` calls `envelopeCharacters` and the first turn overflows the stack.

Prefix-keeping is not a taste call: the survivor labels stay `S1..Sk` contiguous, and three places index a
marker by **position in the carried list** — `CitedDigest.gate` (`inference/CitedDigest.kt:30-32`),
`AnswerText.citations(text, count)` (`inference/AnswerText.kt:115-131`) and
`AnswerText.citationsMatchTheirPassages` (`:203-205`). Dropping a middle source and renumbering the
survivors would repoint every citation in the turn at a different passage; dropping a middle source and
*keeping* the original numbers would need a per-turn grammar, because
`StructuredAnswer.markerAlternation` (`:37`) is built once from `MAX_ANSWER_SOURCES`. Prefix-keeping
needs neither: `citations()` ignores any marker outside `1..count`, so an answer that still emits `[S5]`
after `S5` was trimmed counts as **uncited**, and the existing gate replaces the prose with the cited
digest. That is the safe direction of failure and it is already tested behaviour.

**Implemented rule, which supersedes the `PROMPT_BUDGET` refusal this task first specified.** A
4 000-character question plus one 2 000-character excerpt already costs more than 6 000, so refusing the
completion would have turned slow answers into failed turns — a visible regression against `goal.md`'s
"a retrieval miss never hides an otherwise usable answer". What ships:

1. `plan(question, query, history, sources, budget)` returns `PromptBudget(sources, history)`; `create`
   renders from it, and `carriedSources(...)` exposes the same decision to callers.
2. When the list does not fit, **retained history is dropped before any passage is cut** — the rule
   `docs/architecture.md` already argued for, since history costs 300+500 per pair while a passage costs
   up to 2 053.
3. If it still does not fit, the prompt carries `MINIMUM_SOURCES_FOR_ANSWER = 1` passage and no history.
   An empty passage set then comes only from a retrieval miss, never from the budget.
4. `keptCount`/`fitCount` is the pure prefix scan; the floor lives in `plan`, so the scan can report zero
   and let history go first.

Because the engine calls `carriedSources` immediately after retrieval and before `onEvidence`, the reader,
the prompt, the citations and the stored turn all describe the same set — no source is ever displayed that
the model was not given.

Finally record the bound where it was claimed: `docs/architecture.md:250-262` already says
`ResearchPrompt.MAX_PROMPT_CHARACTERS` caps the built text at 6 000 characters, but also quoted a 2 000
question cap, a 300–1 200 passage band, and "excerpts sized from the space left" — none of which existed.
That paragraph was rewritten to describe what the code now does.

- [ ] **Step 4: Make every consumer describe the carried prefix**

In `LocalResearchEngine.run` the `ResearchPrompt.withContextBudget(...)` call at `:131-136` gains no new
argument (the default is the shared constant), and everything downstream of `:136` switches from the
pre-trim `evidence` list to `prompt.sources`:

| Where | Now | After |
| --- | --- | --- |
| `CitedDigest.gate(answer, evidence, ...)` (`:169`) | `evidence` | `prompt.sources` |
| `ResearchOutcome.evidence` (`:174`) | `evidence` | `prompt.sources` |
| `ResearchUsage.sourceCount` (`:187`) | `evidence.size` | `prompt.sources.size` |
| the grammar handed to JNI | `StructuredAnswer.grammar`, fixed at S1–S6 | unchanged |

`prompt` is scoped inside the `withContextBudget` lambda, so hoist the carried list to a local declared
next to `promptCharacters` (`:113`) the same way `historyPairCount` already is — that is the smallest
change that keeps one retrieval and one completion per question. Leaving the grammar fixed is
deliberate: `AnswerText.citations(text, count)` ignores markers above the carried count, so a stale
`[S6]` degrades to an uncited answer and the gate top-ups the digest, rather than attributing a claim to
a passage that was never sent. Do not add a per-turn grammar to "fix" that; it costs a grammar compile on
every turn and buys nothing the validator doesn't already enforce.

- [ ] **Step 5: Run the unit tests, then the device gate**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.inference.ResearchPromptTest"`
Expected: PASS (the pre-existing suite plus the 4 new tests).
Run: `cd android && ./gradlew verify`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/dev/blobs/app/inference/ResearchPrompt.kt \
        android/app/src/main/kotlin/dev/blobs/app/engine/LocalResearchEngine.kt \
        android/app/src/test/kotlin/dev/blobs/app/inference/ResearchPromptTest.kt
git commit -m "fix(engine): bound the prompt with a global character budget"
```

---

### Task 5: Make the captures able to answer the 8× question

**Files:**
- Create: `android/app/src/androidTest/kotlin/dev/blobs/app/RecordFields.kt` (one shared header writer)
- Modify: `android/app/src/androidTest/kotlin/dev/blobs/app/PublicResearchWorkflowTest.kt` (report header + per-case fields)
- Modify: `android/app/src/androidTest/kotlin/dev/blobs/app/AppJourneyPerformanceTest.kt` (header + per-turn prompt shape)
- Modify: `android/app/src/androidTest/kotlin/dev/blobs/app/NativeInferenceTest.kt` (both turn reports)

**Tree note:** the two captures this task first named, `GenerationCostTest.kt` and
`FollowUpJourneyTest.kt`, exist only as untracked files in the shared checkout `D:/rimbara/blobs`; neither
is on `feat/offline-research-engine`, so wiring them here would commit another session's work. Their
equivalents on this branch are the turn reports inside `NativeInferenceTest` (`recordOfflineChatFlow` and
`chat-no-sources`), which already read `container.work` and write the same identity keys.

**Interfaces:**
- Consumes: `Build.FINGERPRINT`, `Build.VERSION.SDK_INT`, `inferenceThreads(...)` (`inference/NativeBridge.kt`), `container.work.lastPerformanceSnapshot` (`WorkPerformanceSnapshot.kt`), `NativeTurnTimings.json()`.
- Produces: `internal fun JSONObject.putReportHeader(context: Context, runId: String): JSONObject` and `internal fun installedApkSha256(context: Context): String` in `RecordFields.kt`, writing `format`, `run_id`, `application_id`, `installed_apk_sha256`, `device_acceptance`, `fingerprint`, `api`, `abi`, `inference_threads`. Android's `org.json.JSONObject` has no `putAll`, which is why this is an extension that fills the caller's object rather than a builder that returns one. (`allowed_cores` is deliberately absent — that value arrives with Wave C's governor, and writing a placeholder for it here would be a field that always lies.)

- [ ] **Step 1: Write the shared header writer**

```kotlin
package dev.blobs.app

import android.app.Application
import android.content.Context
import android.os.Build
import dev.blobs.app.inference.inferenceThreads
import org.json.JSONObject

/** Every capture report must carry these keys; goal.md bars speed claims without them. */
internal fun JSONObject.putReportHeader(context: Context, runId: String): JSONObject = apply {
    put("format", 1)
    put("run_id", runId)
    put("application_id", context.packageName)
    put("installed_apk_sha256", installedApkSha256(context))
    put("fingerprint", Build.FINGERPRINT)
    put("api", Build.VERSION.SDK_INT)
    put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
    put("device_acceptance", DEVICE_ACCEPTANCE_UNVERIFIED)
    put("inference_threads", inferenceThreads(Runtime.getRuntime().availableProcessors()))
}

/** Hashed from the APK the instrumentation actually runs, not the file on the build host. */
internal fun installedApkSha256(context: Context): String
```

`installedApkSha256` is written here as SHA-256 over `context.applicationInfo.sourceDir`; the private
copies in the untracked captures can be deleted when that lane merges, not before. Keep each file's
existing extra-key spellings (`airplane_mode` vs `airplane_mode_enabled` differ between captures today)
so old reports stay comparable where a key already exists.

- [ ] **Step 2: Use it in the three report builders**

Replace each hand-built header (`NativeInferenceTest.kt`'s `recordOfflineChatFlow` and `chat-no-sources`
reports, `AppJourneyPerformanceTest.kt`'s report, `PublicResearchWorkflowTest.kt`'s report) with
`putReportHeader(context, runId)` plus that capture's extra keys (`cases_sha256`, `suite_id`, `scope`,
`selected_model`, `packages`, `model_sha256`, `library_sha256`). The library-validation and
package-install reports keep their own headers: they are not turn timings and Wave A does not claim them.

- [ ] **Step 3: Add the per-case prompt shape the shelf suite never recorded**

Where the shelf runner already reads `lastPerformanceSnapshot` (`PublicResearchWorkflowTest.kt`), extend
that block with `prompt_characters`, `source_count`, `history_pair_count`, `generation_attempts`,
`output_token_limit` and `native_stages`, and assert on a `COMPLETE` turn that
`prompt_characters <= ResearchPrompt.MAX_PROMPT_CHARACTERS` and that the turn's stored evidence count
equals `source_count` — the two claims the prompt bound actually makes. Add the same keys to
`AppJourneyPerformanceTest`'s per-turn object and `NativeInferenceTest`'s zero-source report, so the two
paths become comparable field-for-field.

- [ ] **Step 4: Prove the fields exist and are non-zero**

Run one shelf-suite case and one journey case on the emulator, pull both reports, then:

```bash
python3 - <<'PY'
import json,glob
required={'installed_apk_sha256','fingerprint','api','device_acceptance','inference_threads'}
case={'prompt_characters','source_count','history_pair_count','generation_attempts'}
for p in sorted(glob.glob('corpus/evaluation/results/**/*.json',recursive=True))[-4:]:
    d=json.load(open(p,encoding='utf-8'))
    missing=required-set(d)
    items=d.get('results') or d.get('turns') or d.get('runs') or []
    case_missing=set().union(*[{k for k in case if k not in (i or {})} for i in items]) if items else case
    print(p, 'header missing', missing or '-', '| case missing', case_missing or '-',
          '| acceptance', d.get('device_acceptance'))
PY
```

Expected: no missing keys on the new captures, `device_acceptance: unverified`, and `prompt_characters > 0` on every turn.

- [ ] **Step 5: Run the attribution experiment the spec is waiting on**

Same configuration, one question, one library set, three sources, measured through both paths: the `GenerationCostTest` direct call and the `PublicResearchWorkflowTest` UI turn. Then compare `first_token_ms` (direct) with `first_visible_text_elapsed_ms − retrieval_elapsed_ms` (UI) at equal `prompt_characters` and equal `context_capacity`. Expected deliverable is a number, not a narrative: if the two agree within ~2×, the shelf suite's 16–26 ms/char is real user cost and Wave B's warm path is the priority; if they differ by more, the difference lives in the Kotlin path and gets its own task.

- [ ] **Step 6: Write the result into the durable docs, then commit**

Append the finding to `docs/engineering-roadmap.md` near the existing thread/timing records, and one line to `CHANGELOG.md` naming the artifact ids and the emulator profile. Keep `docs/superpowers/` as the internal working document — the durable claim belongs to architecture/roadmap.

```bash
git add android/app/src/androidTest/kotlin/dev/blobs/app docs/engineering-roadmap.md CHANGELOG.md
git commit -m "test(device): name the build, device and prompt shape in every capture"
```

---

## Wave A exit criteria

- Every stage of a turn has a number attached, and the stage sum reconciles with `elapsed_ms` within 20 %.
- `context_capacity` is one fixed value per model, `reused_prefix_tokens > 0` on the second turn, and turn 2's `first_token_ms` is at least 20 % below turn 1's — or the baseline note records why that failed and what it re-rates.
- A shelf-prompt turn cannot exceed `MAX_PROMPT_CHARACTERS`, the evidence list equals what the model was given, and a squeezed budget still carries one passage rather than refusing the answer or downgrading it to `unsourced`.
- Every new capture names its APK digest, device fingerprint, API level, ABI, threads, prompt size and source count, and says `device_acceptance: unverified`.
- `bash ./gradlew verify` passes, and the paired measurement from Task 5 Step 5 exists as a number in the roadmap.

**Deferred on purpose:** `allowedCores`, `SpeedPlan`, ADPF, `GGML_LLAMAFILE`, the 200-token bound, Settings, the warm path and the evidence-on-screen change. They are Waves B and C in the spec's §12, sequenced after this wave's numbers exist.
