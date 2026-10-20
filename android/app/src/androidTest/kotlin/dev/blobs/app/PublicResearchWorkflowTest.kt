package dev.blobs.app

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.ProductViewModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

@ManualCheck
class PublicResearchWorkflowTest {
    @get:Rule(order = 0)
    val fontScale = SystemFontScaleRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun questionsThroughChatAndSourceReader() {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val context = compose.activity
        val container = (context.application as BlobsApplication).container
        val root = File(context.filesDir, "evaluation/$runId").canonicalFile
        val casesFile = File(root, "cases.json")
        check(casesFile.isFile && casesFile.length() < 200_000)
        val bytes = casesFile.readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(digest == args.getString("casesSha256"))
        val suite = JSONObject(bytes.toString(Charsets.UTF_8))
        check(suite.getInt("format") == 1)
        val cases = suite.getJSONArray("cases")
        require(cases.length() in 1..20)
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            require(case.getString("id").matches(Regex("[a-zA-Z0-9-]{1,80}")))
            require(case.getString("question").length in 1..2000)
        }
        val output = File(root, "ui-report.json")
        check(!output.exists())
        val model = ViewModelProvider(context)[ProductViewModel::class.java]
        compose.waitUntil(30_000) { model.state.value.loaded && model.work.value.ready }
        val installed = runBlocking { container.database.packages().all() }
        val selected = installed.single {
            it.packageId == args.getString("catalogModelId") && it.kind == "model"
        }
        check(container.catalog.hasStarterLibrary(installed))
        val expectedLibraries = suite.getJSONArray("intended_libraries")
        for (index in 0 until expectedLibraries.length()) {
            check(
                installed.any {
                    it.packageId == expectedLibraries.getString(index) && it.enabled
                }
            )
        }
        fun offline() {
            check(
                Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) ==
                    1
            )
            check(context.getSystemService(ConnectivityManager::class.java).activeNetwork == null)
        }
        offline()
        if (!cases.getJSONObject(0).optBoolean("new_conversation", true)) {
            val initialQuestion = suite.getString("initial_question")
            require(initialQuestion.length in 1..2000)
            compose.waitUntil(15_000) {
                model.turns.value.size == 1 &&
                    model.turns.value.single().question == initialQuestion &&
                    model.turns.value.single().status != "RUNNING"
            }
        }
        val results = JSONArray()
        val report = JSONObject().put("format", 1).put("run_id", runId)
            .put("cases_sha256", digest).put("suite_id", suite.getString("id"))
            .put(
                "scope",
                "Public questions through the actual Compose chat UI; normally installed assets"
            )
            .put("fingerprint", Build.FINGERPRINT).put("api", Build.VERSION.SDK_INT)
            .put("airplane_mode", true).put("active_network", JSONObject.NULL)
            .put("selected_model", selected.key).put("complete", false).put("results", results)
            .put(
                "packages",
                JSONArray().apply {
                    installed.forEach {
                        put(
                            JSONObject().put("key", it.key).put("id", it.packageId)
                                .put(
                                    "bytes",
                                    it.bytes
                                ).put("sha256", it.sha256).put("enabled", it.enabled)
                        )
                    }
                }
            )
        fun save() {
            val atomic = AtomicFile(output)
            val stream = atomic.startWrite()
            try {
                stream.write(report.toString(2).toByteArray())
                atomic.finishWrite(stream)
            } catch (error: Exception) {
                atomic.failWrite(stream)
                throw error
            }
        }
        fun capture(name: String) {
            compose.waitForIdle()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            File(root, "$name.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            image.recycle()
        }
        save()
        try {
            runBlocking { model.selectModel(selected).join() }
            if (!model.state.value.options.welcomed) runBlocking { model.welcome().join() }
            compose.runOnUiThread { model.finishSetup() }
            compose.waitUntil(15_000) { model.state.value.page == Page.CHAT }
            for (index in 0 until cases.length()) {
                offline()
                val case = cases.getJSONObject(index)
                val id = case.getString("id")
                val newConversation = case.optBoolean("new_conversation", true)
                if (newConversation) {
                    runBlocking { model.newConversation().join() }
                    compose.waitUntil(10_000) { model.turns.value.isEmpty() }
                }
                compose.waitUntil(10_000) {
                    model.state.value.page == Page.CHAT &&
                        !model.work.value.busy
                }
                val question = case.getString("question")
                val inputLabel = if (newConversation) {
                    R.string.question_hint
                } else {
                    R.string.follow_up_hint
                }
                compose.onNodeWithContentDescription(context.getString(inputLabel))
                    .performTextInput(question)
                val started = SystemClock.elapsedRealtime()
                compose.onNodeWithContentDescription(
                    context.getString(R.string.send)
                ).performClick()
                var firstVisible: Long? = null
                compose.waitUntil(600_000) {
                    if (model.work.value.startedAt >= started &&
                        model.work.value.text.isNotBlank() && firstVisible == null
                    ) {
                        firstVisible = SystemClock.elapsedRealtime() - started
                    }
                    model.turns.value.lastOrNull()?.let {
                        it.question == question && it.status != "RUNNING" && !model.work.value.busy
                    } == true
                }
                val turn = model.turns.value.last()
                val result = JSONObject().put("id", id).put("question", question)
                    .put("conversation_id", turn.conversationId).put("answer", turn.answer)
                    .put("status", turn.status).put("error", turn.error)
                    .put("evidence", JSONArray(turn.evidence)).put("elapsed_ms", turn.elapsedMs)
                    .put("ui_wall_elapsed_ms", SystemClock.elapsedRealtime() - started)
                    .put("first_observed_work_text_ms", firstVisible ?: JSONObject.NULL)
                container.work.lastPerformanceSnapshot?.let {
                    val generation = it.generation
                    result.put("native_result", it.nativeResult)
                        .put("retrieval_elapsed_ms", it.retrievalElapsedMs)
                        .put("native_total_us", generation.totalUs)
                        .put("prefill_us", generation.prefillUs)
                        .put("model_load_us", generation.modelLoadUs)
                        .put("prompt_tokens", generation.promptTokens)
                        .put("output_tokens", generation.outputTokens)
                        .put("model_cache_hit", generation.modelCacheHit)
                        .put("reused_prompt_tokens", generation.reusedPromptTokens)
                }
                results.put(result)
                save()
                capture("$id-chat")
                val citation = Regex("\\[S([1-4])]").find(turn.answer)
                    ?.groupValues?.get(1)?.toInt()
                val source = citation?.let { Evidence.decode(turn.evidence).getOrNull(it - 1) }
                if (source != null) {
                    val sourceButtons = compose.onAllNodesWithContentDescription(
                        context.getString(R.string.read_source, citation, source.title)
                    )
                    val sourceButton = sourceButtons[sourceButtons.fetchSemanticsNodes().lastIndex]
                    if (!sourceButton.isDisplayed()) sourceButton.performScrollTo()
                    sourceButton.performClick()
                    compose.waitUntil(10_000) { model.state.value.page == Page.READER }
                    capture("$id-source")
                    if (index == 0) {
                        val fullSource = compose.onNodeWithText(
                            context.getString(R.string.read_full_source)
                        )
                        if (!fullSource.isDisplayed()) fullSource.performScrollTo()
                        fullSource.performClick()
                        compose.waitUntil(10_000) { model.state.value.readerIsFull }
                        capture("$id-full-source")
                        result.put("full_source_reader_verified", true)
                    }
                    compose.runOnUiThread { model.back() }
                    compose.waitUntil(10_000) { model.state.value.page == Page.CHAT }
                    result.put("cited_source_reader_verified", true)
                    save()
                }
                println("PUBLIC_UI_CASE:$id:${turn.status}:${turn.elapsedMs}")
            }
            val originalTheme = runBlocking { container.preferences.themeMode.first() }
            try {
                runBlocking { container.preferences.setThemeMode(ThemeMode.LIGHT) }
                runBlocking { container.preferences.themeMode.first { it == ThemeMode.LIGHT } }
                capture("chat-light")
                runBlocking { container.preferences.setThemeMode(ThemeMode.DARK) }
                runBlocking { container.preferences.themeMode.first { it == ThemeMode.DARK } }
                capture("chat-dark")
                fontScale.set(1.5f)
                capture("chat-large-text")
                val followUp = compose.onNodeWithContentDescription(
                    context.getString(R.string.follow_up_hint)
                )
                if (!followUp.isDisplayed()) {
                    compose.onNodeWithText(context.getString(R.string.latest)).performClick()
                }
                compose.waitUntil(10_000) { followUp.isDisplayed() }
                followUp.assertIsDisplayed()
                capture("chat-large-text-composer")
            } finally {
                runBlocking { container.preferences.setThemeMode(originalTheme) }
            }
            offline()
            report.put("complete", true)
        } catch (error: Throwable) {
            report.put("error", error.toString())
            throw error
        } finally {
            save()
        }
        println("Public UI evidence: ${output.absolutePath}")
    }
}
