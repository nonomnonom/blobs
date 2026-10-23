package dev.blobs.app.inference

import dev.blobs.app.data.library.Evidence

/** Why Blobs offered a follow-up, recorded with the turn so the offer stays inspectable. */
internal enum class SuggestionBasis(val storedValue: String) {
    UNEXPLORED_SOURCE("unexplored_source"),
    UNCOVERED_HEADING("uncovered_heading"),

    // Offers written into an older answer body by the model itself. They carry no
    // evidence link.
    LEGACY_MODEL("legacy_model");

    companion object {
        fun fromStored(value: String): SuggestionBasis? = values().firstOrNull {
            it.storedValue == value
        }
    }
}

/**
 * One next-question offer. `text` is always a verbatim fragment of local library
 * material, never app- or model-invented prose, and `sourceIndex` points at the
 * evidence entry that produced it, or is [NO_SOURCE] when no evidence backs it.
 */
internal data class FollowUp(
    val text: String,
    val basis: SuggestionBasis,
    val sourceIndex: Int = NO_SOURCE
) {
    companion object {
        const val NO_SOURCE = -1
    }
}

/**
 * Builds the next-question offers for a finished turn from the question, its
 * evidence, and the answer body alone.
 *
 * The rules are pure and local: no second completion, no second retrieval, and
 * no participation in the prompt or native runtime.
 */
internal object FollowUpSuggestions {
    const val MAX = 3

    private const val MIN_CHARS = 5
    private const val MAX_CHARS = 120
    private const val MAX_HEADING_WORDS = 12
    private const val MIN_TERM_LENGTH = 4
    private const val BULLET_MARKERS = "*-•·#`>"
    private val sentencePunctuation = Regex("[.!?:;]\\s*$")
    private val listMarker = Regex("^(?:[*\\-+•·]|\\d+[.)])\\s+")
    private val wordSplit = Regex("[^\\p{L}\\p{N}]+")

    fun from(question: String, answer: String, evidence: List<Evidence>): List<FollowUp> {
        if (answer.isBlank() || evidence.isEmpty()) return emptyList()
        val cited = AnswerText.citedSourceIndices(answer, evidence.size)
        val answered = terms(answer, question)
        val taken = mutableSetOf(normalize(question))
        val offers = ArrayList<FollowUp>(MAX)

        evidence.forEachIndexed { index, source ->
            if (index in cited) return@forEachIndexed
            val title = source.section.ifBlank { source.title }
            offer(offers, taken, title, SuggestionBasis.UNEXPLORED_SOURCE, index)
        }
        evidence.forEachIndexed { index, source ->
            if (index !in cited || offers.size >= MAX) return@forEachIndexed
            heading(source, answered)?.let { heading ->
                offer(offers, taken, heading, SuggestionBasis.UNCOVERED_HEADING, index)
            }
        }
        return offers.take(MAX)
    }

    /** One `basis|sourceIndex|text` record per line; malformed input decodes to null. */
    fun encode(suggestions: List<FollowUp>): String = suggestions
        .filter { it.basis != SuggestionBasis.LEGACY_MODEL }
        .take(MAX)
        .joinToString("\n") { offer ->
            "${offer.basis.storedValue}|${offer.sourceIndex}|${offer.text}"
        }

    fun decodeOrNull(value: String): List<FollowUp>? {
        if (value.isBlank()) return emptyList()
        val offers = value.lines().map { line ->
            val parts = line.split('|')
            if (parts.size != 3) return null
            val basis = SuggestionBasis.fromStored(parts[0]) ?: return null
            if (basis == SuggestionBasis.LEGACY_MODEL) return null
            val sourceIndex = parts[1].toIntOrNull() ?: return null
            val text = parts[2]
            if (sourceIndex < 0 || text.length !in MIN_CHARS..MAX_CHARS || !isSendable(text)) {
                return null
            }
            FollowUp(text, basis, sourceIndex)
        }
        return offers.take(MAX).toList()
    }

    /** Offers recovered from an answer body written by an older build. */
    fun legacy(parsed: List<String>): List<FollowUp> = parsed
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.length in MIN_CHARS..MAX_CHARS && isSendable(it) }
        .take(MAX)
        .map { FollowUp(it, SuggestionBasis.LEGACY_MODEL) }

    private fun heading(source: Evidence, answered: Set<String>): String? = source.text
        .lineSequence()
        .map(String::trim)
        .filter { isHeadingish(it) }
        .firstOrNull { terms(it).none { term -> term in answered } }

    private fun offer(
        offers: MutableList<FollowUp>,
        taken: MutableSet<String>,
        raw: String,
        basis: SuggestionBasis,
        sourceIndex: Int
    ) {
        if (offers.size >= MAX) return
        val text = raw.trim().replace(Regex("\\s+"), " ")
        val key = normalize(text)
        if (text.length !in MIN_CHARS..MAX_CHARS || key in taken || !isSendable(text)) return
        taken.add(key)
        offers.add(FollowUp(text, basis, sourceIndex))
    }

    /** A candidate must survive encode/decode unchanged and be acceptable as a question. */
    private fun isSendable(text: String): Boolean =
        '\n' !in text && '|' !in text && text.none(Char::isISOControl) &&
            text.any(Char::isLetterOrDigit)

    private fun isHeadingish(line: String): Boolean {
        if (line.length !in MIN_CHARS..MAX_CHARS || !isSendable(line)) return false
        if (line.first() in BULLET_MARKERS || listMarker.containsMatchIn(line)) return false
        if (line.endsWith(")") || line.endsWith("]")) return false
        if (sentencePunctuation.containsMatchIn(line)) return false
        return words(line).size <= MAX_HEADING_WORDS
    }

    private fun words(value: String): List<String> =
        value.split(wordSplit).filter(String::isNotBlank)

    private fun terms(vararg texts: String): Set<String> = texts
        .flatMap(::words)
        .map { it.lowercase() }
        .filter { it.length >= MIN_TERM_LENGTH && it !in STOPWORDS }
        .toSet()

    private fun normalize(value: String): String = value.lowercase().trim()

    private val STOPWORDS = setOf(
        "that", "this", "from", "have", "with", "will", "than", "then", "into",
        "over", "about", "which", "when", "what", "does", "did", "were", "been",
        "would", "could", "should", "there", "their", "them", "they", "your",
        "yours", "ours", "its", "his", "hers", "who", "whom", "whose", "while",
        "because", "before", "after", "again", "once", "only", "also", "such",
        "same", "each", "very", "more", "most", "less", "least", "much", "many"
    )
}
