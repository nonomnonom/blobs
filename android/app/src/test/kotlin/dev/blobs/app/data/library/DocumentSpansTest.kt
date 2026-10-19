package dev.blobs.app.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSpansTest {
    @Test
    fun unicodeOffsetsPointToTheExactSavedPassage() {
        val body =
            "\u2003\n" +
                "Trees 🌳 provide shade. Take drinking water 🧭 on the trail.\n\n".repeat(150)
        val spans = documentSpans(body)
        assertTrue(spans.size > 1)
        for (span in spans) {
            assertTrue(span.endCodePoint - span.startCodePoint in 1..1400)
            assertEquals(
                span.text,
                body.substring(
                    body.offsetByCodePoints(0, span.startCodePoint),
                    body.offsetByCodePoints(0, span.endCodePoint)
                )
            )
        }
        assertEquals(
            body.filterNot(Char::isWhitespace),
            spans.joinToString("") { it.text }.filterNot(Char::isWhitespace)
        )
    }

    @Test
    fun aLongUnbrokenWordKeepsSupplementaryCharactersIntact() {
        val body = "🧭".repeat(3201)
        val spans = documentSpans(body)
        assertEquals(listOf(0, 1400, 2800), spans.map { it.startCodePoint })
        assertEquals(listOf(1400, 2800, 3201), spans.map { it.endCodePoint })
        assertEquals(body, spans.joinToString("") { it.text })
    }

    @Test
    fun whitespaceHasNoPassages() {
        assertTrue(documentSpans(" \n\t\u2003").isEmpty())
    }
}
