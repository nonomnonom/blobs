package dev.blobs.app.data.library

import java.text.Normalizer
import java.util.Locale

object RetrievalQuery {
    private val wordPattern = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "the", "and", "or", "not", "for", "with", "that", "this", "what", "how", "why",
        "are", "does", "can", "you", "about", "from", "which", "have", "would", "their",
        "they", "its", "these", "those", "compare", "explain", "between", "difference",
        "versus", "could", "do", "did", "be", "it", "into", "also", "tell", "please", "me",
        "more", "than", "when", "where", "who", "was", "were", "has", "had", "is", "of",
        "to", "by", "on", "in", "an", "as", "at", "if", "we", "us", "our", "describe",
        "guide", "list"
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
    private val titleNoiseWords = setOf("city", "town")
    private val followUp = Regex(
        "^(and\\b|what about\\b|how about\\b|tell me more\\b|go on\\b|" +
            "why[?.!]*$|why (is|was) that\\b|" +
            "(how|what|why|which|can|could|does|do|is|are) " +
            "(does |do |is |are |would |can |of )?" +
            "(it|its|they|their|them|that|this|these|those|both|one|" +
            "the (first|second|third|former|latter))\\b)"
    )
    private val responseLengthInstruction = Regex(
        "(?i)(?:^|(?<=[.!?\\n]))\\s*(?:please\\s+)?" +
            "(?:answer|respond)\\s+in\\s+(?:[0-9]+|one|two|three)\\s+" +
            "(?:(?:short|brief|concise)\\s+)?(?:sentences?|paragraphs?|words?)\\s*" +
            "(?:[.!?:](?=\\s|$)|$)"
    )

    fun forSearch(question: String): String = responseLengthInstruction.replace(question, "").trim()

    fun resolve(question: String, previousQuestions: List<String>): String {
        if (!isFollowUp(question)) return question
        val recent = previousQuestions.filter(String::isNotBlank)
        if (recent.isEmpty()) return question
        val anchorIndex = recent.indexOfLast { !isFollowUp(it) }.coerceAtLeast(0)
        val anchor = recent[anchorIndex].take(300)
        val continuation = recent.drop(anchorIndex + 1)
            .joinToString(" ") { it.take(300) }.takeLast(300)
        val context = listOf(anchor, continuation).filter(String::isNotBlank).joinToString(" ")
        return "$question $context"
    }

    private fun isFollowUp(question: String): Boolean =
        followUp.containsMatchIn(question.trim().lowercase(Locale.ROOT))

    private fun normalized(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    fun nameKey(text: String): String =
        wordPattern.findAll(normalized(text)).joinToString(" ") { it.value }

    fun isBroadTitleQuestion(question: String, title: String): Boolean {
        val titleWords = nameKey(title).split(' ').toSet()
        val questionTerms = nameKey(question).split(' ').filter {
            it.length >= 2 && it !in broadTitleIntentWords
        }
        return questionTerms.isNotEmpty() && questionTerms.all { it in titleWords }
    }

    fun isDirectTitleMatch(question: String, title: String): Boolean {
        if (isBroadTitleQuestion(question, title)) return true
        val titleWords = nameKey(title).split(' ')
        if (titleWords.size > 1) return true
        val titleWord = titleWords.singleOrNull() ?: return false
        return wordPattern.findAll(question).any { match ->
            match.range.first > 0 &&
                match.value.first().isUpperCase() &&
                nameKey(match.value) == titleWord
        }
    }

    fun titleTerms(question: String): Set<String> = nameKey(question).split(' ')
        .filter { it.length >= 2 && it !in stopWords && it !in titleNoiseWords }
        .toSet()

    fun topicTerms(question: String, subjectTitles: List<String>): Set<String> =
        if (subjectTitles.isEmpty()) {
            emptySet()
        } else {
            titleTerms(question) - subjectTitles.flatMap { titleTerms(it) }.toSet() -
                broadTitleIntentWords - setOf("best")
        }

    fun titleOverlap(questionTerms: Set<String>, title: String): Int {
        val titleWords = nameKey(title).split(' ').toSet()
        return questionTerms.count { term ->
            titleWords.any { word ->
                word == term || isShortTitleExpansion(term, word)
            }
        }
    }

    fun evidenceOverlap(
        questionTerms: Set<String>,
        title: String,
        section: String,
        text: String
    ): Int {
        val terms = questionTerms - broadTitleIntentWords - titleNoiseWords
        if (terms.isEmpty()) return 0
        val words = wordPattern.findAll("$title $section $text".lowercase(Locale.ROOT))
            .mapTo(mutableSetOf()) { it.value }
        return terms.count(words::contains)
    }

    private fun isShortTitleExpansion(term: String, word: String): Boolean =
        term.length >= 3 && word.startsWith(term) && word.length - term.length <= 3

    data class NameCandidate(val key: String, val words: IntRange)

    fun names(question: String): List<NameCandidate> {
        val words = wordPattern.findAll(normalized(question))
            .map { it.value }.take(40).toList()
        return (minOf(6, words.size) downTo 1).flatMap { length ->
            (0..words.size - length).mapNotNull { start ->
                val phrase = words.subList(start, start + length)
                val key = phrase.joinToString(" ")
                if (key.length in 2..600 && phrase.any { it.length >= 2 && it !in stopWords }) {
                    NameCandidate(key, start until start + length)
                } else {
                    null
                }
            }
        }
    }

    fun match(question: String, titles: List<String> = emptyList()): String {
        val words = wordPattern.findAll(normalized(question))
            .map { it.value }.filter { it.length >= 2 }.toList()
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
