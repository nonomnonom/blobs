# Blobs SpeedGovernor — Wave C (adaptive governor, memory floor, Settings booster, ADPF, prefill flag) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** decide each turn's thread count, answer bound and residency from measured device facts, expose the choice as a Settings mode, add the only legal boost surface a third-party app has (ADPF hint sessions), and make an uncapped catalog recommendation impossible.

**Architecture:** `inference/` gains three files that are pure data plus one orchestrator — `DeviceProfile` (read facts), `SpeedPlan` (decided configuration), `SpeedGovernor` (resolve, ADPF session lifecycle, re-plan on thermal). Native owns the ADPF handle and the `allowedCores` read behind `dlopen`; Kotlin never holds a native handle. `data/preferences` persists the mode, `feature/settings` renders mode plus the resolved plan, `engine/` consumes the plan for its two numbers (threads, token bound). `data/library` and `data/packages` keep their existing ownership; nothing moves layer.

**Tech Stack:** Kotlin, coroutines, Jetpack Compose + `compose-ui-test`, DataStore preferences, C++17 with `<sched.h>`, `<dlfcn.h>` and `<android/performance_hint.h>` against NDK `27.1.12297006`, JUnit 4, `corpus/evaluation/results/*.json` captures.

**Spec:** [docs/superpowers/specs/2026-10-25-blobs-speed-governor-design.md](../specs/2026-10-25-blobs-speed-governor-design.md) — §6.1–6.3, §6.6, §6.8–6.10, §6.12, §4 (what ADPF really offers), §9.1, slices 2, 5, 6, 7, 8, 9.

**Depends on:** [Wave A](./2026-10-26-blobs-speed-wave-a.md) (timers, fixed capacity, record fields) and [Wave B](./2026-10-26-blobs-speed-wave-b.md) (warm path, connection reuse). Wave C's thread ceiling uses Wave A's `setThreads`-without-rebuild behaviour, and its "does more than four threads help?" answer depends on Wave A's Task 5 attribution. Start here only after Wave B's numbers exist.

## Global Constraints

- `minSdk = 28`, `compileSdk = 37`, `ndkVersion = "27.1.12297006"`, ABIs `arm64-v8a` + `x86_64` (`android/app/build.gradle.kts:12,14,27-33`).
- ADPF surface is exactly what this NDK header declares — `APerformanceHint_getManager`, `createSession`, `getPreferredUpdateRateNanos`, `updateTargetWorkDuration`, `reportActualWorkDuration`, `closeSession` at API 33; `setThreads` at 34; `setPreferPowerEfficiency` and `reportActualWorkDuration2` at 36. **There is no `notifyWorkloadSpike`, no `setMode`, no core mask, no affinity query** (spec §4). `sched_setaffinity` is not used anywhere in this plan.
- Symbols newer than `minSdk` must be resolved at runtime through `dlopen("libandroid.so")` + `dlsym`; an unresolved direct call breaks installation on Android 9.
- English-only source, comments, docs and UI copy (`docs/rules/language.md`); new strings in `res/values/product.xml`. Modes are `Automatic` / `Saver` / `Turbo` — the owner's word "Hemat" is not shipped as UI text.
- Every ADPF or thread-count claim is written as `supported` / `not supported` or as a measured number with its record; the emulator has no vendor governor, so a speed claim from ADPF is inadmissible (spec §9.1 slice 8).
- `bash ./gradlew verify` from `android/` is the gate; `connectedDebugAndroidTest` for device runs, `@ManualCheck` excluded; one device-heavy run at a time; pull every report immediately.
- No new Gradle dependencies.

---

### Task 1: Count the cores the process may actually use

