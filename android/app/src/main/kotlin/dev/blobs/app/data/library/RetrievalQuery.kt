package dev.blobs.app.data.library

import java.text.Normalizer
import java.util.Locale

object RetrievalQuery {
    private val stopWords = setOf(
        "the", "and", "or", "not", "for", "with", "that", "this", "what", "how", "why",
        "are", "does", "can", "you", "about", "from", "which", "have", "would", "their",
        "they", "its", "these", "those", "compare", "explain", "between", "difference",
        "versus", "could", "do", "did", "be", "it", "into", "also", "tell", "please", "me",
        "more", "than", "when", "where", "who", "was", "were", "has", "had", "is", "of",
        "to", "by", "on", "in", "an", "as", "at", "if", "we", "us", "our", "describe"
    )
    private val broadTitleIntentWords = stopWords + setOf(
        "i",
        "should",
        "know",
        "before",
        "visiting",
        "visit",
        "around"
    )
    private val followUp = Regex(
        "^(and\\b|what about\\b|how about\\b|tell me more\\b|go on\\b|" +
            "why[?.!]*$|why (is|was) that\\b|" +
            "(how|what|why|can|could|does|do|is|are) (does |do |is |are |would |can )?" +
            "(it|its|they|their|them|that|this|these|those|both)\\b)"
    )

    fun resolve(question: String, previousQuestion: String?): String {
        if (previousQuestion.isNullOrBlank()) return question
        val normalized = question.trim().lowercase(Locale.ROOT)
        return if (followUp.containsMatchIn(normalized)) {
            "$question ${previousQuestion.take(300)}"
        } else {
            question
        }
    }

    private fun normalized(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    fun nameKey(text: String): String =
        Regex("[\\p{L}\\p{N}]+").findAll(normalized(text)).joinToString(" ") { it.value }

    fun isBroadTitleQuestion(question: String, title: String): Boolean {
        val titleWords = nameKey(title).split(' ').toSet()
        val questionTerms = nameKey(question).split(' ').filter {
            it.length >= 2 && it !in broadTitleIntentWords
        }
        return questionTerms.isNotEmpty() && questionTerms.all { it in titleWords }
    }

    data class NameCandidate(val key: String, val words: IntRange)

    fun names(question: String): List<NameCandidate> {
        val words = Regex("[\\p{L}\\p{N}]+").findAll(normalized(question))
            .map { it.value }.take(40).toList()
        return (minOf(6, words.size) downTo 1).flatMap { length ->
            (0..words.size - length).mapNotNull { start ->
                val phrase = words.subList(start, start + length)
                val key = phrase.joinToString(" ")
                if (key.length in 2..600 && phrase.any { it !in stopWords }) {
                    NameCandidate(key, start until start + length)
                } else {
                    null
                }
            }
        }
    }

    fun match(question: String, titles: List<String> = emptyList()): String {
        val words = Regex("[\\p{L}\\p{N}]{2,}").findAll(normalized(question))
            .map { it.value }.toList()
        val terms = words.filter { it !in stopWords }.distinct().take(20)
            .map { "\"$it\"" }
        val phraseTerms = words.zipWithNext { left, right ->
            if (left !in stopWords && right !in stopWords) "\"$left $right\"" else null
        }.filterNotNull().distinct().take(20)
        val titlePhrases = titles.take(4).mapNotNull { title ->
            val tokens = Regex("[\\p{L}\\p{N}]+").findAll(normalized(title))
                .map { it.value }.joinToString(" ")
            tokens.takeIf { it.isNotEmpty() }?.let { "title:\"$it\"" }
        }
        return (terms + phraseTerms + titlePhrases).distinct().joinToString(" OR ")
    }
}
