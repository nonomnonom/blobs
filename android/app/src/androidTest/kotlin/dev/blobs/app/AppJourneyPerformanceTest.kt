package dev.blobs.app

import android.app.ActivityManager
import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.ui.AppViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/** Optional timing and memory capture; generated answer wording is never graded. */
@ManualCheck
class AppJourneyPerformanceTest {
    @Test
    fun recordsSearchResponseStartCancellationAndMemory() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val profile = args.getString("performanceProfile")
        assumeNotNull(profile)
        require(requireNotNull(profile).matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as BlobsApplication).container
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var viewModel: AppViewModel
        scenario.onActivity { viewModel = ViewModelProvider(it)[AppViewModel::class.java] }
        val previous = container.preferences.options.first()
        val report = JSONObject()
        val output = File(context.getExternalFilesDir("performance"), "$profile.json")
        val conversations = mutableListOf<String>()
        fun workDiagnostic(): String = container.work.state.value.let { state ->
            val notice = state.notice?.let(context::getString) ?: "none"
            "stage=${state.researchStage}, busy=${state.busy}, " +
                "evidence=${Evidence.decode(state.evidence).size}, " +
                "textChars=${state.text.length}, notice=$notice"
        }
        try {
            withTimeout(15_000) { container.work.state.first { it.ready && !it.busy } }
            withTimeout(15_000) { viewModel.state.first { it.loaded } }
            val packages = container.database.packages().all()
            val model = packages.single { it.key == args.getString("performanceModel") }
            withTimeout(15_000) {
                viewModel.packages.first { saved -> saved.any { it.key == model.key } }
            }
            withContext(Dispatchers.Main) { viewModel.selectModel(model) }.join()
            withContext(Dispatchers.Main) { viewModel.selectScope(null) }.join()
            withTimeout(15_000) {
                viewModel.state.first {
                    it.options.scope == "*" && viewModel.selectedModel()?.key == model.key
                }
            }
            val memory = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
            report.putReportHeader(context, requireNotNull(profile))
            report.put("profile", profile)
                .put("total_memory_bytes", memory.totalMem)
                .put("available_processors", Runtime.getRuntime().availableProcessors())
                .put("model_sha256", model.sha256)
                .put(
                    "libraries",
                    JSONArray().apply {
                        packages.filter { it.kind == "library" && it.enabled }.forEach {
                            put(JSONObject().put("key", it.key).put("sha256", it.sha256))
                        }
                    }
                )
            fun save() = output.writeText(report.toString(2), Charsets.UTF_8)
            val searches = JSONArray()
            report.put("searches", searches)
            repeat(3) { repetition ->
                for (query in listOf("", "Photosynthesis", "hiking", "Tokyo", "water")) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    val found = container.library.browse(packages, query)
                    searches.put(
                        JSONObject().put("query", query).put("repetition", repetition)
                            .put(
                                "elapsed_ms",
                                (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                            )
                            .put("results", found.size)
                    )
                }
            }
            save()
            container.work.releaseInferenceModel()
            val responses = JSONArray()
            report.put("responses", responses)
            repeat(3) { repetition ->
                val id = "performance-$profile-$repetition"
                conversations.add(id)
                val now = System.currentTimeMillis()
                val conversation = ConversationEntity(id, "", "", now, now)
                container.database.conversations().save(conversation)
                withContext(Dispatchers.Main) { viewModel.openConversation(conversation) }.join()
                val start = SystemClock.elapsedRealtime()
                var peakPssKb = 0
                val sampler = launch {
                    while (true) {
                        val info = Debug.MemoryInfo()
                        Debug.getMemoryInfo(info)
                        peakPssKb = maxOf(peakPssKb, info.totalPss)
                        delay(1000)
                    }
                }
                try {
                    withContext(Dispatchers.Main) {
                        viewModel.send("What do my saved science sources say about photosynthesis?")
                    }.join()
                    assertEquals(id, container.work.state.value.threadId)
                    withContext(Dispatchers.Main) { viewModel.draft("Keep my draft") }
                    val evidenceState = withTimeoutOrNull(180_000) {
                        container.work.state.first {
                            it.threadId == id && (it.evidence != "[]" || !it.busy)
                        }
                    }
                    check(evidenceState != null) {
                        "Timed out waiting for library evidence; ${workDiagnostic()}"
                    }
                    check(evidenceState.evidence != "[]") {
                        "Response ended without library evidence; ${workDiagnostic()}"
                    }
                    val evidenceMs = SystemClock.elapsedRealtime() - start
                    val responseState = withTimeoutOrNull(180_000) {
                        container.work.state.first {
                            it.threadId == id && (it.text.isNotBlank() || !it.busy)
                        }
                    }
                    check(responseState != null) {
                        "Timed out waiting for answer text; ${workDiagnostic()}"
                    }
                    check(responseState.text.isNotBlank()) {
                        "Response ended without visible text; ${workDiagnostic()}"
                    }
                    val visibleMs = SystemClock.elapsedRealtime() - start
                    val responseMemory = Debug.MemoryInfo()
                    Debug.getMemoryInfo(responseMemory)
                    peakPssKb = maxOf(peakPssKb, responseMemory.totalPss)
                    val stop = SystemClock.elapsedRealtime()
                    scenario.onActivity { viewModel.stop() }
                    withTimeout(15_000) { container.work.state.first { !it.busy } }
                    val cancellationMs = SystemClock.elapsedRealtime() - stop
                    val turn = container.database.conversations().turns(id).single()
                    val performance = requireNotNull(container.work.lastPerformanceSnapshot)
                    responses.put(
                        JSONObject().put("repetition", repetition)
                            .put(
                                "evidence_ready_ms",
                                evidenceMs
                            ).put("first_text_state_ms", visibleMs)
                            .put(
                                "cancellation_ms",
                                cancellationMs
                            ).put("sampled_peak_pss_kb", peakPssKb)
                            .put("retrieval_ms", performance.retrievalElapsedMs)
                            .put(
                                "first_visible_text_elapsed_ms",
                                performance.firstVisibleTextElapsedMs ?: JSONObject.NULL
                            )
                            .put("end_to_end_elapsed_ms", performance.endToEndElapsedMs)
                            .put("prompt_characters", performance.promptCharacters)
                            .put("source_count", performance.sourceCount)
                            .put("history_pair_count", performance.historyPairCount)
                            .put("generation_attempts", performance.generationAttempts)
                            .put("output_token_limit", performance.outputTokenLimit)
                            .put(
                                "native_stages",
                                performance.nativeStageTimings?.json() ?: JSONObject.NULL
                            )
                            .put("pss_at_first_text_kb", responseMemory.totalPss)
                            .put("status", turn.status)
                    )
                    assertEquals(
                        "Keep my draft",
                        container.database.conversations().find(id)?.draft
                    )
                    assertEquals("Keep my draft", viewModel.state.value.draft)
                    assertTrue(Evidence.decode(turn.evidence).isNotEmpty())
                    assertTrue(turn.status in setOf("STOPPED", "COMPLETE"))
                    save()
                } finally {
                    sampler.cancel()
                    container.work.stop()
                    withTimeout(15_000) { container.work.state.first { !it.busy } }
                }
            }
            report.put("complete", true)
            save()
            println("Application performance: ${output.absolutePath}")
        } finally {
            container.preferences.selectModel(previous.model)
            container.preferences.selectScope(previous.scope)
            val previousConversation = container.database.conversations().find(
                previous.conversation
            )
            if (previousConversation != null) {
                withContext(Dispatchers.Main) {
                    viewModel.openConversation(previousConversation)
                }.join()
            } else {
                container.preferences.selectConversation(previous.conversation)
            }
            conversations.forEach { container.database.conversations().delete(it) }
            scenario.close()
        }
    }
}