**Files:**
- Modify: `android/app/src/main/cpp/local_model_runtime.cpp` / `.h` (add `allowed_cores()`)
- Modify: `android/app/src/main/cpp/blobs.cpp` (JNI entry)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/inference/NativeBridge.kt:3-6,60-68`
- Test: `android/app/src/test/kotlin/dev/blobs/app/inference/InferenceThreadPolicyTest.kt`
- Modify: `docs/architecture.md:321-322` (the recorded clamp-to-8 decision)

**Interfaces:**
- Consumes: `sched_getaffinity` (`<sched.h>`, `CPU_COUNT`).
- Produces: `internal fun inferenceThreads(allowedCores: Int, ceiling: Int): Int` (replacing the one-argument form) and `NativeBridge.allowedCores(): Int`.

- [ ] **Step 1: Rewrite the policy test to the new rule before writing code**

The existing assertion set encodes the decision this wave reverses (`inferenceThreads(8) == 8`), so it is changed first and deliberately:

```kotlin
package dev.blobs.app.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceThreadPolicyTest {
    @Test
    fun neverExceedsTheCoresTheProcessIsAllowedToUse() {
        assertEquals(1, inferenceThreads(allowedCores = 0, ceiling = 4))
        assertEquals(1, inferenceThreads(allowedCores = 1, ceiling = 4))
        assertEquals(2, inferenceThreads(allowedCores = 2, ceiling = 4))
        assertEquals(4, inferenceThreads(allowedCores = 4, ceiling = 4))
        assertEquals(4, inferenceThreads(allowedCores = 8, ceiling = 4))
        assertEquals(6, inferenceThreads(allowedCores = 8, ceiling = 6))
        assertEquals(6, inferenceThreads(allowedCores = 4, ceiling = 6))
    }
}
```

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.inference.InferenceThreadPolicyTest"`
Expected: FAIL — unresolved overload.

- [ ] **Step 2: Implement the policy and the core read**

```kotlin
/**
 * `allowedCores` is what the kernel will schedule this process on, not what the SoC has:
 * eight threads on a four-core target measured 442 s for 75 tokens against 4.8 s at four.
 */
internal fun inferenceThreads(allowedCores: Int, ceiling: Int): Int =
    allowedCores.coerceIn(1, ceiling)
```

