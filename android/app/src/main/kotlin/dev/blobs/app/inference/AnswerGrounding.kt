package dev.blobs.app.inference

import dev.blobs.app.data.library.Evidence
import java.text.Normalizer
import java.util.Locale

/** A conservative lexical citation check, not a semantic entailment test. */
object AnswerGrounding {
    fun shouldWithholdCompletion(
        nativeResult: Int,
        text: String,
        evidence: List<Evidence>
    ): Boolean = (nativeResult == 0 || nativeResult == 2) &&
        text.isNotBlank() && !hasSupportedCitations(text, evidence)

    private val citation = Regex("\\[S(\\d+)]")
    private val groupedCitation = Regex("\\[(S\\d+(?:\\s*,\\s*S\\d+)+)\\]")
    private val groupedSourceId = Regex("S(\\d+)")
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val sentenceBreak = Regex("(?<=[.!?])\\s+(?=[A-Z])|\\n{2,}")
    private val stopWords = setOf(
        "a", "an", "the", "and", "or", "not", "for", "with", "that", "this", "what",
        "how", "why", "are", "does", "can", "you", "about", "from", "which", "have",
        "would", "their", "they", "its", "these", "those", "compare", "explain", "between",
        "difference", "versus", "could", "do", "did", "be", "it", "into", "also", "tell",
        "please", "me", "more", "than", "when", "where", "who", "was", "were", "has", "had",
        "is", "of", "to", "by", "on", "in", "as", "at", "if", "we", "us", "our", "describe",
        "according", "article", "passage", "source", "sources", "claim", "claims", "answer",
        "says", "said", "states", "state", "using", "used", "use", "such", "some", "all",
        "more", "most", "less", "one", "two", "three", "four", "five", "six", "also", "may",
        "might", "must", "should", "could", "would", "because", "while", "but", "then", "there",
        "here", "their", "them", "both", "each", "every", "another", "same", "different"
    )

    /**
     * Returns true only when every cited sentence has meaningful lexical overlap with its cited
     * passages. This catches clearly unrelated references; it cannot prove that a passage entails
     * a claim.
     */
    fun hasSupportedCitations(text: String, evidence: List<Evidence>): Boolean {
        if (evidence.isEmpty()) return false
        val sentences = text.trim().split(sentenceBreak).filter(String::isNotBlank)
        var citedSentenceCount = 0
        for (sentence in sentences) {
            val references = buildList {
                groupedCitation.findAll(sentence).forEach { group ->
                    groupedSourceId.findAll(group.groupValues[1]).forEach { match ->
                        add(match.groupValues[1].toIntOrNull()?.minus(1) ?: -1)
                    }
                }
                citation.findAll(sentence).forEach { match ->
                    add(match.groupValues[1].toIntOrNull()?.minus(1) ?: -1)
                }
            }.distinct()
            if (references.isEmpty()) continue
            citedSentenceCount++
            if (references.any { it !in evidence.indices }) return false

            val claim = groupedCitation.replace(sentence, "")
            val claimTerms = terms(citation.replace(claim, ""))
            if (claimTerms.isEmpty()) return false
            val requiredOverlap = minOf(2, claimTerms.size)
            val citedTerms = references.map { index ->
                terms("${evidence[index].title} ${evidence[index].section} ${evidence[index].text}")
            }
            if (citedTerms.any { claimTerms.intersect(it).size < requiredOverlap }) return false
            val sourceTerms = citedTerms.flatten().toSet()
            val overlap = claimTerms.intersect(sourceTerms).size
            if (overlap < requiredOverlap || overlap.toDouble() / claimTerms.size < 0.25) {
                return false
            }
        }
        return citedSentenceCount > 0
    }

    private fun terms(text: String): Set<String> = words.findAll(
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    ).mapNotNull { match ->
        val token = match.value
        if (token.length < 3 || token in stopWords || token.all(Char::isDigit)) {
            null
        } else {
            stem(token).takeIf { it.length >= 3 && it !in stopWords }
        }
    }.toSet()

    private fun stem(token: String): String = when {
        token.length > 5 && token.endsWith("ies") -> token.dropLast(3) + "y"
        token.length > 6 && token.endsWith("ing") -> token.dropLast(3)
        token.length > 5 && token.endsWith("ed") -> token.dropLast(2)
        token.length > 5 && token.endsWith("es") -> token.dropLast(2)
        token.length > 4 && token.endsWith("s") -> token.dropLast(1)
        else -> token
    }
}
