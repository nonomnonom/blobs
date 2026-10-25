package dev.blobs.app.inference

import dev.blobs.app.data.library.Evidence

/**
 * Cited, verbatim lines taken from the retrieved passages.
 *
 * A turn whose completion used none of its sources gets one of these instead of model prose, and a
 * turn that used one source of several gets one or two lines for the sources it ignored. The reader
 * still gets an answer with inspectable evidence either way. Markers keep the evidence list's own
 * indices, so a line cites the passage it came from even when an earlier passage produced no usable
 * sentence.
 */
internal object CitedDigest {
    private const val MAXIMUM_SOURCES = 6
    private const val MAXIMUM_LINE_CHARACTERS = 240
    private const val MINIMUM_SENTENCE_CHARACTERS = 40
    private val sentenceEnd = Regex("(?<=[.!?])\\s+")
    private val whitespace = Regex("\\s+")
    private val navigationBullet = Regex("\\s[•|»]\\s")

    /**
     * Decides what a turn actually shows. An answer that used none of its sources is replaced; an
     * answer that marked one source out of three or more keeps its prose and gains two lines for the
     * sources it ignored, which is the measured shape of a comparison that dropped a subject.
     *
     * A turn that ran out of tokens or failed keeps its prose only when that prose cites something:
     * an uncited run-out is a list of claims, and the reader gets the cited digest instead.
     */
    fun gate(answer: String, evidence: List<Evidence>, finished: Boolean = true): String? {
        if (evidence.isEmpty()) return null
        val cited = AnswerText.citedSourceIndices(answer, evidence.size)
        if (!finished) return if (cited.isEmpty()) build(evidence) else null
        val digest = when {
            cited.isEmpty() -> build(evidence)

            cited.size == 1 && evidence.size >= 3 -> build(
                evidence,
                skipIndices = skipFor(evidence, cited.first()),
                maximumLines = 2
            )

            else -> null
        } ?: return null
        return if (cited.isEmpty()) digest else "$answer\n\n$digest"
    }

    /**
     * Sources a top-up must not repeat: the cited passage itself and every passage from the same
     * guide, so a one-sided answer gains the subjects it dropped instead of the one it used.
     */
    private fun skipFor(evidence: List<Evidence>, citedIndex: Int): Set<Int> {
        val subject = evidence[citedIndex].title.substringBefore('/').trim()
        val sameSubject = evidence.indices.filter {
            evidence[it].title.substringBefore('/').trim() == subject
        }
        return (sameSubject + citedIndex).map { it + 1 }.toSet()
    }

    fun build(
        evidence: List<Evidence>,
        skipIndices: Set<Int> = emptySet(),
        maximumLines: Int = MAXIMUM_SOURCES
    ): String? {
        if (maximumLines < 1) return null
        val lines = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for ((index, source) in evidence.withIndex()) {
            if (lines.size == maximumLines) break
            if (index + 1 in skipIndices) continue
            val sentence = firstUsableSentence(source.text) ?: continue
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