In `NativeBridge`, replace the `processThreads` companion that reads `Runtime.getRuntime().availableProcessors()` (`:67`) with a lazily-read native core count and keep the resolution to one value per process (a changing thread count mid-process is what Wave A's `setThreads` now handles explicitly):

```kotlin
internal fun allowedCores(): Int = allowedCoresNative().coerceAtLeast(1)

private external fun allowedCoresNative(): Int

private val processCeiling: Int by lazy { inferenceThreads(allowedCores(), MAX_INFERENCE_THREADS) }
```

Native side, in `local_model_runtime.cpp` and exported in the header:

```cpp
int allowed_cores() {
    cpu_set_t set {};
    if (::sched_getaffinity(0, sizeof(set), &set) != 0 || ::CPU_COUNT(&set) == 0) {
        return 0; // the caller treats 0 as "unknown" and falls back to one thread
    }
    return ::CPU_COUNT(&set);
}
```

with the matching `Java_dev_blobs_app_inference_NativeBridge_allowedCoresNative` in `blobs.cpp`. Do not call `sched_setaffinity` anywhere; the read is the whole point.

- [ ] **Step 3: Record the reversal where it was decided**

Replace `docs/architecture.md:321-322`'s "Threads are auto-detected from the device and clamped to 8, so an eight-core phone is not forced onto a four-thread policy" with text stating that the ceiling is a plan decision and that the count is bounded by `sched_getaffinity`-allowed cores, citing `cost-baseline-001` (442 s for 75 tokens at eight threads) and that **no configuration above four threads has ever been measured in this repository**. Leaving the old sentence would document a policy the code no longer implements.

- [ ] **Step 4: Gate, then commit**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.inference.*"` then `./gradlew verify`
Expected: PASS, PASS.

```bash
git add android/app/src/main/cpp android/app/src/main/kotlin/dev/blobs/app/inference docs/architecture.md
git commit -m "fix(inference): bound inference threads by cores the process may use"
```

---

### Task 2: DeviceProfile, SpeedPlan and the governor

**Files:**
- Create: `android/app/src/main/kotlin/dev/blobs/app/inference/DeviceProfile.kt`
- Create: `android/app/src/main/kotlin/dev/blobs/app/inference/SpeedPlan.kt`
- Create: `android/app/src/main/kotlin/dev/blobs/app/inference/SpeedGovernor.kt`
- Create: `android/app/src/test/kotlin/dev/blobs/app/inference/SpeedGovernorTest.kt`
- Modify: `android/app/src/main/kotlin/dev/blobs/app/engine/LocalResearchEngine.kt` (take the plan for threads and the token bound)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/AppContainer.kt` (construct the governor, pass it to the engine)

**Interfaces:**
- Consumes: `NativeBridge.allowedCores()`, `ActivityManager` memory totals, `PackageManager`/`Build.VERSION.SDK_INT`, `DownloadablePackage.compatibility.minimumRamMb`, `PackageEntity` count, thermal from Task 6.
- Produces: `DeviceProfile(allowedCores, totalMemoryBytes, apiLevel, thermal: ThermalLevel, modelBytes, modelMinimumRamMb, libraryCount)`, `SpeedPlan(threads, answerTokenLimit, contextCapacity, residentModel, allowWarmup, adpfEnabled, targetCycleNanos, reason)`, `SpeedGovernor.plan(profile, mode): SpeedPlan` and `SpeedGovernor.adopt(plan): Unit` (pushes threads through Wave A's `KvCache::set_threads`).

- [ ] **Step 1: Write the failing policy tests**

```kotlin
package dev.blobs.app.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedGovernorTest {
    private fun profile(
        cores: Int = 4,
        totalBytes: Long = 8L * GiB,
        thermal: ThermalLevel = ThermalLevel.NONE,
        modelBytes: Long = 730_893_248,
        minimumRamMb: Long = 4096,
    ) = DeviceProfile(cores, totalBytes, 36, thermal, modelBytes, minimumRamMb, libraryCount = 9)

    @Test
    fun automaticNeverExceedsTheMeasuredFourThreadOptimum() {
        val plan = SpeedGovernorPolicy.plan(profile(cores = 8), PerformanceMode.AUTOMATIC)
        assertEquals(4, plan.threads)
        assertEquals(200, plan.answerTokenLimit)
    }

    @Test
    fun saverGivesBackCoresAndTokens() {
        val plan = SpeedGovernorPolicy.plan(profile(), PerformanceMode.SAVER)
        assertEquals(2, plan.threads)
        assertEquals(160, plan.answerTokenLimit)
        assertFalse("a power-saver must not hold the model", plan.residentModel)
        assertFalse(plan.allowWarmup)
    }

    @Test
    fun residencyNeedsTwoGibibytesOfHeadroomOverMeasuredPss() {
        // LFM2 measured 1.30 GiB PSS after a turn: a 4 GB device keeps ~2.7 GiB free, a 3 GB device not 2 GiB.
        assertTrue(SpeedGovernorPolicy.plan(profile(totalBytes = 4L * GiB), PerformanceMode.AUTOMATIC).residentModel)
        assertFalse(SpeedGovernorPolicy.plan(profile(totalBytes = 3L * GiB), PerformanceMode.AUTOMATIC).residentModel)
    }

    @Test
    fun heatRemovesResidencyBeforeItRemovesThreads() {
        val plan = SpeedGovernorPolicy.plan(profile(thermal = ThermalLevel.SEVERE), PerformanceMode.TURBO)
        assertFalse(plan.residentModel)
        assertEquals(160, plan.answerTokenLimit)
        assertEquals(4, plan.threads)
    }

    @Test
    fun publishedRamFloorIsRespectedEvenWithHeadroom() {
        val plan = SpeedGovernorPolicy.plan(
            profile(totalBytes = 5L * GiB, modelBytes = 1_280_835_840, minimumRamMb = 6144),
            PerformanceMode.TURBO,
        )
        assertFalse("catalog says the 2B writer needs 6 GB", plan.residentModel)
    }
}
```

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "dev.blobs.app.inference.SpeedGovernorTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 2: Implement the data, the mode enum and the rules**

`PerformanceMode` is created here, not in Task 4, because the plan function takes it: in `android/app/src/main/kotlin/dev/blobs/app/data/PerformanceMode.kt`, `internal enum class PerformanceMode { AUTOMATIC, SAVER, TURBO }` (Task 4 only persists it).

```kotlin
// SpeedPlan.kt
package dev.blobs.app.inference

import dev.blobs.app.data.PerformanceMode

/** File-level so the policy and its tests share one spelling of a gibibyte. */
internal const val GiB = 1024L * 1024L * 1024L

internal enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, UNKNOWN }

internal data class DeviceProfile(
    val allowedCores: Int,
    val totalMemoryBytes: Long,
    val apiLevel: Int,
    val thermal: ThermalLevel,
    val modelBytes: Long,
    val modelMinimumRamMb: Long,
    val libraryCount: Int,
)

