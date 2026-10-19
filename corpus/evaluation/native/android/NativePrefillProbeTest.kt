package dev.blobs.app

import android.os.Build
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.GenerationMetrics
import dev.blobs.app.inference.NativeBridge
import dev.blobs.app.inference.TokenSink
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

@ManualCheck
class NativePrefillProbeTest {
    @Test
    fun measuredFrozenPrompt() {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val model = File(requireNotNull(args.getString("modelFile")))
        val systemFile = File(requireNotNull(args.getString("systemFile")))
        val userFile = File(requireNotNull(args.getString("userFile")))
        val expectedModel = requireNotNull(args.getString("modelSha256"))
        require(expectedModel.matches(Regex("[a-f0-9]{64}")))
        val limit = requireNotNull(args.getString("limit")).toInt()
        require(limit in 1..768)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "prefill-$runId.json")
        check(!output.exists())
        val system = systemFile.readBytes()
        val user = userFile.readBytes()
        require(system.size in 1..16_384 && user.size in 1..200_000)
        val report = JSONObject().apply {
            put("format", 1)
            put("run_id", runId)
            put("complete", false)
            put("application_id", context.packageName)
            put("fingerprint", Build.FINGERPRINT)
            put("model_bytes", model.length())
            put("model_sha256", digest(model))
            put("system_bytes", system.size)
            put("system_sha256", digest(system))
            put("user_bytes", user.size)
            put("user_sha256", digest(user))
            put("output_limit", limit)
            put(
                "timing_scope",
                "Native stages in microseconds on this emulator run. File hashing precedes measured generation. This is not a phone prediction."
            )
        }
        fun save() = saveReport(output, report)
        save()
        check(report.getString("model_sha256") == expectedModel)
        val bytes = ByteArrayOutputStream()
        val metrics = GenerationMetrics()
        val bridge = NativeBridge()
        bridge.reset()
        val result = bridge.generate(
            model.path.toByteArray(),
            system,
            user,
            limit,
            TokenSink { bytes.write(it) },
            metrics
        )
        val raw = bytes.toString("UTF-8")
        val answer = AnswerText.visible(raw)
        report.put("result", result)
            .put("raw_answer", raw)
            .put("visible_answer", answer)
            .put("metrics", metrics.json())
            .put("complete", true)
        save()
        assertTrue("Native status $result", result == 0 || result == 2)
        assertTrue(answer.isNotBlank())
        assertTrue(metrics.promptTokens > 0 && metrics.outputTokens > 0)
        assertTrue(metrics.firstTokenCallbackUs >= metrics.prefillUs)
        assertTrue(metrics.totalUs >= metrics.firstTokenCallbackUs)
        assertTrue(metrics.nonThinkingPrefix)
    }

    private fun digest(file: File): String = file.inputStream().use { input ->
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        while (true) {
            val length = input.read(buffer)
            if (length < 0) break
            hash.update(buffer, 0, length)
        }
        hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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
        put("reused_prompt_tokens", reusedPromptTokens)
        put("processed_prompt_tokens", processedPromptTokens)
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
