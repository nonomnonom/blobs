package dev.blobs.app.inference

import dev.blobs.app.data.library.Evidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchPromptTest {
    private val question = "Compare solar panels and wind turbines."
    private val query = "$question Earlier topic: energy storage"
    private val history = listOf("Earlier question" to "Earlier answer")
    private val sources = List(4) { index ->
        Evidence(
            id = "library/document-$index",
            title = "Subject $index",
            publisher = "Publisher",
            url = "https://example.test/$index",
            license = "Test",
            updated = "2026-10-20",
            saved = "2026-10-21",
            section = "Section $index",
            text = "Source $index content. ".repeat(80),
            edition = "Test edition"
        )
    }

    @Test
    fun fittingPromptPreservesOriginalSourceTextHistoryAndQuestion() {
        var calls = 0
        val result = ResearchPrompt.withContextBudget(question, query, history, sources) { prompt ->
            calls++
            assertEquals(sources, prompt.sources)
            assertTrue(prompt.text.contains("Earlier question: Earlier question"))
            assertTrue(prompt.text.endsWith("Question: $question\n/no_think"))
            sources.forEachIndexed { index, source ->
                assertTrue(prompt.text.contains("<source id=\"S${index + 1}\">"))
                assertTrue(prompt.text.contains(source.text))
            }
            0
        }
        assertEquals(0, result)
        assertEquals(1, calls)
    }

    @Test
    fun overflowDropsHistoryThenShortensAllSourcesWithoutChangingTheirIdsOrTheQuestion() {
        val attempts = mutableListOf<ResearchPrompt>()
        val result = ResearchPrompt.withContextBudget(question, query, history, sources) { prompt ->
            attempts.add(prompt)
            if (attempts.size < 4) throw IllegalStateException("CONTEXT_FULL")
            2
        }
        assertEquals(2, result)
        assertEquals(sources, attempts[1].sources)
        assertFalse(attempts[1].text.contains("Earlier answer:"))
        assertEquals(listOf(700, 700, 700, 700), attempts[2].sources.map { it.text.length })
        assertEquals(listOf(350, 350, 350, 350), attempts[3].sources.map { it.text.length })
        attempts.forEach { prompt ->
            assertTrue(prompt.text.contains("Conversation topic: Earlier topic: energy storage"))
            assertTrue(prompt.text.endsWith("Question: $question\n/no_think"))
            assertEquals(1, Regex(Regex.escape(question)).findAll(prompt.text).count())
            prompt.sources.forEachIndexed { index, source ->
                assertEquals(sources[index], source.copy(text = sources[index].text))
                assertTrue(sources[index].text.startsWith(source.text))
                assertTrue(prompt.text.contains(source.text))
                assertTrue(prompt.text.contains("<source id=\"S${index + 1}\">"))
            }
        }
    }

    @Test
    fun oversizedQuestionFailsAfterBoundedAttemptsWithoutTruncatingTheQuestion() {
        val longQuestion = "What does this mean? ".repeat(200)
        var calls = 0
        val error = assertThrows(IllegalStateException::class.java) {
            ResearchPrompt.withContextBudget<Int>(
                longQuestion,
                longQuestion,
                history,
                sources
            ) { prompt ->
                calls++
                assertTrue(prompt.text.contains(longQuestion))
                throw IllegalStateException("CONTEXT_FULL")
            }
        }
        assertEquals("CONTEXT_FULL", error.message)
        assertEquals(4, calls)
    }

    @Test
    fun otherFailuresAndCancellationNeverTriggerRetries() {
        var calls = 0
        val failure = IllegalStateException("MEMORY")
        val caught = assertThrows(IllegalStateException::class.java) {
            ResearchPrompt.withContextBudget<Int>(question, query, history, sources) {
                calls++
                throw failure
            }
        }
        assertTrue(caught === failure)
        assertEquals(1, calls)
        calls = 0
        assertEquals(
            1,
            ResearchPrompt.withContextBudget(question, query, history, sources) {
                calls++
                1
            }
        )
        assertEquals(1, calls)
    }

    @Test
    fun identicalCandidatesAreNotRetriedAndExcerptsPreserveUnicodePairs() {
        val short = listOf(sources.first().copy(text = "Short passage"))
        var calls = 0
        val error = assertThrows(IllegalStateException::class.java) {
            ResearchPrompt.withContextBudget<Int>(question, question, emptyList(), short) {
                calls++
                throw IllegalStateException("CONTEXT_FULL")
            }
        }
        assertEquals("CONTEXT_FULL", error.message)
        assertEquals(1, calls)

        val unicode = listOf(sources.first().copy(text = "x".repeat(349) + "😀" + "y".repeat(400)))
        calls = 0
        ResearchPrompt.withContextBudget(question, question, emptyList(), unicode) { prompt ->
            calls++
            if (calls < 3) throw IllegalStateException("CONTEXT_FULL")
            assertEquals("x".repeat(349), prompt.sources.single().text)
            0
        }
        assertEquals(3, calls)
    }
}