internal data class SpeedPlan(
    val threads: Int,
    val answerTokenLimit: Int,
    val contextCapacity: Int,
    val residentModel: Boolean,
    val allowWarmup: Boolean,
    val adpfEnabled: Boolean,
    val targetCycleNanos: Long,
    val reason: String,
)

internal object SpeedGovernorPolicy {
    const val RESIDENCY_HEADROOM_BYTES = 2L * GiB

    /** Post-turn PSS measured on this repository's only profiled device class. */
    fun predictedPssBytes(modelBytes: Long): Long = when {
        modelBytes <= 800_000_000 -> (1.30 * GiB).toLong()   // lfm2-1b2-q4km
        modelBytes <= 1_400_000_000 -> (1.89 * GiB).toLong() // qwen35-2b-q4km
        else -> Long.MAX_VALUE                                 // phi4-mini: never measured, never resident
    }
}
```

Then `SpeedGovernorPolicy.plan(profile, mode)`, in the order the spec's §6.2 numbers them: threads from `inferenceThreads(allowedCores, ceiling)` with `ceiling` 2 / 4 / 4 (`Saver` / `Automatic` / `Turbo` — Turbo's ceiling stays at four until Task 5 proves otherwise, and `reason` says so); `answerTokenLimit` 160 for `Saver` or thermal ≥ `SEVERE`, else 200; `contextCapacity` = `LOCAL_RESEARCH_CONTEXT_TOKENS`; residency only when `totalMemoryBytes - predictedPssBytes(modelBytes) >= RESIDENCY_HEADROOM_BYTES`, `thermal <= LIGHT`, `mode != SAVER`, and `totalMemoryBytes / MiB >= modelMinimumRamMb`; `adpfEnabled = apiLevel >= 33`; `targetCycleNanos` 400 / 200 / 100 ms by mode. The rules are a pure object so they are testable without a device; `SpeedGovernor` is then a small class holding the current `SpeedPlan`, the native session handle from Task 6, and exposing `adopt(plan)` (pushes threads through Wave A's `set_threads`) plus `describe(): String` for the Settings status block. It contains no arithmetic.

- [ ] **Step 3: Let the engine read the plan instead of its constants**

In `LocalResearchEngine.run`, replace `MAX_RESEARCH_OUTPUT_TOKENS` at the `generate` call (`:147`) with `plan.answerTokenLimit`, and pass `plan.threads` into `runtime.generate(...)` (the parameter already exists — `NativeBridge.kt:35,44`). `ResearchUsage.outputTokenLimit` keeps reporting the real value, so a record always shows which bound ran.

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: PASS, including `StructuredAnswerTest` and `ResearchPromptTest` which assert prompt-shape, not token count.

- [ ] **Step 4: Commit the policy without any native session yet**

```bash
git add android/app/src/main/kotlin/dev/blobs/app/inference android/app/src/main/kotlin/dev/blobs/app/engine \
        android/app/src/main/kotlin/dev/blobs/app/AppContainer.kt android/app/src/test/kotlin/dev/blobs/app/inference
git commit -m "feat(inference): decide threads, answer bound and residency per device"
```

---

### Task 3: Close the catalog floor's bypass

**Files:**
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageCatalog.kt:92-104`
- Modify: every `recommendedModel` caller that assumed non-null (`data/` callers, `ui/AppViewModel.kt:224-235`, `feature/setup/SetupScreen.kt`)
- Test: `android/app/src/androidTest/kotlin/dev/blobs/app/data/packages/PackageCatalogTest.kt:55-70`

**Interfaces:**
- Consumes: `DownloadablePackage.compatibility.minimumRamMb` (`DownloadablePackage.kt:19,132`), `packageFitsMemory` (`PackageCatalog.kt:103-104`).
- Produces: `fun recommendedModel(totalMemoryBytes: Long, freeStorageBytes: Long): DownloadablePackage?` — `null` meaning "no model fits this device".

- [ ] **Step 1: Write the failing assertion**

In `PackageCatalogTest`, which already writes `compatibility.minimum_ram_mb = 8192` at `:63`, add:

