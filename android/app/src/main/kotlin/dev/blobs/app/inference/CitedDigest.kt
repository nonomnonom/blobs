package dev.blobs.app.inference

import dev.blobs.app.data.library.Evidence

/**
 * A cited, verbatim answer for a turn whose completion used none of its sources.
 *
 * The reader still gets an answer with inspectable evidence instead of model prose presented as
 * fact. Markers keep the evidence list's own indices, so a line cites the passage it came from even
 * when an earlier passage produced no usable sentence.
 */
internal object CitedDigest {
    private const val MAXIMUM_SOURCES = 6
    private const val MAXIMUM_LINE_CHARACTERS = 240
    private const val MINIMUM_SENTENCE_CHARACTERS = 40
    private val sentenceEnd = Regex("(?<=[.!?])\\s+")
    private val whitespace = Regex("\\s+")
    private val navigationBullet = Regex("\\s[•|»]\\s")

    fun build(evidence: List<Evidence>, maximumSources: Int = MAXIMUM_SOURCES): String? {
        if (maximumSources < 1) return null
        val lines = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        evidence.take(maximumSources).forEachIndexed { index, source ->
            val sentence = firstUsableSentence(source.text) ?: return@forEachIndexed
            if (seen.add(sentence)) lines += "- $sentence [S${index + 1}]"
        }
        return lines.joinToString("\n").ifBlank { null }
    }

    private fun firstUsableSentence(text: String): String? = text.lineSequence()
        .filterNot { line -> navigationBullet.findAll(line).count() >= 2 }
        .flatMap { line -> line.split(sentenceEnd).asSequence() }
        .map { it.replace(whitespace, " ").trim() }
        .firstOrNull { it.length >= MINIMUM_SENTENCE_CHARACTERS }
        ?.let(::trimToLimit)

    private fun trimToLimit(sentence: String): String {
        if (sentence.length <= MAXIMUM_LINE_CHARACTERS) return sentence
        val boundary = sentence.lastIndexOf(' ', MAXIMUM_LINE_CHARACTERS)
        return sentence.substring(0, if (boundary > 0) boundary else MAXIMUM_LINE_CHARACTERS)
            .trimEnd()
    }
}
