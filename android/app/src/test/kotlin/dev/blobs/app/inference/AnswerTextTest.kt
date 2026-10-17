package dev.blobs.app.inference

import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.library.RetrievalQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerTextTest {
    @Test
    fun incompleteThinkingNeverLeaksIntoAnswer() {
        assertEquals("", AnswerText.visible("<think>unpublished reasoning"))
        assertEquals("An answer [1]", AnswerText.visible("<think>hidden</think>\nAn answer [1]"))
    }

    @Test
    fun newAnswersKeepOnlyRetrievedNamespacedCitations() {
        assertEquals(
            "Supported [S2]. Article footnote . Unsupported .",
            AnswerText.validSourceCitations(
                "Supported [S2]. Article footnote [2]. Unsupported [S999999].",
                2
            )
        )
    }

    @Test
    fun citationParsingPrefersNamespacedIdsAndKeepsLegacyAnswersReadable() {
        assertEquals(
            listOf(AnswerText.Citation(4..7, 1)),
            AnswerText.citations("New [S2] and article [1]", 2)
        )
        assertEquals(
            listOf(AnswerText.Citation(7..9, 1)),
            AnswerText.citations("Legacy [2] and unavailable [9]", 2)
        )
        assertTrue(AnswerText.citations("Invalid [S9] and numeric [1]", 2).isEmpty())
    }

    @Test
    fun userOperatorsCannotBecomeSearchSyntax() {
        assertEquals("\"trees\" OR \"roofs\"", LibraryStore.searchTerms("trees OR roofs - \" ; *"))
        assertEquals("", LibraryStore.searchTerms("How does this?"))
    }

    @Test
    fun sourceNameQueriesNormalizeFormulaAndBoundPhraseLookup() {
        assertEquals("\"co2\"", RetrievalQuery.match("What is CO₂?"))
        assertEquals(
            "\"ai\" OR title:\"artificial intelligence\"",
            RetrievalQuery.match("What is AI?", listOf("Artificial intelligence"))
        )
        val names = RetrievalQuery.names("Compare heat pumps and resistance heating")
        assertTrue(names.any { it.key == "heat pumps" && it.words == 1..2 })
        assertTrue(names.any { it.key == "resistance heating" && it.words == 4..5 })
        assertTrue(names.none { it.key == "compare" || it.key == "and" })
        assertTrue(RetrievalQuery.names("word ".repeat(10000)).size <= 225)
        assertTrue(RetrievalQuery.names("").isEmpty())
    }
}