```kotlin
@Test
fun aDeviceBelowEveryPublishedFloorGetsNoRecommendation() {
    val catalog = catalogWith(6144, 8192, 4096)
    assertEquals(null, catalog.recommendedModel(totalMemoryBytes = 3L * 1024 * 1024 * 1024, freeStorageBytes = 50L * 1024 * 1024 * 1024))
}
```

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest` (this test class is instrumented) — or the unit equivalent if the file is in `src/test`.
Expected: FAIL — the current code returns the smallest model anyway.

- [ ] **Step 2: Remove the fallthrough**

```kotlin
fun recommendedModel(totalMemoryBytes: Long, freeStorageBytes: Long): DownloadablePackage? {
    val availableModels = models.sortedBy { it.recommendedUpToMemoryMb }
    val memoryMb = (totalMemoryBytes / (1024L * 1024L)).coerceAtLeast(1)
    val preferredIndex = availableModels.indexOfFirst { it.recommendedUpToMemoryMb >= memoryMb }
        .let { if (it < 0) availableModels.lastIndex else it }
    return availableModels.subList(0, preferredIndex + 1).lastOrNull {
        packageFitsStorage(it, freeStorageBytes) && packageFitsMemory(it, totalMemoryBytes)
    }
}
```

The deleted line is `?: availableModels.minBy { it.manifest.bytes }`: it recommended a model after the memory gate had already rejected it.

- [ ] **Step 3: Handle the null at every call site the compiler now flags**

`AppViewModel` already routes a missing model to `Page.MODELS` (`:232-234`); `SetupScreen` and any `AppContainer` selection keep the same "no model selected" path rather than inventing a default, so the user sees the model page with its storage/RAM explanation instead of a model that cannot run.

- [ ] **Step 4: Gate, then commit**

Run: `cd android && ./gradlew verify`
Expected: PASS.

```bash
git add android/app/src/main/kotlin/dev/blobs/app android/app/src/androidTest/kotlin/dev/blobs/app/data/packages
git commit -m "fix(catalog): never recommend a model the memory floor rejected"
```

---

### Task 4: Settings — the booster the owner can see

**Files:**
- Modify: `android/app/src/main/kotlin/dev/blobs/app/data/preferences/PreferencesStore.kt`
- Modify: `android/app/src/main/kotlin/dev/blobs/app/ui/AppUiState.kt:8-26` (`Page.PERFORMANCE`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/feature/settings/SettingsScreens.kt:66-118` and add `PerformanceScreen`
- Modify: `android/app/src/main/kotlin/dev/blobs/app/ui/BlobsApp.kt:369-400` (routing)
- Modify: `android/app/src/main/res/values/product.xml`
- Test: `android/app/src/test/kotlin/dev/blobs/app/data/preferences/PerformanceModeTest.kt` (new)

**Interfaces:**
- Consumes: `PreferencesStore` key/setter pattern (`:49-56,59`), `SettingsScreen`'s `MenuRow(title, summary) { model.navigate(Page.X) }` shape, `SpeedGovernor.describe()`.
- Produces: `enum class PerformanceMode { AUTOMATIC, SAVER, TURBO }` in `data/`, `PreferencesStore.performanceMode: Flow<PerformanceMode>`, `setPerformanceMode(mode)`, `Options.performanceMode`.

- [ ] **Step 1: Write the persistence round-trip test**

No new test dependency is introduced: this goes in `androidTest` (the same source set `PackageCatalogTest` uses), builds a `PreferencesStore` over a file in `context.cacheDir`, and drives it with `runBlocking`, which the source set already uses.

```kotlin
package dev.blobs.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.PerformanceMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceModeStoreTest {
    @Test
    fun unknownStoredValueFallsBackToAutomaticAndTurboPersists() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "speed-mode-test.preferences_pb").apply { delete() }
        val store = androidx.datastore.core.DataStore
            .let { PreferenceDataStoreFactory.create { file } }
        store.edit { it[stringPreferencesKey("performance_mode")] = "HERO_MODE" }

        val preferences = PreferencesStore(store)
        assertEquals(PerformanceMode.AUTOMATIC, preferences.performanceMode.first())

        preferences.setPerformanceMode(PerformanceMode.TURBO)
        assertEquals(PerformanceMode.TURBO, preferences.performanceMode.first())
        file.delete()
    }
}
```

