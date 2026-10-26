# Blobs SpeedGovernor — Wave B (warm path, retrieval insurance, evidence on screen) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** take model load, prefix priming and library open out of the turn the user waits for, keep one unreadable library from killing a turn, and show the passages the app already has while the answer is still being written.

**Architecture:** three pieces, in that order. `data/work/WorkController` decides *when* background work is allowed and owns the idle window; `data/library` owns the reusable SQLite boundary and the per-library fan-out; `feature/chat` renders evidence as reading material with a `pending` state. The native layer gains two no-generation entry points (`prefaultModel`, `prepareModel`) and one discarded completion used only to seed the KV prefix. Nothing in this wave decides thread counts, touches ADPF, or adds a Settings row — that is Wave C.

**Tech Stack:** Kotlin, coroutines on the existing `Dispatchers.IO` work scope, `androidx.sqlite` bundled driver, Jetpack Compose + `compose-ui-test` in `androidTest`, JUnit 4 unit tests, `corpus/evaluation/results/*.json` captures.

**Spec:** [docs/superpowers/specs/2026-10-25-blobs-speed-governor-design.md](../specs/2026-10-25-blobs-speed-governor-design.md) — §6.4 (warm path), §6.11 (fan-out), §6.7 (perceived latency), §7.1 (shelf arithmetic is a caching fix), §9, §9.1, slices 3, 3b, 4.

**Depends on Wave A:** [2026-10-26-blobs-speed-wave-a.md](./2026-10-26-blobs-speed-wave-a.md). Tasks 2 and 3 here assert with Wave A's `model_cached`, `reused_prefix_tokens` and `timings` fields, and Wave A's Task 5 emitter supplies the device/APK keys. Do not start this plan before Wave A is merged.

## Global Constraints

- `minSdk = 28`, `compileSdk = 37`, `ndkVersion = "27.1.12297006"`, ABIs `arm64-v8a` + `x86_64` (`android/app/build.gradle.kts:12,14,27-33`).
- English only in source, identifiers, comments, developer docs and UI copy (`docs/rules/language.md`); new strings go in `res/values/product.xml`.
- One retrieval pass and one completion per question on a bounded prompt (`goal.md:13-16`). The prefix-prime completion in Task 3 belongs to **no** question, is discarded, and must never reach state, Room, or the screen.
- New dependencies are not allowed in this wave: no `lifecycle-process`, no `kotlinx-coroutines-test` addition (the idle trigger uses the `AppViewModel` scope that already exists).
- Every capture must carry `installed_apk_sha256`, `fingerprint`, `api`, `abi`, `inference_threads`, `prompt_characters`, `source_count`, and `device_acceptance: "unverified"` (Wave A Task 5 supplies the shared header). No phone claim exists: `docs/engineering-roadmap.md:308-313` records no phone-class device.
- Native loading/KV/memory changes need target-device acceptance (`docs/rules/testing-and-evaluation.md`), so Tasks 2 and 3 end with an emulator record explicitly labelled unverified, never a speed claim.
- The gate is `bash ./gradlew verify` from `android/`; device gate is `connectedDebugAndroidTest`, which excludes `@ManualCheck` (`android/app/build.gradle.kts:26`).
- One device-heavy run at a time; check `/proc/loadavg` and free memory first; pull every report immediately because the suite uninstalls the app.

---

### Task 1: An idle window that background work may legitimately use

**Files:**
- Create: `android/app/src/main/kotlin/dev/blobs/app/data/work/WarmGate.kt`
- Create: `android/app/src/test/kotlin/dev/blobs/app/data/work/WarmGateTest.kt`
- Modify: `android/app/src/main/kotlin/dev/blobs/app/ui/AppViewModel.kt` (`init`, near the existing `container.work.state` reads)

**Interfaces:**
- Consumes: `WorkState` fields `ready`, `busy`, `transferActive`, `downloading` (`data/WorkState.kt:6-27`).
- Produces: `internal object WarmGate { fun isIdle(state: WorkState): Boolean; fun <T> onceIdle(states: Flow<WorkState>, block: suspend () -> T): Flow<T?> }` — the second form is not required; if only `isIdle` is used, keep just `isIdle`.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.blobs.app.data.work

