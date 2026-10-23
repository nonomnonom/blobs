package dev.blobs.app.inference

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The local-source support a completion claims for itself, as one JSON enumeration value. */
internal enum class ModelSupport(val storedValue: String) {
    SOURCED("sourced"),
    PARTIAL("partial"),
    UNSOURCED("unsourced");

    companion object {
        fun fromStored(value: String): ModelSupport? = values().firstOrNull {
            it.storedValue == value
        }
    }
}

/** A decoded model payload: the prose to show and the support the model claimed. */
internal data class ModelOutput(val answer: String, val support: ModelSupport)

/**
 * Blobs' response contract: one JSON document whose answer comes first and whose support claim
 * comes last.
 *
 * The shape is not requested in prose. [grammar] goes to the native sampler, which can only accept
 * a token that keeps the document valid, and [decode] re-checks the finished document before
 * anything is persisted. Square brackets can therefore only appear as real citation markers, an
 * answer always terminates, and no preamble or trailing commentary can reach the reader.
 */
internal object StructuredAnswer {
    const val GRAMMAR_ROOT = "root"

    /** Markers the grammar admits, derived from the retrieval bound that owns them. */
    private val markerAlternation = (1..ResearchPrompt.MAX_ANSWER_SOURCES)
        .joinToString(" | ") { "\"$it\"" }

    /**
     * GBNF for the pinned native runtime. Each production stays on one line because the parser
     * ends a rule at a newline unless the continuation is grouped. `answer-char` excludes the
     * quote, the backslash, control points, and both square brackets, so the only bracketed text a
     * completion can emit is a marker listed in [markerAlternation].
     */
    val grammar = """
        |root          ::= answer-object
        |answer-object ::= "{" ws answer-pair "," support-pair ws "}"
        |answer-pair   ::= "\"answer\"" ":" ws answer-string
        |support-pair  ::= "\"support\"" ":" ws support
        |support       ::= "\"sourced\"" | "\"partial\"" | "\"unsourced\""
        |answer-string ::= "\"" answer-char* "\""
        |answer-char   ::= [^"\\\x7F\x00-\x1F\x5B\x5D] | marker | escape
        |marker        ::= "[S" ($markerAlternation) "]"
        |escape        ::= "\\" (["\\/bfnrt] | "u" hex hex hex hex)
        |hex           ::= [0-9a-fA-F]
        |ws            ::= | " " | "\n" [ \t]{0,20}
        |
    """.trimMargin()

    private val strictJson = Json { isLenient = false }

    /** Null unless the whole document is exactly one answer object with a non-blank answer. */
    fun decode(raw: String): ModelOutput? {
        val document = try {
            strictJson.parseToJsonElement(raw)
        } catch (_: SerializationException) {
            return null
        }
        val fields = document as? JsonObject ?: return null
        if (fields.size != 2) return null
        val answer = (fields["answer"] as? JsonPrimitive)
            ?.takeIf(JsonPrimitive::isString)
            ?.content
            ?: return null
        val claimed = (fields["support"] as? JsonPrimitive)
            ?.takeIf(JsonPrimitive::isString)
            ?.content
            ?.let { ModelSupport.fromStored(it) }
            ?: return null
        if (answer.isBlank()) return null
        return ModelOutput(answer, claimed)
    }

    /**
     * The answer text readable from an incomplete document, so streaming, cancellation, and a
     * token-limited turn still render prose instead of raw JSON. Null means no usable prefix has
     * arrived yet and the caller keeps the text it already showed.
     */
    fun partialText(raw: String): String? {
        var index = skipWhitespace(raw, 0)
        if (index >= raw.length || raw[index] != '{') return null
        index = skipWhitespace(raw, index + 1)
        if (!raw.startsWith("\"answer\"", index)) return null
        index = skipWhitespace(raw, index + "\"answer\"".length)
        if (index >= raw.length || raw[index] != ':') return null
        index = skipWhitespace(raw, index + 1)
        if (index >= raw.length || raw[index] != '"') return null
        return scanString(raw, index + 1)
    }

    private fun skipWhitespace(value: String, from: Int): Int {
        var index = from
        while (index < value.length && value[index].isWhitespace()) index++
        return index
    }

    /**
     * Reads a JSON string body while decoding escapes. An escape cut off by the end of the buffer
     * stops at the last complete character, so a half-written unicode sequence is never shown.
     */
    private fun scanString(raw: String, start: Int): String? {
        val text = StringBuilder(raw.length - start)
        var index = start
        while (index < raw.length) {
            val current = raw[index]
            when {
                current == '"' -> return text.toString()

                current == '\\' -> {
                    val escape = escapeAt(raw, index) ?: return null
                    if (escape.text == null) return text.toString()
                    text.append(escape.text)
                    index = escape.next
                }

                current.code < 0x20 || current.code == 0x7F -> return null

                else -> {
                    text.append(current)
                    index++
                }
            }
        }
        return text.toString()
    }

    private class Escape(val text: String?, val next: Int)

    private val singleEscapes = mapOf(
        '"' to "\"",
        '\\' to "\\",
        '/' to "/",
        'b' to Char(0x08).toString(),
        'f' to Char(0x0C).toString(),
        'n' to Char(0x0A).toString(),
        'r' to Char(0x0D).toString(),
        't' to Char(0x09).toString()
    )

    /**
     * The decoded escape and the next read position. A null text means the buffer stopped inside
     * the escape; a null escape means the sequence can never become valid JSON.
     */
    private fun escapeAt(raw: String, index: Int): Escape? {
        val marker = raw.getOrNull(index + 1) ?: return Escape(null, index)
        singleEscapes[marker]?.let { return Escape(it, index + 2) }
        if (marker != 'u') return null
        val code = hexAt(raw, index + 2) ?: return Escape(null, index)
        if (!code.isHighSurrogate()) return Escape(code.toString(), index + 6)
        val pair = hexAt(raw, index + 6) ?: return Escape(null, index)
        if (!pair.isLowSurrogate()) return null
        return Escape("$code$pair", index + 10)
    }

    private fun hexAt(raw: String, start: Int): Char? {
        if (start + HEX_LENGTH > raw.length) return null
        var value = 0
        for (offset in 0 until HEX_LENGTH) {
            val digit = Character.digit(raw[start + offset], 16)
            if (digit < 0) return null
            value = value * 16 + digit
        }
        return value.toChar()
    }

    private const val HEX_LENGTH = 4
}
