package dev.blobs.app

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.GgufValidator
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.GenerationMetrics
import dev.blobs.app.inference.NativeBridge
import dev.blobs.app.inference.TokenSink
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

@ManualCheck
class NativeInferenceTest {
    @Test
    fun prepareLibraryForAnswerEvaluation() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "prepared-library-$runId.json")
        check(!output.exists())
        val container = (context.applicationContext as BlobsApplication).container
        withTimeout(15_000) { container.work.state.first { it.ready } }
        val report = JSONObject().put("format", 1).put("complete", false)
            .put("scope", "Normal library import before staging a separate model test fixture")
        saveReport(output, report)
        try {
            val uri = Uri.fromFile(File(requireNotNull(args.getString("libraryPackage"))))
            val manifest = container.installer.inspect(uri)
            require(manifest.kind == "library")
            report.put("manifest", JSONObject(manifest.json()))
            saveReport(output, report)
            val installed = container.installer.install(uri, manifest) {}
            report.put("installed_key", installed.key).put("complete", true)
        } catch (error: Exception) {
            report.put("error", error.toString())
            throw error
        } finally {
            saveReport(output, report)
        }
    }

    @Test
    fun installedLibraryAndModelProduceSavedCitedAnswer() = recordAnswer(false)

    @Test
    fun verifiedModelFileProducesSavedCitedAnswer() = recordAnswer(true)

    private fun recordAnswer(verifiedFile: Boolean) = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val modelPath = requireNotNull(
            args.getString(if (verifiedFile) "modelFile" else "modelPackage")
        )
        val libraryPath = requireNotNull(args.getString("libraryPackage"))
        val runId = args.getString("runId") ?: UUID.randomUUID().toString()
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val question = args.getString("question")
            ?: "How do trees reduce urban heat? Explain with sources."
        require(question.length in 1..2000)
        val timeoutMs = (args.getString("timeoutSeconds") ?: "240").toLong() * 1000
        require(timeoutMs in 60_000..600_000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as BlobsApplication).container
        withTimeout(15_000) { container.work.state.first { it.ready } }
        val output = File(context.filesDir, "answer-$runId.json")
        check(!output.exists())
        val id = "answer-$runId"
        val report = JSONObject().apply {
            put("format", 1)
            put("run_id", runId)
            put("complete", false)
            put("question", question)
            put("application_id", context.packageName)
            put("fingerprint", Build.FINGERPRINT)
            put("api", Build.VERSION.SDK_INT)
            put("generation_started", false)
            put(
                "model_preparation",
                if (verifiedFile) {
                    "Verified GGUF fixture: checksum and native importer validator, without model installation. Library import and WorkController persistence/generation are exercised. This is not model-package import proof."
                } else {
                    "Normal model-package import"
                }
            )
            put("timeout_ms", timeoutMs)
            put(
                "timing_scope",
                "End-to-end app observation on this device. First nonblank WorkState text is not native token latency. No phone-performance extrapolation."
            )
        }
        fun save() = saveReport(output, report)
        save()
        val installed = try {
            listOf(modelPath, libraryPath).mapIndexed { index, path ->
                val kind = if (index == 0) "model" else "library"
                if (index == 0 && verifiedFile) {
                    val expected = requireNotNull(args.getString("modelSha256"))
                    require(expected.matches(Regex("[a-f0-9]{64}")))
                    val file = File(path)
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val length = input.read(buffer)
                            if (length < 0) break
                            digest.update(buffer, 0, length)
                        }
                    }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == expected)
                    GgufValidator.validate(file)
                    report.put("model_file_bytes", file.length())
                    save()
                    return@mapIndexed PackageEntity(
                        "fixture-${expected.take(16)}", "fixture-model", expected.take(8),
                        "model", file.name, file.path, file.length(), expected,
                        "Evaluation fixture; see its pinned model license",
                        "local:verified-fixture", 0
                    )
                }
                val uri = Uri.fromFile(File(path))
                val manifest = container.installer.inspect(uri)
                require(manifest.kind == kind)
                report.put("${kind}_manifest", JSONObject(manifest.json()))
                save()
                container.installer.install(uri, manifest) {}
            }
        } catch (error: Exception) {
            report.put("status", if (verifiedFile) "PREPARATION_FAILED" else "IMPORT_FAILED")
            report.put("error", error.toString())
            save()
            throw error
        }
        val model = installed[0]
        val library = installed[1]
        report.put("model_sha256", model.sha256).put("model_key", model.key)
        report.put("library_sha256", library.sha256).put("library_key", library.key)
        container.database.conversations().save(
            ConversationEntity(
                id,
                "",
                question,
                System.currentTimeMillis(),
                System.currentTimeMillis()
            )
        )
        val start = SystemClock.elapsedRealtime()
        var firstVisibleMs: Long? = null
        val observer = launch {
            container.work.state.first { it.threadId == id && it.text.isNotBlank() }
            firstVisibleMs = SystemClock.elapsedRealtime() - start
        }
        var finished = false
        try {
            assertTrue(container.work.answer(id, question, model, installed, null))
            report.put("generation_started", true)
            save()
            container.database.conversations().saveDraft(id, "My next question stays here")
            finished = withTimeoutOrNull(timeoutMs) {
                container.work.state.first { !it.busy }
            } != null
        } catch (error: Exception) {
            report.put("test_error", error.toString())
            throw error
        } finally {
            if (container.work.state.value.busy) {
                container.work.stop()
                withTimeoutOrNull(60_000) { container.work.state.first { !it.busy } }
            }
            observer.cancel()
            val turn = container.database.conversations().turns(id).lastOrNull()
            report.put("complete", !container.work.state.value.busy && turn != null)
            report.put("finished_within_timeout", finished)
            report.put("wall_elapsed_ms", SystemClock.elapsedRealtime() - start)
            report.put("first_visible_state_ms", firstVisibleMs ?: JSONObject.NULL)
            report.put("status", turn?.status ?: JSONObject.NULL)
            report.put("answer", turn?.answer ?: JSONObject.NULL)
            report.put("evidence", turn?.evidence ?: JSONObject.NULL)
            report.put("saved_elapsed_ms", turn?.elapsedMs ?: JSONObject.NULL)
            report.put("error", turn?.error ?: JSONObject.NULL)
            save()
            println("Answer evidence: ${output.absolutePath}")
        }
        assertTrue(
            "Generation did not finish within $timeoutMs ms; partial evidence saved",
            finished
        )
        val turn = container.database.conversations().turns(id).last()
        assertTrue("${turn.status}: ${turn.error}", turn.status in setOf("COMPLETE", "LIMITED"))
        assertTrue(turn.answer.contains(Regex("\\[S\\d+]")))
        assertTrue(turn.evidence.contains("Wikipedia contributors"))
        assertEquals(
            "My next question stays here",
            container.database.conversations().find(id)?.draft
        )
    }

    @Test
    fun realModelStreamsAndCancellationStopsNativeLoop() {
        val path = InstrumentationRegistry.getArguments().getString("smokeModel")
        assumeTrue("Provide a verified GGUF with -e smokeModel <app-private path>", path != null)
        val model = requireNotNull(path)
        assertTrue(File(model).isFile)
        val args = InstrumentationRegistry.getArguments()
        val runId = args.getString("runId") ?: UUID.randomUUID().toString()
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reportFile = File(context.filesDir, "native-$runId.json")
        check(!reportFile.exists())
        val hash = MessageDigest.getInstance("SHA-256")
        File(model).inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                hash.update(buffer, 0, length)
            }
        }
        val report = JSONObject().apply {
            put("format", 1)
            put("run_id", runId)
            put("complete", false)
            put("fingerprint", Build.FINGERPRINT)
            put("model_bytes", File(model).length())
            put("model_sha256", hash.digest().joinToString("") { "%02x".format(it) })
            put(
                "timing_scope",
                "Native stages in microseconds; -1 means not reached. First callback includes queue, loading and prefill; callback bytes may not yet form visible UTF-8 text. Generation loop includes token callbacks. File hash reads precede loading; this is not a filesystem-cold or phone-performance benchmark."
            )
        }
        fun checkpoint() = saveReport(reportFile, report)
        checkpoint()
        val bridge = NativeBridge()
        bridge.reset()
        val output = ByteArrayOutputStream()
        val metrics = GenerationMetrics()
        val result = bridge.generate(
            model.toByteArray(),
            "Answer briefly in English. /no_think".toByteArray(),
            (
                "Source [1]: Photosynthesis converts light energy into chemical energy. " +
                    "What does photosynthesis do? Cite [1]. /no_think"
                ).toByteArray(),
            96,
            TokenSink { output.write(it) },
            metrics
        )
        report.put("answer_metrics", metrics.json()).put("answer_result", result)
        report.put("raw_answer", output.toString("UTF-8"))
        checkpoint()
        assertTrue("Native status $result", result == 0 || result == 2)
        assertTrue(metrics.promptTokens > 0 && metrics.outputTokens > 0)
        assertTrue(metrics.firstTokenCallbackUs >= metrics.prefillUs)
        assertTrue(metrics.totalUs >= metrics.firstTokenCallbackUs)
        assertTrue(metrics.modelLoadUs >= 0 && metrics.contextSetupUs >= 0)
        val answer = AnswerText.visible(output.toString("UTF-8"))
        assertTrue("Output: $answer", answer.isNotBlank())
        bridge.reset()
        var tokens = 0
        val cancellationMetrics = GenerationMetrics()
        val stopped = bridge.generate(
            model.toByteArray(),
            "Answer in English. /no_think".toByteArray(),
            "Describe the solar system in detail. /no_think".toByteArray(),
            96,
            TokenSink { if (++tokens == 3) bridge.stop() },
            cancellationMetrics
        )
        report.put(
            "cancellation_metrics",
            cancellationMetrics.json()
        ).put("cancellation_result", stopped)
        checkpoint()
        assertEquals(1, stopped)
        assertEquals(3, tokens)
        assertEquals(3L, cancellationMetrics.outputTokens)
        val failureMetrics = GenerationMetrics()
        bridge.reset()
        try {
            bridge.generate(
                "$model.missing".toByteArray(),
                "".toByteArray(),
                "".toByteArray(),
                1,
                TokenSink {},
                failureMetrics
            )
            throw AssertionError("A missing model must fail")
        } catch (error: IllegalStateException) {
            assertEquals("MODEL_LOAD", error.message)
        }
        assertTrue(failureMetrics.totalUs >= 0)
        assertEquals(-1L, failureMetrics.firstTokenCallbackUs)
        assertEquals(0L, failureMetrics.outputTokens)
        report.put("failure_metrics", failureMetrics.json()).put("complete", true)
        checkpoint()
    }

    private fun GenerationMetrics.json(): JSONObject = JSONObject().apply {
        put("total_us", totalUs)
        put("queue_us", queueUs)
        put("model_load_us", modelLoadUs)
        put("template_and_tokenize_us", templateAndTokenizeUs)
        put("context_setup_us", contextSetupUs)
        put("prefill_us", prefillUs)
        put("generation_loop_us", generationLoopUs)
        put("token_callback_us", tokenCallbackUs)
        put("first_token_callback_us", firstTokenCallbackUs)
        put("prompt_tokens", promptTokens)
        put("output_tokens", outputTokens)
        put("non_thinking_prefix", nonThinkingPrefix)
    }

    private fun saveReport(output: File, report: JSONObject) {
        val file = AtomicFile(output)
        val stream = file.startWrite()
        try {
            stream.write(report.toString(2).toByteArray())
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