import dev.blobs.app.data.WorkState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmGateTest {
    @Test
    fun onlyAForegroundIdleSessionMayWarm() {
        assertTrue(WarmGate.isIdle(WorkState(ready = true)))
        assertFalse(WarmGate.isIdle(WorkState(ready = false)))
        assertFalse("a running turn owns the runtime", WarmGate.isIdle(WorkState(ready = true, busy = true)))
        assertFalse("a transfer owns the flash", WarmGate.isIdle(WorkState(ready = true, transferActive = true)))
        assertFalse(WarmGate.isIdle(WorkState(ready = true, downloading = true)))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.data.work.WarmGateTest"`
Expected: FAIL — unresolved reference `WarmGate`.

- [ ] **Step 3: Implement the predicate**

```kotlin
package dev.blobs.app.data.work

import dev.blobs.app.data.WorkState

/** The only moment background speed work may run: foreground, loaded, nothing else active. */
internal object WarmGate {
    fun isIdle(state: WorkState): Boolean =
        state.ready && !state.busy && !state.transferActive && !state.downloading
}
```

- [ ] **Step 4: Re-run the test, then the gate**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.data.work.WarmGateTest"` then `./gradlew verify`
Expected: PASS, PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/dev/blobs/app/data/work/WarmGate.kt \
        android/app/src/test/kotlin/dev/blobs/app/data/work/WarmGateTest.kt
git commit -m "feat(work): define the idle window background speed work may use"
```

---

### Task 2: Prefault and pre-load the model outside the turn

**Files:**
- Modify: `android/app/src/main/cpp/local_model_runtime.cpp` (add `prefault_model`, reuse `load_model` at `:117-161`) and `local_model_runtime.h`
- Modify: `android/app/src/main/cpp/blobs.cpp` (two JNI entries, mirroring `releaseModel` at `:32-51`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/inference/NativeBridge.kt`
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/work/ResearchWork.kt` (expose a warm entry next to `releaseInferenceModel` at `:322`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/ui/AppViewModel.kt` (trigger on the idle window from Task 1)
- Test: `android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt` (new labelled run `warm-then-turn`)

**Interfaces:**
- Consumes: `WarmGate.isIdle`, `NativeBridge` instance held by `LocalResearchEngine`, Wave A's `NativeTurnTimings.loadMs` / `modelWasCached`.
- Produces: `internal fun prefaultModel(path: String): Boolean` and `internal fun prepareModel(path: String): Boolean` on `NativeBridge`; `suspend fun warmInferenceModel(path: String): Boolean` on `ResearchWork`.

- [ ] **Step 1: Write the failing capture expectation**

In `GenerationCostTest.kt`, next to the existing labelled runs, add a run that measures a real first turn *after* warming, so the absence of a load is visible:

```kotlin
val warmed = bridge.prepareModel(model.path) && bridge.prefaultModel(model.path)
assertTrue("native warm entry points reported failure", warmed)
measure("warm-turn1-grammar", threads, constrained = true, tokenLimit = limit)
```

Expected before implementing: unresolved references to `prepareModel` / `prefaultModel`.

- [ ] **Step 2: Add the native prefault**

In `local_model_runtime.cpp`, a file-scoped helper using the only advisory API Bionic documents here (`posix_fadvise`, `<fcntl.h>`; `MADV_WILLNEED` is not in Bionic's public headers):

```cpp
bool prefault_model(const std::string &path) {
    const int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return false;
    }
    struct stat file {};
    const bool readable = ::fstat(fd, &file) == 0 && file.st_size > 0;
    const bool advised = readable &&
        ::posix_fadvise(fd, 0, 0, POSIX_FADV_WILLNEED) == 0;
    ::close(fd);
    return advised;
}
```

Export it as `blobs::prefault_model` in `local_model_runtime.h` and add the JNI mirror in `blobs.cpp`:

```cpp
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_blobs_app_inference_NativeBridge_prefaultModelNative(JNIEnv *env, jobject, jbyteArray path) {
    return utf8(env, path).empty() ? JNI_FALSE
        : (blobs::prefault_model(utf8(env, path)) ? JNI_TRUE : JNI_FALSE);
}
```

- [ ] **Step 3: Add the native pre-load**

`prepareModel` must not generate and must not touch the KV cache; it calls the model cache that `generate()` already uses:

```cpp
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_blobs_app_inference_NativeBridge_prepareModelNative(JNIEnv *env, jobject, jbyteArray path) {
    const auto model_path = utf8(env, path);
    if (model_path.empty()) {
        return JNI_FALSE;
    }
    bool cached = false;
    return blobs::prepare_model(model_path, &cached) ? JNI_TRUE : JNI_FALSE;
}
```

with `blobs::prepare_model(const std::string &path, bool *was_cached)` in `local_model_runtime.cpp` taking `std::lock_guard` on the same mutex the load path already uses and calling the existing `load_model(path, &cached)`; extend `load_model` with the `bool*` out-parameter that records a cache hit (Wave A added this for timings — reuse it, do not add a second flag).

- [ ] **Step 4: Kotlin boundary, and no eager container construction**

```kotlin
internal fun prefaultModel(path: String): Boolean =
    prefaultModelNative(path.toByteArray(Charsets.UTF_8))

internal fun prepareModel(path: String): Boolean =
    prepareModelNative(path.toByteArray(Charsets.UTF_8))
```

plus the two `private external fun` declarations. In `ResearchWork`, next to `releaseInferenceModel` (`:322`):

```kotlin
suspend fun warmInferenceModel(path: String): Boolean =
    withContext(Dispatchers.IO) {
        if (stopped || session.isBusy()) return@withContext false
        engine.prefaultModel(path) && runCatching { engine.prepareModel(path) }.isSuccess
    }
```

`LocalResearchEngine` gains the two one-line delegations to its existing `runtime: NativeBridge`.

- [ ] **Step 5: Trigger it from the idle window only**

In `AppViewModel`'s `init`, after the flows the class already collects, add a single collector that never re-enters while busy:

```kotlin
private var warming = false

private fun warmWhenIdle() {
    viewModelScope.launch(Dispatchers.Default) {
        container.work.state
            .map { WarmGate.isIdle(it) to it }
            .collect { (idle, state) ->
                val model = selectedModel() ?: return@collect
                if (!idle || warming || state.modelWarmed) return@collect
                warming = true
                container.work.warmInferenceModel(model.path)
                warming = false
            }
    }
}
```

Add `val modelWarmed: Boolean = false` to `WorkState` and set it in the same `session.update` that reports a successful warm; that keeps one warm attempt per model selection rather than one per state emission.

Two more triggers, both from the spec's §6.4 step 0 and the trim path:

- **Right after a package activates.** `PackageInstaller.install` commits by
  `staging.renameTo(target)` (`data/packages/PackageInstaller.kt:214`, with
  `activated = true` at `:216`). After a **model** package activates, request one warm
  attempt (`container.work.warmInferenceModel(path)`) — the file's bytes are already in the
  page cache at that instant, and the first question a user asks after installing a model
  is exactly the cold turn this removes. Do not make the install depend on it: wrap in
  `runCatching`, and an install stays successful even if warming fails.
- **After a trim.** `WorkController.trimInferenceMemory()` (`data/work/ResearchWork.kt:324`)
  releases the model, so it must also set `modelWarmed = false` in the same update. Without
  that flag reset the idle collector never warms again and the next turn silently pays the
  full load — the current, unwanted behaviour this task exists to delete.

- [ ] **Step 6: Build, run the capture, and read the number**

Run `cd android && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`, install only the test APK, then `am instrument` the cost class as in Wave A Task 2 Step 5. Pull the report and check:

```bash
python3 - <<'PY'
import json,glob
d=json.load(open(sorted(glob.glob('corpus/evaluation/results/app-prompt-cost-*.json',key=__import__('os').path.getmtime))[-1],encoding='utf-8'))
runs={r['label']:r for r in d['runs']}
warm=runs.get('warm-turn1-grammar') or {}
print('warm turn1 cached', warm.get('model_cached'), 'load_ms', (warm.get('timings') or {}).get('load_ms'))
assert warm.get('model_cached') is True, 'warm-up did not reach the native cache'
PY
```

Expected: `model_cached true` and `timings.load_ms == 0` on the warm run.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/cpp android/app/src/main/kotlin/dev/blobs/app/inference \
        android/app/src/main/kotlin/dev/blobs/app/data/work \
        android/app/src/main/kotlin/dev/blobs/app/ui/AppViewModel.kt \
        android/app/src/main/kotlin/dev/blobs/app/data/WorkState.kt \
        android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt
git commit -m "feat(inference): pre-load and prefault the model during foreground idle"
```

---

### Task 3: Prime the KV prefix with a completion nobody sees

**Files:**
- Modify: `android/app/src/main/kotlin/dev/blobs/app/engine/LocalResearchEngine.kt` (a `prime` entry that calls `runtime.generate` with `limit = 1`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/work/ResearchWork.kt` (call it inside the warm sequence, after Task 2)
- Modify: `android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt` (`prime-then-turn` labelled run)
- Test: `android/app/src/test/kotlin/dev/blobs/app/inference/ResearchPromptTest.kt` (system prompt bytes are stable, already partially covered — extend)

**Interfaces:**
- Consumes: `ResearchPrompt.system` (`:22-30`), `systemPromptBytes` (`LocalResearchEngine.kt:76`), `NativeBridge.generate(...)`, Wave A's `reused_prefix_tokens`.
- Produces: `internal fun primePrefix(modelPath: String): Int` on `LocalResearchEngine`, returning reused prefix tokens or `-1`; `ResearchWork.warmInferenceModel` calls it after `prepareModel`.

- [ ] **Step 1: Pin the byte-stability precondition first**

```kotlin
@Test
fun systemPromptIsTheSameBytesEveryTurn() {
    val first = ResearchPrompt.system.toByteArray(Charsets.UTF_8)
    val second = ResearchPrompt.system.toByteArray(Charsets.UTF_8)
    assertArrayEquals(first, second)
    assertTrue(ResearchPrompt.system.length <= ResearchPrompt.MAX_SYSTEM_CHARACTERS)
}
```

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.inference.ResearchPromptTest"`
Expected: PASS (this is the precondition the priming depends on; if it ever fails, priming is useless and must be deleted).

- [ ] **Step 2: Add the priming call**

```kotlin
/** Seeds the KV prefix with the byte-stable system prompt. Output is discarded. */
internal fun primePrefix(modelPath: String): Int {
    val prompt = ResearchPrompt.withContextBudget(
        question = "ok", query = "ok", history = emptyList(), sources = emptyList(),
    ) { it }
    val discarded = StringBuilder()
    runtime.generate(
        modelPath.toByteArray(Charsets.UTF_8),
        systemPromptBytes,
        prompt.text.toByteArray(Charsets.UTF_8),
        StructuredAnswer.grammarFor(0).toByteArray(Charsets.UTF_8),
        grammarRootBytes,
        1,
        TokenSink { bytes, size ->
            discarded.append(String(bytes, 0, size, Charsets.UTF_8))
            false // never keep more than one token
        },
    )
    return runtime.lastTurnTimings()?.reusedPrefixTokens ?: -1
}
```

The `false` return stops generation after the first token, which is exactly how the existing sink signals "stop" (`BoundedAnswerBytes` returns false at the byte cap). `discarded` exists only so the compiler proves the bytes are dropped; add no logging of its content.

- [ ] **Step 3: Assert the integrity rule with a test, not a comment**

In `android/app/src/test/kotlin/dev/blobs/app/data/work/`, add a test over a fake engine that calls `warmInferenceModel` and asserts: `WorkState.text` stays `""`, `WorkState.turnId` stays `""`, and no `conversations.begin` was invoked. Mirror the fake style the neighbouring `WorkStateTest.kt` uses.

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.data.work.*"`
Expected: FAIL until the wiring asserts are added to the warm path, then PASS.

- [ ] **Step 4: Wire it and measure**

`ResearchWork.warmInferenceModel` becomes: `prefaultModel` → `prepareModel` → `engine.primePrefix(path)`; ignore the returned count except to record it in the next capture. In `GenerationCostTest.kt` add:

```kotlin
bridge.lastTurnTimings() // after warm
val primed = engine.primePrefix(model.path)
measure("prime-turn1-grammar", threads, constrained = true, tokenLimit = limit)
```

Run the instrumented class, pull the report, and assert the reused prefix is non-zero on the first real turn:

```bash
python3 - <<'PY'
import json,glob
d=json.load(open(sorted(glob.glob('corpus/evaluation/results/app-prompt-cost-*.json',key=__import__('os').path.getmtime))[-1],encoding='utf-8'))
r={x['label']:x for x in d['runs']}['prime-turn1-grammar']
print('reuse', r.get('reused_prefix_tokens'), 'first_token_ms', r['first_token_ms'])
assert (r.get('reused_prefix_tokens') or 0) > 0, 'priming did not seed a reusable prefix'
PY
```

Expected: assertion passes. Record `first_token_ms` in the roadmap next to the unprimed value from Wave A so the saving is a number, not an adjective.

- [ ] **Step 5: Gate, then commit**

Run: `cd android && ./gradlew verify`
Expected: PASS.

```bash
git add android/app/src/main/kotlin/dev/blobs/app/engine android/app/src/main/kotlin/dev/blobs/app/data/work \
        android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt \
        android/app/src/test/kotlin/dev/blobs/app
git commit -m "feat(engine): prime the KV prefix on foreground idle, discarding the output"
```

---

### Task 4: Stop re-opening every library every turn

**Files:**
- Create: `android/app/src/main/kotlin/dev/blobs/app/data/library/LibraryConnectionCache.kt`
- Create: `android/app/src/test/kotlin/dev/blobs/app/data/library/LibraryConnectionCacheTest.kt`
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/library/LibrarySqlite.kt` (`open` at `:10-13`), `LibraryStore.kt` (the `flatMap` at `:71-92`)
- Test: `android/app/src/test/kotlin/dev/blobs/app/data/library/LibraryConnectionCacheTest.kt`

**Interfaces:**
- Consumes: `PackageEntity(key, version, sha256, path)`, `BundledSQLiteDriver`.
- Produces: `internal class LibraryConnectionCache(private val open: (String) -> SQLiteConnection, private val maximum: Int = 8) { fun acquire(key: String, version: String, sha256: String, path: String): SQLiteConnection; fun evict(key: String); fun evictAll() }`, and `LibraryStore(private val openConnection: (String) -> SQLiteConnection = { path -> LibrarySqlite.open(path) })` — the injectable opener is what makes Task 5's failure test possible without a database.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.blobs.app.data.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A connection that records closure, so eviction is observable without a database. */
private class FakeConnection(val path: String) : SQLiteConnection {
    var closed = false
    override fun close() { closed = true }
    override fun prepare(sql: String): SQLiteStatement = throw UnsupportedOperationException(sql)
}

class LibraryConnectionCacheTest {
    private val opened = mutableListOf<FakeConnection>()

    private fun cache(maximum: Int = 2) = LibraryConnectionCache(
        open = { path -> FakeConnection(path).also { opened += it } },
        maximum = maximum,
    )

    @Test
    fun oneConnectionPerPackageAcrossTurns() {
        val subject = cache()
        val first = subject.acquire("a", "2026-10.1", "sha-a", "/data/a")
        assertSame(first, subject.acquire("a", "2026-10.1", "sha-a", "/data/a"))
        assertEquals(1, opened.size)
    }

    @Test
    fun aVersionChangeNeverReusesTheOldConnection() {
        val subject = cache()
        val before = subject.acquire("a", "2026-10.1", "sha-a", "/data/a") as FakeConnection
        val after = subject.acquire("a", "2026-10.2", "sha-b", "/data/a")
        assertTrue("stale connection must be closed", before.closed)
        assertTrue(before !== after)
    }

    @Test
    fun theLeastUsedEntryIsEvictedAndClosed() {
        val subject = cache(maximum = 2)
        val a = subject.acquire("a", "1", "sa", "/data/a") as FakeConnection
        subject.acquire("b", "1", "sb", "/data/b")
        subject.acquire("c", "1", "sc", "/data/c")
        assertTrue("the least recently used connection must be closed", a.closed)
        assertEquals(2, opened.count { !it.closed })
    }

    @Test
    fun removingAPackageDropsItsConnection() {
        val subject = cache()
        val a = subject.acquire("a", "1", "sa", "/data/a") as FakeConnection
        subject.evict("a")
        assertTrue(a.closed)
    }
}
```

`SQLiteConnection` is an interface; if this pin of `androidx.sqlite` declares more abstract members than the two above, implement them as `throw UnsupportedOperationException(...)` in `FakeConnection` rather than switching to a Robolectric or on-device database — the unit under test is the cache's bookkeeping, not SQLite.

- [ ] **Step 2: Run them and confirm the failure**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.data.library.LibraryConnectionCacheTest"`
Expected: FAIL — unresolved reference.

- [ ] **Step 3: Implement the cache**

```kotlin
package dev.blobs.app.data.library

import androidx.sqlite.SQLiteConnection

/**
 * Read-only connections are per-turn today: every library re-opens, re-runs its PRAGMAs and
 * re-prepares its schema probe, which is the dominant cost of a wide shelf. Identity is the
 * package version and digest, so an upgrade can never be served by the previous connection.
 */
internal class LibraryConnectionCache(
    private val open: (String) -> SQLiteConnection,
    private val maximum: Int = 8,
) {
    private class Entry(val version: String, val sha256: String, val connection: SQLiteConnection)

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun acquire(key: String, version: String, sha256: String, path: String): SQLiteConnection {
        entries[key]?.let { existing ->
            if (existing.version == version && existing.sha256 == sha256) {
                entries.remove(key)
                entries[key] = existing
                return existing.connection
            }
            existing.connection.close()
        }
        while (entries.size >= maximum) {
            val oldest = entries.keys.first()
            entries.remove(oldest)?.connection?.close()
        }
        val created = Entry(version, sha256, open(path))
        entries[key] = created
        return created.connection
    }

    @Synchronized
    fun evict(key: String) {
        entries.remove(key)?.connection?.close()
    }

    @Synchronized
    fun evictAll() {
        entries.values.forEach { it.connection.close() }
        entries.clear()
    }
}
```

- [ ] **Step 4: Use it, and hold a connection on one thread at a time**

In `LibraryStore`, own one cache and take a per-call guard so a connection is never shared across threads (the `androidx.sqlite` connection is not thread-safe; Wave C's fan-out depends on this holding):

```kotlin
private val connections = LibraryConnectionCache(open = { path -> LibrarySqlite.open(path) })
private val guards = ConcurrentHashMap<String, Any>()
```

Inside `retrieveFromLibrary`, wrap the body in `synchronized(guards.computeIfAbsent(pack.key) { Any() }) { ... }` and obtain the connection with `connections.acquire(pack.key, pack.version, pack.sha256, pack.path)`. Call `connections.evict(pack.key)` from the package-removal path in `PackageStore`/`PackageInstaller`, and `connections.evictAll()` from `WorkController.trimInferenceMemory`'s caller side so a memory trim releases them too.

- [ ] **Step 5: Prove the cost actually moved**

Run the journey capture (`FollowUpJourneyTest`, 3 libraries if staged, otherwise the existing 1-library set) and compare the first-turn `retrieval_elapsed_ms` against the recorded cold values 1323 / 2211 / 2794 ms:

```bash
python3 - <<'PY'
import json,glob
p=sorted(glob.glob('corpus/evaluation/results/follow-up-journey-*.json',key=__import__('os').path.getmtime))[-1]
d=json.load(open(p,encoding='utf-8'))
turns=[t['performance'] for t in d['turns'] if t.get('performance')]
print(p, [t['retrieval_elapsed_ms'] for t in turns])
assert turns[0]['retrieval_elapsed_ms'] < 1000, 'first-turn retrieval still paying cold opens'
PY
```

Expected: turn-1 retrieval below 1 000 ms on the emulator (the warming is what should move it), every turn non-zero, and the pull of `prompt_characters` from Wave A present in the same block.

- [ ] **Step 6: Gate, then commit**

Run: `cd android && ./gradlew verify`
Expected: PASS.

```bash
git add android/app/src/main/kotlin/dev/blobs/app/data/library android/app/src/test/kotlin/dev/blobs/app/data/library
git commit -m "perf(library): reuse read-only library connections across turns"
```

---

### Task 5: One unreadable library must not end the turn

**Files:**
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/library/LibraryStore.kt` (the `flatMap` at `:71-92`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/engine/LocalResearchEngine.kt` (`ResearchOutcome` gains the failed package keys)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/work/ResearchWork.kt` (persist the notice alongside the turn)
- Test: `android/app/src/test/kotlin/dev/blobs/app/data/library/LibraryStoreFailureTest.kt`

**Interfaces:**
- Consumes: `LibraryConnectionCache.acquire`, `PackageEntity`.
- Produces: `internal data class RetrievalOutcome(val evidence: List<Evidence>, val failedPackages: List<String>)` and `LibraryStore.retrieve` keeping its current signature while returning evidence only, with failures surfaced through `retrieveToOutcome`.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.blobs.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStoreFailureTest {
    @Test
    fun oneUnreadableLibraryStillReturnsTheOthersEvidence() {
        val store = LibraryStore(
            open = { path -> if (path.contains("broken")) throw IllegalStateException("OPEN") else FakeConnection(path) },
        )
        val outcome = store.retrieveToOutcome(
            packages = listOf(packageEntity("good", "/data/good"), packageEntity("broken", "/data/broken")),
            question = "how do trees cool a street",
            scope = null,
            maximumResults = 3,
        )
        assertTrue("expected evidence from the healthy library", outcome.evidence.isNotEmpty())
        assertEquals(listOf("broken"), outcome.failedPackages)
    }
}
```

Match `packageEntity` and `FakeConnection` to the fakes Task 4's test already introduces — reuse them rather than writing a second pair.

- [ ] **Step 2: Run it and watch it fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.data.library.LibraryStoreFailureTest"`
Expected: FAIL — a thrown `IllegalStateException` escapes `retrieve`.

- [ ] **Step 3: Isolate the per-library failure**

Replace the plain `flatMap` body with a per-library `runCatching`, keeping order and the `order` index that `selectEvidence` ranks by:

```kotlin
val failures = mutableListOf<String>()
val candidates = enabledLibraries.flatMap { pack ->
    runCatching { retrieveFromLibrary(pack, searchQuestion, lexicalQuery, titleTerms, documentIds, maximumResults) }
        .getOrElse {
            connections.evict(pack.key)
            failures += pack.key
            emptyList()
        }
}
```

`retrieveToOutcome` returns `RetrievalOutcome(selectEvidence(candidates, titleTerms, maximumResults), failures.toList())`; the existing `retrieve` delegates and drops the failures so every current caller keeps compiling.

- [ ] **Step 4: Tell the reader, without inventing an answer**

`LocalResearchEngine.run` uses `retrieveToOutcome`, adds `failedPackages` to `ResearchOutcome`, and `ResearchWork` stores a non-blocking notice string on the turn (reuse the existing `error`/notice column pattern rather than a new column if one already fits). The turn must still complete with evidence from the healthy libraries; `docs/bounty-contract.md` treats a retrieval miss as a legitimate turn, and a partially unreadable shelf is one.

- [ ] **Step 5: Gate and commit**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.data.library.*"` then `./gradlew verify`
Expected: PASS, PASS.

```bash
git add android/app/src/main/kotlin/dev/blobs/app/data/library android/app/src/main/kotlin/dev/blobs/app/engine \
        android/app/src/main/kotlin/dev/blobs/app/data/work android/app/src/test/kotlin/dev/blobs/app/data/library
git commit -m "fix(library): isolate a failed library instead of failing the turn"
```

---

### Task 6: Let the reader start reading before the answer starts

**Files:**
- Modify: `android/app/src/main/kotlin/dev/blobs/app/feature/chat/TurnCard.kt` (`:90-116`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/feature/chat/ChatScreen.kt` (`:202-218`)
- Modify: `android/app/src/main/res/values/product.xml` (one `pending` label + one sentence for the reading-material framing)
- Test: `android/app/src/test/kotlin/dev/blobs/app/feature/chat/TurnSourceGroupsTest.kt` (extend) and an instrumented parity check

**Interfaces:**
- Consumes: `work.evidence`, `work.turnId`, `work.researchStage`, `groupTurnSources(evidence)`.
- Produces: `TurnSources(turnId, groups, citedSourceIndices, onRead, running: Boolean)` where `running == true` renders the `pending` chip and no grounding claim.

- [ ] **Step 1: Unit-test the rule that must not break**

```kotlin
@Test
fun runningTurnShowsSourcesWithoutClaimingGrounding() {
    val groups = groupTurnSources(listOf(evidence("S1", "Trees"), evidence("S2", "Shade")))
    assertEquals(2, groups.size)
    // While RUNNING, the card may show passages; it may not show a grounding chain.
    assertFalse(TurnCardPlan.showsGrounding(running = true, answer = "Trees cool streets."))
    assertTrue(TurnCardPlan.showsSources(running = true, sourceCount = 2))
    assertTrue(TurnCardPlan.showsGrounding(running = false, answer = "Trees cool streets."))
}
```

Put `TurnCardPlan` in `feature/chat/` as two one-line predicates, so the contract boundary (`docs/bounty-contract.md:115-118`) is a tested object rather than scattered `if (!running)` checks.

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.feature.chat.TurnSourceGroupsTest"`
Expected: FAIL — unresolved `TurnCardPlan`.

- [ ] **Step 2: Implement the predicates and use them in the card**

```kotlin
package dev.blobs.app.feature.chat

/** What a reader may see while a turn is still running, per docs/bounty-contract.md. */
internal object TurnCardPlan {
    fun showsSources(running: Boolean, sourceCount: Int): Boolean = sourceCount > 0
    fun showsGrounding(running: Boolean, answer: String): Boolean = !running && answer.isNotBlank()
}
```

In `TurnCard.kt`, replace `if (!running) { TurnSources(...) }` with `if (TurnCardPlan.showsSources(running, groups.sumOf { it.items.size }))` and pass `running = running` into `TurnSources`; keep `GroundingChain` behind `TurnCardPlan.showsGrounding(...)`. In `TurnSources`, when `running` is true, render the existing `pending` label from `product.xml` next to the group heading so passages read as material being checked, never as the model's prose.

- [ ] **Step 3: Echo the question before the row exists**

In `ChatScreen.kt`'s turns list, when `work.busy && work.turnId` has no matching row in `turns`, render the question bubble from `WorkState` above the running card. Add the tag `const val TAG_ECHO = "chat-question-echo"` next to the existing `TAG_ITEM` so the instrumented check can assert it.

- [ ] **Step 4: Instrumented proof for both behaviours**

Extend the existing compose-driven check set (the same file style as `UiParityTest`) with one test that: starts a turn against a staged library, asserts `TAG_ECHO` appears before the RUNNING row exists, asserts a sources row is visible while `work.busy`, and asserts no grounding badge node exists until the turn finishes.

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest` (one device-heavy run at a time; check `/proc/loadavg` first).
Expected: PASS, and the previously recorded `evidence_ready_state_ms` (44–63 ms) becomes the moment the reader gets something to look at instead of an invisible state change.

- [ ] **Step 5: Copy review, gate, commit**

Confirm the new `product.xml` strings are English and shorter than the existing stage labels, then run `./gradlew verify`.

```bash
git add android/app/src/main/kotlin/dev/blobs/app/feature/chat android/app/src/main/res/values/product.xml \
        android/app/src/test/kotlin/dev/blobs/app/feature/chat android/app/src/androidTest/kotlin/dev/blobs/app
git commit -m "feat(chat): show turn sources as reading material while the answer is written"
```

---

### Task 7: Fan the shelf out, and publish passages as they land

**Files:**
- Create: `android/app/src/main/kotlin/dev/blobs/app/data/library/LibraryFanOut.kt`
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/library/LibraryStore.kt` (the `flatMap` at `:71-92`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/engine/LocalResearchEngine.kt` (`run`, the `retrieve` call at `:97` and `onEvidence` at `:104`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/work/ResearchWork.kt` (the `onEvidence` handler at `:109-132`)
- Test: `android/app/src/test/kotlin/dev/blobs/app/data/library/LibraryFanOutTest.kt`

**Interfaces:**
- Consumes: `LibraryConnectionCache` (Task 4), per-key thread confinement (Task 4 Step 4), `RetrievalQuery` normalization (unchanged, run once before the fan-out).
- Produces: `internal fun <T, R> runInSubmissionOrder(items: List<T>, concurrency: Int, transform: (T) -> R, onFailure: (T, Throwable) -> R): List<R>`; `internal fun documentIdsFor(pack: PackageEntity, scope: Set<String>?): List<String>?` (in `LibraryStore.kt`, top-level); `LibraryStore.retrieveToOutcome(packages, question, scope, maximumResults, concurrency: Int = 1, onPartial: (List<Evidence>) -> Unit = {})`; and an engine callback `onEvidencePartial: (List<Evidence>) -> Unit` alongside the existing final `onEvidence`.

**Why the test does not go through `retrieveFromLibrary`:** `RetrievalCandidate` (`LibraryStore.kt:22-32`) is a private nested class, so no test outside `LibraryStore` can build one, and faking `androidx.sqlite.SQLiteConnection` well enough to produce ranked rows would be a second query engine. Fanning out cannot change ranking — each library's candidate list is still built wholly on one thread, in the same order, by the same unchanged SQL — so the task tests the three properties the fan-out genuinely introduces: group order survives completion order, scope skipping keeps its exact old meaning, and partial callbacks accumulate instead of shrinking.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.blobs.app.data.library

import dev.blobs.app.data.local.PackageEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFanOutTest {
    private fun entity(key: String) = PackageEntity(
        key = key, packageId = key, version = "1", kind = "library", title = key,
        path = "/data/$key", bytes = 1L, sha256 = "sha-$key", license = "CC0",
        origin = "test", installedAt = 0L
    )

    @Test
    fun groupsComeBackInSubmissionOrderEvenWhenTheyFinishBackwards() {
        val secondStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val results = runInSubmissionOrder(
            items = listOf("a", "b", "c"),
            concurrency = 3,
            transform = { item ->
                if (item == "b") {
                    secondStarted.countDown()
                    check(releaseFirst.await(5, TimeUnit.SECONDS))
                }
                item.uppercase()
            },
            onFailure = { item, error -> error = error; "failed:$item" },
        )
        assertEquals(listOf("A", "B", "C"), results)
        assertTrue(secondStarted.count == 0L)
    }

    @Test
    fun aThrowingItemIsRoutedToOnFailureAndNeverSilentlyDropsTheGroup() {
        val seen = mutableListOf<String>()
        val results = runInSubmissionOrder(
            items = listOf("a", "b", "c"),
            concurrency = 2,
            transform = { item -> if (item == "b") throw IllegalStateException("OPEN") else item },
            onFailure = { item, _ -> seen += item; "skip:$item" },
        )
        assertEquals(listOf("a", "b", "c"), results.map { it.toString() }.let { listOf("a", "skip:b", "c" }.zip(it).map { (e, a) -> assertEquals(e, a); a })
        assertEquals(listOf("b"), seen)
    }

    @Test
    fun concurrencyOneRunsOnTheCallerThreadWithoutAPool() {
        val caller = Thread.currentThread().name
        val names = runInSubmissionOrder(
            items = listOf("a", "b"), concurrency = 1,
            transform = { Thread.currentThread().name }, onFailure = { _, _ -> "failed" },
        )
        assertEquals(listOf(caller, caller), names)
    }

    @Test
    fun scopeResolutionMatchesTheInlineRule() {
        val pack = entity("lib1")
        assertNull(documentIdsFor(pack, null))
        assertNull(documentIdsFor(pack, setOf("lib1")))
        assertEquals(listOf("d9"), documentIdsFor(pack, setOf("lib1/d9")))
        assertNull(documentIdsFor(pack, setOf("lib2/d1")))
        assertNull(documentIdsFor(pack, setOf("lib2/d1", "lib3")))
    }
}
```

The second test's assertion line is deliberately ugly-if-broken; replace it with the plain form before committing:

```kotlin
assertEquals(listOf("a", "skip:b", "c"), results)
```

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.data.library.LibraryFanOutTest"`
Expected: FAIL — unresolved references to `runInSubmissionOrder` and `documentIdsFor`.

- [ ] **Step 2: Add the bounded pool helper**

Create `LibraryFanOut.kt`. No new dependency and no suspension restructuring: one `ExecutorService` per call, results collected in submission order, every failure routed through `onFailure` on the caller thread.

```kotlin
package dev.blobs.app.data.library

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Runs [transform] over [items] with at most [concurrency] workers and returns groups in submission order. */
internal fun <T, R> runInSubmissionOrder(
    items: List<T>,
    concurrency: Int,
    transform: (T) -> R,
    onFailure: (T, Throwable) -> R
): List<R> {
    if (items.isEmpty()) return emptyList()
    if (concurrency <= 1) return items.map { item -> runCatching { transform(item) }.getOrElse { onFailure(item, it) } }
    val pool = Executors.newFixedThreadPool(minOf(concurrency, items.size))
    try {
        return items.map { item -> pool.submit(Callable { transform(item) }) }
            .mapIndexed { index, future ->
                runCatching { future.get() }.getOrElse { error ->
                    if (error is java.util.concurrent.ExecutionException) error.cause?.let { onFailure(items[index], it) }
                        ?: onFailure(items[index], error)
                    else onFailure(items[index], error)
                }
            }
    } finally {
        pool.shutdown()
    }
}
```

`ExecutionException` unwrapping is not decoration: `retrieveFromLibrary` throws `IllegalStateException` from the driver, and a test or notice that names the wrapper instead of the cause is a lie about which library broke.

- [ ] **Step 3: Use it in retrieval, keep the ranking pass exactly where it is**

Add the scope helper as a top-level `internal fun` in `LibraryStore.kt`, hoisting the logic currently inlined in the `flatMap` body at `:73-81` — same meaning, one place:

```kotlin
/** Documents this library must search, `null` for "search the whole library", and `null` plus `shouldSkip` for "do not search it at all". */
internal fun documentIdsFor(pack: PackageEntity, scope: Set<String>?): List<String>? {
    if (scope == null) return null
    if (pack.key in scope) return null
    val documentIds = scope.filter { it.startsWith("${pack.key}/") }.map { it.substringAfter('/') }
    return documentIds.ifEmpty { null }
}

private fun shouldSkip(pack: PackageEntity, scope: Set<String>?, documentIds: List<String>?): Boolean =
    scope != null && documentIds == null && pack.key !in scope
```

Then replace the `flatMap` body at `:72-92` with a fan-out that keeps `titleTerms`, `searchQuestion`, `lexicalQuery` and `maximumResults` computed once on the caller thread:

```kotlin
val enabledLibraries = packages.filter { it.kind == "library" && it.enabled }
val failures = mutableListOf<String>()
val candidates = runInSubmissionOrder(
    items = enabledLibraries,
    concurrency = concurrency,
    transform = { pack ->
        val documentIds = documentIdsFor(pack, scope)
        if (shouldSkip(pack, scope, documentIds)) emptyList()
        else retrieveFromLibrary(pack, searchQuestion, lexicalQuery, titleTerms, documentIds, maximumResults)
    },
    onFailure = { pack, _ ->
        connections.evict(pack.key)
        failures += pack.key
        emptyList()
    }
)
```

Two rules make this safe to land: `retrieveFromLibrary` keeps assigning `order = orderIndex++` per library on the thread that built that library's list, so ranking input order is byte-identical to the sequential path; and `concurrency = 1` is passed from every existing call site, so nothing parallel runs until Wave C's plan supplies a number greater than one. The pool is only created when `concurrency > 1`.

- [ ] **Step 3: Run the determinism tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.data.library.*"`
Expected: PASS. If ranking differs, the `order` counter or the submission order is the bug — fix that, never "fix" the test.

- [ ] **Step 4: Publish partial passages as reading material only**

`LocalResearchEngine.run` gains `onEvidencePartial` and forwards each partial batch; `ResearchWork` writes it into `WorkState.evidence` with the same `session.update` it already uses for the final set. The invariant that makes this legal under `docs/bounty-contract.md:115-118`: partials are shown as reading material with the `pending` chip (Task 6), the turn's model prompt still uses the final ranked set from `retrieveToOutcome`, and `onEvidencePartial` may only ever be called with a set that is not smaller than the last one — assert it in the Task 5 test rather than trusting the caller.

- [ ] **Step 5: Measure the cold shelf, then commit**

Run the scaling capture (`MultiLibraryRetrievalScalingTest` with the 9-library case file `corpus/evaluation/cases/multi-library-retrieval-scaling-v6.json`) at `concurrency = 1` and `concurrency = 4`, and record both medians with the Wave A header fields. Expected shape, not a claim: the parallel median drops toward the slowest single library rather than the sum. If it does not, keep `concurrency = 1` as the default and write that result down — this task is insurance for the first turn, and the spec's §7.1 says caching, not parallelism, is the real shelf fix.

```bash
git add android/app/src/main/kotlin/dev/blobs/app/data/library android/app/src/main/kotlin/dev/blobs/app/engine \
        android/app/src/main/kotlin/dev/blobs/app/data/work android/app/src/test/kotlin/dev/blobs/app/data/library
git commit -m "perf(library): fan shelf retrieval out with unchanged ranking and partial display"
```

---

## Wave B exit criteria

- A first turn after app start reports `model_cached: true` and `timings.load_ms == 0`, including a turn taken immediately after installing a model, and after a trim the flag resets so the next idle window warms again.
- The first real turn reports `reused_prefix_tokens > 0`, and the priming completion is provably invisible: no turn row, no `WorkState.text`, no persisted body.
- Retrieval no longer re-opens or re-prepares a library it already has; the journey capture shows turn-1 retrieval under 1 000 ms on the emulator profile.
- A library that cannot be opened contributes no candidates, is named in a notice, and does not fail the turn.
- `concurrency = 1` remains the shipped default until Wave C's plan supplies a number, and the fan-out path is proven to rank identically to the sequential path.
- Sources are visible while a turn is RUNNING, the question is visible before the row exists, and no grounding claim appears before the answer does.
- `bash ./gradlew verify` passes and every new capture carries the Wave A header fields plus `device_acceptance: "unverified"`.

**Deferred on purpose:** thread policy, `allowedCores`, ADPF, `GGML_LLAMAFILE`, the 200-token bound, Settings, GPU variants — Wave C, sequenced after Wave A's attribution and this wave's warm-path numbers exist.
