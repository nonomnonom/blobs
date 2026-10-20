package dev.blobs.app.inference

object AnswerText {
    data class Citation(val range: IntRange, val sourceIndex: Int)

    private val namespacedCitation = Regex("\\[S(\\d+)]")
    private val groupedNamespacedCitation = Regex("\\[(S\\d+(?:\\s*,\\s*S\\d+)+)\\]")
    private val groupedSourceId = Regex("S(\\d+)")
    private val legacyCitation = Regex("\\[(\\d{1,6})]")
    private val textSubscript = Regex(
        """```[\s\S]*?(?:```|$)|`[^`\n]*`|\$(?:\\text|\\mathrm)\{([A-Za-z]+)\}_(?:\{([0-9]+)\}|([0-9]+))\$"""
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

    fun visible(raw: String): String {
        val start = raw.indexOf("<think>")
        if (start < 0) return raw.trimStart()
        val end = raw.indexOf("</think>", start)
        return if (end <
            0
        ) {
            raw.substring(0, start).trim()
        } else {
            (raw.substring(0, start) + raw.substring(end + 8)).trimStart()
        }
    }

    fun validSourceCitations(text: String, count: Int): String {
        val grouped = groupedNamespacedCitation.replace(text) { match ->
            val sources = groupedSourceId.findAll(match.groupValues[1])
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 1..count }
                .distinct()
                .toList()
            sources.takeIf { it.isNotEmpty() }?.joinToString(", ", "[", "]") { "S$it" } ?: ""
        }
        return Regex("\\[(S?)(\\d+)]").replace(grouped) {
            val source = it.groupValues[2].toIntOrNull() ?: 0
            if (it.groupValues[1] == "S" && source in 1..count) it.value else ""
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
        val hasNamespacedCitations = groupedNamespacedCitation.containsMatchIn(text) ||
            namespacedCitation.containsMatchIn(text)
        if (hasNamespacedCitations) return namespaced
        return legacyCitation.findAll(text).mapNotNull { match ->
            val source = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (source in 1..count) Citation(match.range, source - 1) else null
        }.toList()
    }
}