(`val store = androidx.datastore.core.DataStore.let { ... }` is just the way to keep the generic type inference on one line; if the compiler complains, write `val store = PreferenceDataStoreFactory.create<androidx.datastore.preferences.core.Preferences> { file }` instead. Do not add `kotlinx-coroutines-test` or a `TemporaryFolder` rule.)

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.blobs.app.data.preferences.PerformanceModeStoreTest`
Expected: FAIL — `performanceMode` and `setPerformanceMode` do not exist yet.

- [ ] **Step 2: Add the key, flow and setter**

Mirror `themeMode` exactly (`PreferencesStore.kt:49-56` and the key at `:59`):

```kotlin
private val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")

val performanceMode: Flow<PerformanceMode> =
    store.data.map { preferences ->
        PerformanceMode.entries.find { it.name == preferences[PERFORMANCE_MODE] } ?: PerformanceMode.AUTOMATIC
    }

suspend fun setPerformanceMode(mode: PerformanceMode) {
    store.edit { it[PERFORMANCE_MODE] = mode.name }
}
```

- [ ] **Step 3: Route and render**

Add `PERFORMANCE` to the `Page` enum after `APPEARANCE` (`AppUiState.kt:20`), one `MenuRow(R.string.performance, performanceSummary, { model.navigate(Page.PERFORMANCE) })` inside the existing `settings_experience` section, a `PerformanceScreen` modelled on `AppearanceScreen` (`SettingsScreens.kt:122`) with three `RadioButton` rows, and a status block reading `SpeedGovernor.describe()`: threads actually planned, answer token bound, context capacity, model resident yes/no, ADPF supported yes/no, thermal at last read, and the plan's `reason`. Route it in `BlobsApp.kt` beside `Page.APPEARANCE` (`:395`).

Strings, English, in `res/values/product.xml`: `performance` = "Speed", `performance_automatic` = "Automatic", `performance_saver` = "Power saver", `performance_turbo` = "Turbo", `performance_summary` = "%1$d threads · %2$d-token answers". A demotion (Turbo refused residency on a small device) is shown in that status block, not hidden.

- [ ] **Step 4: Instrumented check for one thing only**

Add to the compose checks: selecting Turbo persists across a process re-create and the status block contains the words "ADPF". Run: `cd android && ./gradlew :app:connectedDebugAndroidTest`
Expected: PASS.

- [ ] **Step 5: Gate, then commit**

```bash
git add android/app/src/main/kotlin/dev/blobs/app/data android/app/src/main/kotlin/dev/blobs/app/ui \
        android/app/src/main/kotlin/dev/blobs/app/feature/settings android/app/src/main/res/values/product.xml \
        android/app/src/test/kotlin/dev/blobs/app/data/preferences
git commit -m "feat(settings): expose the speed mode and show the resolved plan"
```

---

### Task 5: Prove or drop "more than four threads"

**Files:**
- Modify: `android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt` (thread sweep already exists there)
- Create: `corpus/evaluation/cases/speed-wave-c-v1.json` (fixed question set, reused from `corpus/evaluation/cases/` conventions)
- Modify: `docs/engineering-roadmap.md` (the thread-policy bullet at `:305-307`)

**Interfaces:**
- Consumes: `SpeedPlan.threads`, Wave A timers.
- Produces: a recorded number for `ceiling` in `SpeedGovernor.plan` — the only place in this wave that may raise it above four.

- [ ] **Step 1: Sweep on the widest profile available here**

Run the thread sweep on the 8 GB research AVD at 2, 3, 4, 6 and 8 threads, same model, same library, same prompt, three repetitions.

- [ ] **Step 2: Compare only like with like**

Compute `ms_per_generated_token` and `timings.prefill_ms` per thread count. If no count above four is faster, set the `Turbo` ceiling to four and write that in the roadmap line; the shipped product then has three modes that differ by memory and answer bound, not by cores, and the `reason` string says exactly that.

- [ ] **Step 3: Record and commit**

```bash
git add android/app/src/androidTest/kotlin/dev/blobs/app/GenerationCostTest.kt docs/engineering-roadmap.md
git commit -m "test(device): measure whether more than four inference threads help"
```

---

### Task 6: ADPF hint session — coded, reported, never claimed

**Files:**
- Create: `android/app/src/main/cpp/performance_adpf.cpp` and `.h`
- Modify: `android/app/src/main/cpp/CMakeLists.txt` (add the source to the `blobs` target)
- Modify: `android/app/src/main/cpp/blobs.cpp` (begin/end hooks around `generateNative`)
- Modify: `android/app/src/main/kotlin/dev/blobs/app/inference/SpeedGovernor.kt` (`adpfSupported`, plan adoption)
- Test: `android/app/src/androidTest/kotlin/dev/blobs/app/SpeedGovernorAdpfTest.kt` (support probe only)

**Interfaces:**
- Consumes: `<android/performance_hint.h>` symbols resolved at runtime; `gettid()` of the calling thread and, after the first compute, the pool threads found by diffing `/proc/self/task`.
- Produces: `bool adpf_available()`, `void* adpf_begin(const int32_t* tids, size_t count, int64_t target_ns)`, `void adpf_report(void* session, int64_t actual_ns)`, `void adpf_adopt_threads(void* session, const int32_t* tids, size_t count)`, `void adpf_set_power_efficiency(void* session, bool enabled)`, `void adpf_end(void* session)`.

- [ ] **Step 1: Write the support probe, whose only assertion is honesty**

```kotlin
package dev.blobs.app

