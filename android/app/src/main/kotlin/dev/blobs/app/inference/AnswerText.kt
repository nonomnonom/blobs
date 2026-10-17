package dev.blobs.app.inference

object AnswerText {
    data class Citation(val range: IntRange, val sourceIndex: Int)

    private val namespacedCitation = Regex("\\[S(\\d{1,6})]")
    private val legacyCitation = Regex("\\[(\\d{1,6})]")

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

    fun validSourceCitations(text: String, count: Int): String =
        Regex("\\[(S?)(\\d{1,6})]").replace(text) {
            val source = it.groupValues[2].toIntOrNull() ?: 0
            if (it.groupValues[1] == "S" && source in 1..count) it.value else ""
        }

    fun citations(text: String, count: Int): List<Citation> {
        val namespaced = namespacedCitation.findAll(text).toList()
        val selected = if (namespaced.isNotEmpty()) {
            namespaced
        } else {
            legacyCitation.findAll(text).toList()
        }
        return selected.mapNotNull { match ->
            val source = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (source in 1..count) Citation(match.range, source - 1) else null
        }
    }
}
