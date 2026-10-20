package dev.blobs.app

import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.GenerationMetrics
import dev.blobs.app.inference.NativeBridge
import dev.blobs.app.inference.ResearchPrompt
import dev.blobs.app.inference.TokenSink
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@ManualCheck
class ResearchContextTest {
    @Test
    fun nativeTokenizerOverflowKeepsAllSourcesAndSavedCitationIdentity() {
        val args = InstrumentationRegistry.getArguments()
        val path = requireNotNull(args.getString("smokeModel"))
        val file = File(path)
        val digest = file.inputStream().use { input ->
            val hash = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
            hash.digest().joinToString("") { "%02x".format(it) }
        }
        assertEquals("9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031", digest)
        val sources = List(4) { index ->
            Evidence(
                "fixture/$index", "Subject $index", "Test", "local:test/$index", "Test",
                "2026-10-20", "2026-10-21", "Section $index",
                "x y z ".repeat(233), "Synthetic token-budget fixture"
            )
        }
        val question = "Compare the supplied subjects."
        val bridge = NativeBridge()
        val metrics = GenerationMetrics()
        var attempts = 0
        var selected = sources
        try {
            bridge.reset()
            val result = ResearchPrompt.withContextBudget(
                question,
                question,
                listOf("Earlier question" to "Earlier answer"),
                sources
            ) { prompt ->
                attempts++
                selected = prompt.sources
                assertTrue(prompt.text.endsWith("Question: $question\n/no_think"))
                bridge.generate(
                    path.toByteArray(),
                    ResearchPrompt.system.toByteArray(),
                    prompt.text.toByteArray(),
                    0,
                    TokenSink {
                        throw AssertionError("A zero-output check must not stream tokens")
                    },
                    metrics
                )
            }
            assertEquals(2, result)
            assertTrue("The original prompt must exceed the actual tokenizer limit", attempts > 1)
            assertTrue(metrics.promptTokens in 1..3072)
            assertTrue(selected.all { it.text.length < sources.first().text.length })
            val saved = Evidence.decode(Evidence.encode(selected))
            assertEquals(selected, saved)
            val cited = AnswerText.citations("Compare [S1, S2, S3, S4].", saved.size)
                .map { saved[it.sourceIndex].id }
            assertEquals(sources.map { it.id }, cited)
            println(
                "Context recovery: $attempts attempts; ${metrics.promptTokens} tokens; ${saved.size} sources"
            )
        } finally {
            bridge.releaseModel()
        }
    }
}