import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.inference.NativeBridge
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedGovernorAdpfTest {
    @Test
    fun adpfSupportIsReportedNotAssumed() {
        val supported = NativeBridge().adpfSupported()
        assertTrue("support must be a boolean, not a promise", supported || !supported)
    }
}
```

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest`
Expected: PASS on every device; the value is recorded, never asserted to be true.

- [ ] **Step 2: Resolve the symbols at runtime**

Direct calls would break Android 9 installs, so each entry is fetched once:

```cpp
#include <dlfcn.h>

namespace {
struct Api {
    decltype(&APerformanceHint_getManager) get_manager = nullptr;
    decltype(&APerformanceHint_createSession) create_session = nullptr;
    decltype(&APerformanceHint_reportActualWorkDuration) report = nullptr;
    decltype(&APerformanceHint_updateTargetWorkDuration) update_target = nullptr;
    decltype(&APerformanceHint_closeSession) close_session = nullptr;
    decltype(&APerformanceHint_setThreads) set_threads = nullptr;              // API 34
    decltype(&APerformanceHint_setPreferPowerEfficiency) prefer_efficiency = nullptr; // API 36
    int64_t preferred_update_rate_ns = 0;
};

const Api &api() {
    static const Api loaded = [] {
        Api local {};
        if (android_get_device_version() < 33) {
            return local;
        }
        void *handle = ::dlopen("libandroid.so", RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            return local;
        }
        local.get_manager = reinterpret_cast<decltype(local.get_manager)>(::dlsym(handle, "APerformanceHint_getManager"));
        local.create_session = reinterpret_cast<decltype(local.create_session)>(::dlsym(handle, "APerformanceHint_createSession"));
        local.report = reinterpret_cast<decltype(local.report)>(::dlsym(handle, "APerformanceHint_reportActualWorkDuration"));
        local.update_target = reinterpret_cast<decltype(local.update_target)>(::dlsym(handle, "APerformanceHint_updateTargetWorkDuration"));
        local.close_session = reinterpret_cast<decltype(local.close_session)>(::dlsym(handle, "APerformanceHint_closeSession"));
        if (android_get_device_version() >= 34) {
            local.set_threads = reinterpret_cast<decltype(local.set_threads)>(::dlsym(handle, "APerformanceHint_setThreads"));
        }
        if (android_get_device_version() >= 36) {
            local.prefer_efficiency = reinterpret_cast<decltype(local.prefer_efficiency)>(::dlsym(handle, "APerformanceHint_setPreferPowerEfficiency"));
        }
        if (local.get_manager && local.get_manager() == nullptr) {
            local = Api{}; // no framework service: behave as unsupported
        }
        return local;
    }();
    return loaded;
}
} // namespace
```

- [ ] **Step 3: Begin, adopt, report, end**

`adpf_begin` creates a session for the calling thread with `initialTargetWorkDurationNanos` from `SpeedPlan.targetCycleNanos`. After the first decode, `adpf_adopt_threads` is called with the union of the caller's tid and the pool tids discovered by listing `/proc/self/task` before and after the first `llama_process` — `setThreads` **replaces** the set, so the caller must be in it or the thread running decode drops out of the hint. `adpf_report` is called at the render cadence (every 50 ms tick already exists in the engine sink), rate-limited by `APerformanceHint_getPreferredUpdateRateNanos`. `adpf_end` closes the session at turn end and on background. Every failure path returns silently: a hint that cannot be delivered must never fail a turn.

