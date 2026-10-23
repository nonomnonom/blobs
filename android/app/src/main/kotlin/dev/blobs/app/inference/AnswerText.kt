package dev.blobs.app.inference

internal object AnswerText {
    data class Citation(val range: IntRange, val sourceIndex: Int)

    private val namespacedCitation = Regex("\\[S(\\d+)]")
    private val groupedNamespacedCitation = Regex("\\[(S\\d+(?:\\s*,\\s*S\\d+)+)]")
    private val groupedSourceId = Regex("S(\\d+)")
    private val legacyCitation = Regex("\\[(\\d{1,6})]")
    private val bracketedReference = Regex("\\[([^\\]\\n]{1,80})\\]")
    private val containsNamespacedReference = Regex("(?i)(?:^|[,;]\\s*)S\\d+(?:\\b|[,;])")
    private val numericReferenceList = Regex("^\\s*\\d+(?:\\s*[,;]\\s*\\d+)*\\s*$")
    private val namespacedSourceId = Regex("(?i)\\bS(\\d+)\\b")
    private val numericSourceId = Regex("\\d+")
    private val textSubscript = Regex(
        """```[\s\S]*?(?:```|$)|`[^`\n]*`|\$(?:\\text|\\mathrm)\{([A-Za-z]+)}_(?:\{([0-9]+)}|([0-9]+))\$"""
    )

    fun readable(text: String): String = textSubscript.replace(text) { match ->
        val letters = match.groupValues[1]
        if (letters.isEmpty()) {
            match.value
        } else {
            val digits = match.groupValues[2].ifEmpty { match.groupValues[3] }
            letters + digits.map { '₀' + (it - '0') }.joinToString("")
        }
    }

    /**
     * Streaming tokens can end partway through a UTF-8 code point. Keep that
     * incomplete suffix out of the displayed checkpoint; the next token will
     * make it available on the next render.
     */
    fun decodeCompleteUtf8Prefix(bytes: ByteArray): String =
        decodeCompleteUtf8Prefix(bytes, bytes.size)

    fun decodeCompleteUtf8Prefix(bytes: ByteArray, length: Int): String {
        require(length in 0..bytes.size) { "UTF8_LENGTH" }
        var index = 0
        while (index < length) {
            val first = bytes[index].toInt() and 0xff
            val width = when {
                first <= 0x7f -> 1
                first in 0xc2..0xdf -> 2
                first in 0xe0..0xef -> 3
                first in 0xf0..0xf4 -> 4
                else -> 1
            }
            if (index + width > length) return String(bytes, 0, index, Charsets.UTF_8)
            if (width > 1 && (1 until width).any { offset ->
                    (bytes[index + offset].toInt() and 0xc0) != 0x80
                }
            ) {
                index++
            } else {
                index += width
            }
        }
        return String(bytes, 0, length, Charsets.UTF_8)
    }

    fun visible(raw: String): String {
        val text = raw.replace("\u0000", "")
        val start = text.indexOf("<think>")
        if (start < 0) return text.trimStart()
        val end = text.indexOf("</think>", start)
        return if (end < 0) {
            text.substring(0, start).trim()
        } else {
            (text.substring(0, start) + text.substring(end + 8)).trimStart()
        }
    }

    fun validSourceCitations(text: String, count: Int): String =
        bracketedReference.replace(text) { match ->
            val contents = match.groupValues[1]
            val namespaced = containsNamespacedReference.containsMatchIn(contents)
            if (!namespaced && !numericReferenceList.matches(contents)) {
                match.value
            } else {
                val ids = (if (namespaced) {
                    namespacedSourceId.findAll(contents).map { it.groupValues[1] }
                } else {
                    numericSourceId.findAll(contents).map { it.value }
                }).mapNotNull { it.toIntOrNull() }
                    .filter { it in 1..count }
                    .distinct()
                    .toList()
                ids.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ", "[", "]") { "S$it" }
                    ?: ""
            }
        }

    fun citations(text: String, count: Int): List<Citation> {
        val namespaced = buildList {
            groupedNamespacedCitation.findAll(text).forEach { group ->
                groupedSourceId.findAll(group.groupValues[1]).forEach source@{ sourceMatch ->
                    val source = sourceMatch.groupValues[1].toIntOrNull() ?: return@source
                    if (source in 1..count) {
                        val start = group.range.first + 1 + sourceMatch.range.first
                        val end = group.range.first + 1 + sourceMatch.range.last
                        add(Citation(start..end, source - 1))
                    }
                }
            }
            namespacedCitation.findAll(text).forEach citation@{ match ->
                val source = match.groupValues[1].toIntOrNull() ?: return@citation
                if (source in 1..count) add(Citation(match.range, source - 1))
            }
        }.sortedBy { it.range.first }
        if (groupedNamespacedCitation.containsMatchIn(text) ||
            namespacedCitation.containsMatchIn(text)
        ) {
            return namespaced
        }
        return legacyCitation.findAll(text).mapNotNull { match ->
            match.groupValues[1].toIntOrNull()?.takeIf { it in 1..count }?.let {
                Citation(match.range, it - 1)
            }
        }.toList()
    }

    fun citedSourceIndices(text: String, count: Int): Set<Int> =
        citations(text, count).mapTo(linkedSetOf()) { it.sourceIndex }

    /**
     * A conservative structural guard for source-required prose. A citation
     * immediately following terminal punctuation still belongs to that
     * sentence, as in "The route takes six weeks. [S1]".
     */
    fun hasCompleteCitationCoverage(text: String, count: Int): Boolean {
        val validCitations = citations(text, count)
        if (validCitations.isEmpty()) return false
        var foundProse = false
        var start = 0
        var index = 0

        fun covered(end: Int): Boolean {
            if (!text.substring(start, end).any { it.isLetterOrDigit() }) return true
            foundProse = true
            return validCitations.any { citation ->
                citation.range.first >= start && citation.range.last < end
            }
        }

        while (index < text.length) {
            when (text[index]) {
                '\n' -> {
                    if (!covered(index)) return false
                    start = index + 1
                    index = start
                }

                '.', '!', '?' -> {
                    var end = index + 1
                    while (true) {
                        var next = end
                        while (next < text.length && text[next].isWhitespace()) next++
                        val markerEnd = validCitationMarkerEnd(text, next, validCitations)
                            ?: break
                        end = markerEnd
                    }
                    if (!covered(end)) return false
                    start = end
                    index = end
                }

                else -> index++
            }
        }
        return covered(text.length) && foundProse
    }

    private fun validCitationMarkerEnd(
        text: String,
        start: Int,
        citations: List<Citation>
    ): Int? {
        if (start >= text.length || text[start] != '[') return null
        val end = text.indexOf(']', start + 1)
        if (end < 0) return null
        return if (citations.any { citation ->
                citation.range.first >= start && citation.range.last <= end
            }
        ) {
            end + 1
        } else {
            null
        }
    }
}
