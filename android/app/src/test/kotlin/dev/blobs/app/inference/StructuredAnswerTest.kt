package dev.blobs.app.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredAnswerTest {
    private val slash = "\\"

    private fun jsonUnicode(code: Int): String = slash + "u" + code.toString(16).padStart(4, '0')

    @Test
    fun fullDocumentDecodesToProseAndClaim() {
        val raw = "{\"answer\":\"Leaves transpire [S1] and shade the street. [S2]\"," +
            "\"support\":\"sourced\"}"

        val output = StructuredAnswer.decode(raw)

        assertEquals(
            ModelOutput("Leaves transpire [S1] and shade the street. [S2]", ModelSupport.SOURCED),
            output
        )
    }

    @Test
    fun escapesDecodeLikeJson() {
        val raw = "{\"answer\":\"first line" + jsonUnicode(0x0A) + "second line " +
            slash + "\"quoted" + slash + "\" and " + jsonUnicode(0xE9) + slash + slash +
            "\",\"support\":\"partial\"}"

        val output = StructuredAnswer.decode(raw)

        assertEquals(
            "first line" + Char(0x0A) + "second line \"quoted\" and " + Char(0xE9) + slash,
            output?.answer
        )
        assertEquals(ModelSupport.PARTIAL, output?.support)
    }

    @Test
    fun anythingOutsideTheContractIsRejected() {
        listOf(
            "",
            "   ",
            "Trees cool streets.",
            "{",
            "{\"answer\":\"Sourced.\",\"support\":\"maybe\"}",
            "{\"answer\":\"Sourced.\",\"support\":null}",
            "{\"answer\":\"\",\"support\":\"sourced\"}",
            "{\"answer\":\"   \",\"support\":\"sourced\"}",
            "{\"answer\":true,\"support\":\"sourced\"}",
            "{\"answer\":123,\"support\":\"sourced\"}",
            "{\"support\":\"sourced\"}",
            "{\"answer\":\"Sourced.\"}",
            "{\"answer\":\"Sourced.\",\"support\":\"sourced\",\"extra\":1}",
            "{\"answer\":\"Sourced.\",\"support\":\"sourced\"} trailing prose",
            "[{\"answer\":\"Sourced.\",\"support\":\"sourced\"}]",
            "{\"answer\":\"Sourced.\",\"support\":\"sourced\"}}",
            "{\"answer\":\"Sourced.\",\"support\":\"sourced\",}"
        ).forEach { raw ->
            assertNull(raw, StructuredAnswer.decode(raw))
        }
    }

    @Test
    fun everyStreamingPrefixRendersProseOnly() {
        val raw = "{\"answer\":\"A park cools a street [S1] and a plaza does more. [S2]\"," +
            "\"support\":\"unsourced\"}"
        val expected = StructuredAnswer.decode(raw)?.answer
        assertTrue(expected != null)

        var last = ""
        for (end in 1..raw.length) {
            val visible = StructuredAnswer.partialText(raw.substring(0, end)) ?: continue
            assertTrue(visible, visible.none { it == '{' || it == '}' || it == '"' || it == '\\' })
            assertTrue(visible, visible.length >= last.length)
            assertTrue(visible, expected!!.startsWith(visible))
            last = visible
        }
        assertEquals(expected, last)
    }

    @Test
    fun anIncompleteEscapeIsWithheldUntilItIsWhole() {
        val unicode = jsonUnicode(0xE9)
        val raw = "{\"answer\":\"cool " + unicode + "sthetic\",\"support\":\"sourced\"}"
        val start = raw.indexOf(slash)

        (1 until unicode.length).forEach { cut ->
            assertEquals("cool ", StructuredAnswer.partialText(raw.substring(0, start + cut)))
        }
        assertEquals(
            "cool " + Char(0xE9),
            StructuredAnswer.partialText(raw.substring(0, start + unicode.length))
        )
        assertEquals(
            "cool " + Char(0xE9) + "sthetic",
            StructuredAnswer.partialText(raw)
        )
    }

    @Test
    fun aSplitSurrogatePairIsWithheldUntilBothHalvesArrive() {
        val smile = String(Character.toChars(0x1F600))
        val raw = "{\"answer\":\"smile " + jsonUnicode(0xD83D) + jsonUnicode(0xDE00) +
            " done\",\"support\":\"sourced\"}"
        val start = raw.indexOf(slash)

        assertEquals("smile ", StructuredAnswer.partialText(raw.substring(0, start + 6)))
        assertEquals("smile ", StructuredAnswer.partialText(raw.substring(0, start + 8)))
        assertEquals("smile $smile done", StructuredAnswer.decode(raw)?.answer)
    }

    @Test
    fun plainTextIsNotTreatedAsAStructuredPrefix() {
        assertNull(StructuredAnswer.partialText("Trees cool streets."))
        assertNull(StructuredAnswer.partialText(""))
        assertNull(StructuredAnswer.partialText("{"))
        assertNull(StructuredAnswer.partialText("{\"answer\""))
        assertNull(StructuredAnswer.partialText("{\"support\":\"sourced\"}"))
    }

    @Test
    fun grammarOwnsTheShapeTheDecoderExpects() {
        assertEquals("root", StructuredAnswer.GRAMMAR_ROOT)
        assertTrue(StructuredAnswer.grammar.contains("root          ::= answer-object"))
        assertTrue(
            StructuredAnswer.grammar.indexOf("answer-pair") <
                StructuredAnswer.grammar.indexOf("support-pair")
        )
        // The native parser ends a rule at a newline, so a production must never wrap.
        StructuredAnswer.grammar.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line -> assertTrue(line, line.contains("::=")) }
        ModelSupport.entries.forEach { claim ->
            assertTrue(claim.storedValue, StructuredAnswer.grammar.contains(claim.storedValue))
        }
        val markers = (1..ResearchPrompt.MAX_ANSWER_SOURCES)
            .joinToString(" | ") { "\"$it\"" }
        assertTrue(markers, StructuredAnswer.grammar.contains("\"[S\" ($markers) \"]\""))
        listOf("answer-char", "x5B", "x5D", "x7F", "escape", "hex").forEach { token ->
            assertTrue(token, StructuredAnswer.grammar.contains(token))
        }
    }
}