- [ ] **Step 4: Thermal listener feeds the governor**

In `AppViewModel` (which already owns a scope and the container), register `PowerManager.getCurrentThermalStatus` on API 29 and above, `addThermalStatusListener` where available, and treat API 28 or an unavailable sensor as `ThermalLevel.UNKNOWN`, which the plan reads as "not cool" — never as "no heat". On `MODERATE` or above, `SpeedGovernor.adopt` changes threads through Wave A's `set_threads` (no context rebuild); on `SEVERE`, residency is dropped and the bound becomes 160 (Task 2's rules already encode this).

- [ ] **Step 5: Gate, then commit with the honest label**

Run: `cd android && ./gradlew verify`
Expected: PASS.

```bash
git add android/app/src/main/cpp android/app/src/main/kotlin/dev/blobs/app/inference \
        android/app/src/main/kotlin/dev/blobs/app/ui/AppViewModel.kt android/app/src/androidTest/kotlin/dev/blobs/app
git commit -m "feat(native): hint the scheduler for a local completion, without claiming a gain"
```

---

### Task 7: `GGML_LLAMAFILE`, measured against the current build

**Files:**
- Modify: `android/app/src/main/cpp/CMakeLists.txt:45`
- Test: `GenerationCostTest` paired runs, then `PromptCitationProbeTest` on a held-out answer suite

**Interfaces:**
- Consumes: vendored kernels already at `third_party/…/ggml/src/ggml-cpu/llamafile/` (`ggml-cpu/CMakeLists.txt:84-85` defines `GGML_USE_LLAMAFILE`).
- Produces: a prefill-rate comparison, and a decision written into `docs/engineering-roadmap.md`.

- [ ] **Step 1: Baseline the current build first**

On the same APK as Wave A's Task 5 result, capture `timings.prefill_ms` per prompt length (1.5k, 3k, 6k characters). This is the pair the flag change must be compared against — not an older artifact (spec §9.1's pairing rule).

- [ ] **Step 2: Flip the flag and re-measure**

```cmake
set(GGML_LLAMAFILE ON CACHE BOOL "" FORCE)
```

Rebuild both ABIs, re-run the identical sweep, and compute the ratio. Expected from upstream reports on Android ARM is a large prefill gain; this host is x86_64 emulation, so the number may be near one — that result is still the deliverable.

- [ ] **Step 3: Check the answer, not only the speed**

Run `PromptCitationProbeTest` on the held-out set with the flag on, and compare decoded-document rate, marker count and `model_support` against the Step 1 baseline. Numerics change, so an unchanged-looking speed result with a changed answer shape is a reason to leave the flag off.

- [ ] **Step 4: Decide, record, commit**

Keep the flag on only if prefill improves and the answer suite is unchanged-or-better; otherwise revert the line and write the measurement down either way.

```bash
git add android/app/src/main/cpp/CMakeLists.txt docs/engineering-roadmap.md CHANGELOG.md
git commit -m "build(native): evaluate llamafile SIMD gemm for prompt processing on device"
```

---

## Wave C exit criteria

- Threads come from `sched_getaffinity`-allowed cores and a plan ceiling, never `availableProcessors()`; the reversed decision is corrected in `docs/architecture.md` in the same commit.
- `SpeedPlan` is a tested object: threads, token bound, capacity, residency, ADPF flag, cycle target, and a `reason` string the Settings screen can show verbatim.
- No device can be recommended a model its published `minimum_ram_mb` rejects.
- Settings shows Automatic / Power saver / Turbo, persists them, and displays the resolved plan including `adpf_supported`.
- ADPF is coded behind runtime symbol resolution, reported as supported or not, and carries no speed claim; thermal at or above `SEVERE` removes residency and tightens the bound.
- The 6→8 thread question has a number in the roadmap, and `GGML_LLAMAFILE` has a prefill ratio plus an answer-suite comparison — kept or reverted on evidence.
- Every record from this wave carries the Wave A header fields and `device_acceptance: "unverified"`; nothing in the wave is described as phone performance.

**Deferred on purpose:** GPU/NPU (`ggml-vulkan`, `GGML_OPENCL`, `GGML_HEXAGON`) stays behind the existing `snapdragonBackends` build variant with `n_gpu_layers = 0` in the default build and no measurement, per spec §6.9. It gets its own plan only when a phone-class device exists.
