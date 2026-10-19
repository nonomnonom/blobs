package dev.blobs.app

import android.os.Build
import android.os.Debug
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.inference.GenerationMetrics
import dev.blobs.app.inference.NativeBridge
import dev.blobs.app.inference.TokenSink
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

@ManualCheck
class NativeContextCacheTest {
    @Test
    fun measuresPromptReuseAndInvalidation() {
        val args = InstrumentationRegistry.getArguments()
        val model = args.getString("modelFile")
        assumeTrue("Supply a verified installed model with -e modelFile", model != null)
        val expectedHash = requireNotNull(args.getString("modelSha256"))
        require(expectedHash.matches(Regex("[a-f0-9]{64}")))
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val file = File(requireNotNull(model))
        assertEquals(expectedHash, file.sha256())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "context-cache-$runId.json")
        check(!output.exists())
        val samples = JSONArray()
        val report = JSONObject().apply {
            put("format", 1)
            put("complete", false)
            put("run_id", runId)
            put("model_sha256", expectedHash)
            put("fingerprint", Build.FINGERPRINT)
            put("abi", Build.SUPPORTED_ABIS.first())
            put("available_processors", Runtime.getRuntime().availableProcessors())
            put("max_output_tokens", 1)
            put("app_apk_sha256", File(context.applicationInfo.sourceDir).sha256())
            put("samples", samples)
        }
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
        val bridge = NativeBridge()
        val system = "Answer in English from the supplied offline references. /no_think\n" +
            (1..6).joinToString("\n") {
                "Reference $it: Carry water, check the weather, share your plans, and keep a map."
            }
        fun sample(name: String, instructions: String, question: String): GenerationMetrics {
            bridge.reset()
            val metrics = GenerationMetrics()
            val status = bridge.generate(
                file.path.toByteArray(),
                instructions.toByteArray(),
                question.toByteArray(),
                1,
                TokenSink {},
                metrics
            )
            samples.put(
                JSONObject().apply {
                    put("name", name)
                    put("status", status)
                    put("total_us", metrics.totalUs)
                    put("model_load_us", metrics.modelLoadUs)
                    put("context_setup_us", metrics.contextSetupUs)
                    put("prefill_us", metrics.prefillUs)
                    put("first_token_callback_us", metrics.firstTokenCallbackUs)
                    put("prompt_tokens", metrics.promptTokens)
                    put("output_tokens", metrics.outputTokens)
                    put("model_cache_hit", metrics.modelCacheHit)
                    put("reused_prompt_tokens", metrics.reusedPromptTokens)
                    put("processed_prompt_tokens", metrics.processedPromptTokens)
                    put("context_tokens", metrics.contextTokens)
                    put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize())
                }
            )
            save()
            assertTrue("$name: native status $status", status == 0 || status == 2)
            assertTrue(metrics.promptTokens > 0)
            assertTrue(metrics.outputTokens in 0..1)
            assertEquals(
                metrics.promptTokens,
                metrics.reusedPromptTokens + metrics.processedPromptTokens
            )
            return metrics
        }
        bridge.releaseModel()
        try {
            val cold = sample("cold", system, "What should I pack for a day hike?")
            assertEquals(0L, cold.reusedPromptTokens)
            val identical = sample("identical", system, "What should I pack for a day hike?")
            if (args.getString("expectPrefixReuse") == "true") {
                assertEquals(identical.promptTokens - 1, identical.reusedPromptTokens)
                assertEquals(1L, identical.processedPromptTokens)
            }
            val changed = sample("changed-user", system, "What should I take for a rainy walk?")
            assertTrue(changed.processedPromptTokens > 1)
            if (args.getString("expectPrefixReuse") == "true") {
                assertTrue(changed.reusedPromptTokens > cold.promptTokens / 2)
            }
            val replaced =
                sample("changed-system", "Use the new sources only. /no_think", "Where can I walk?")
            assertTrue(replaced.processedPromptTokens > 1)
            assertTrue(
                replaced.reusedPromptTokens < changed.reusedPromptTokens ||
                    changed.reusedPromptTokens == 0L
            )
            bridge.releaseModel()
            val released = sample("released", system, "What should I pack for a day hike?")
            assertEquals(0L, released.reusedPromptTokens)
            assertTrue(!released.modelCacheHit)

            bridge.reset()
            val cancelled = GenerationMetrics()
            val stopped = bridge.generate(
                file.path.toByteArray(),
                system.toByteArray(),
                "What should I pack for a day hike?".toByteArray(),
                16,
                TokenSink { bridge.stop() },
                cancelled
            )
            assertEquals(1, stopped)
            assertEquals(1L, cancelled.outputTokens)
            val resumed = sample("after-stop", system, "What should I pack for a day hike?")
            if (args.getString("expectPrefixReuse") == "true") {
                assertEquals(resumed.promptTokens - 1, resumed.reusedPromptTokens)
            }

            bridge.reset()
            try {
                bridge.generate(
                    "$model.missing".toByteArray(),
                    system.toByteArray(),
                    "No file".toByteArray(),
                    1,
                    TokenSink {}
                )
                throw AssertionError("A missing model must fail")
            } catch (error: IllegalStateException) {
                assertEquals("MODEL_LOAD", error.message)
            }
            val recovered =
                sample("after-model-failure", system, "What should I pack for a day hike?")
            assertEquals(0L, recovered.reusedPromptTokens)
            assertTrue(!recovered.modelCacheHit)

            val grown = sample("grown-context", system.repeat(4), "What should I pack?")
            assertTrue(grown.contextTokens > recovered.contextTokens)
            assertEquals(0L, grown.reusedPromptTokens)
            sample("shortened-prompt", system, "What should I pack for a day hike?")

            val executor = Executors.newSingleThreadExecutor()
            try {
                bridge.reset()
                val started = CountDownLatch(1)
                val interrupted = GenerationMetrics()
                val future = executor.submit<Int> {
                    started.countDown()
                    bridge.generate(
                        file.path.toByteArray(),
                        system.repeat(12).toByteArray(),
                        "What should I pack?".toByteArray(),
                        1,
                        TokenSink {},
                        interrupted
                    )
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                Thread.sleep(250)
                bridge.stop()
                assertEquals(1, future.get(30, TimeUnit.SECONDS))
                report.put(
                    "interrupted_prefill",
                    JSONObject().put("prefill_us", interrupted.prefillUs)
                        .put("processed_prompt_tokens", interrupted.processedPromptTokens)
                        .put("prompt_tokens", interrupted.promptTokens)
                )
                val afterAbort = sample("after-prefill-abort", system, "What should I pack?")
                if (interrupted.prefillUs >= 0) assertEquals(0L, afterAbort.reusedPromptTokens)
            } finally {
                bridge.stop()
                executor.shutdownNow()
                check(executor.awaitTermination(30, TimeUnit.SECONDS))
            }
            bridge.releaseModel()
            report.put("native_heap_after_release_bytes", Debug.getNativeHeapAllocatedSize())
            report.put("complete", true)
            save()
        } finally {
            bridge.releaseModel()
            bridge.reset()
        }
    }

    private fun File.sha256(): String = inputStream().use {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(256 * 1024)
        while (true) {
            val count = it.read(bytes)
            if (count < 0) break
            digest.update(bytes, 0, count)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
