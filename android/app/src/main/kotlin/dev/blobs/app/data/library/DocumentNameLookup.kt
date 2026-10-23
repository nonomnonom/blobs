package dev.blobs.app.data.library

import androidx.sqlite.SQLiteConnection

/** Resolves unambiguous canonical titles and reviewed aliases for one library. */
internal class DocumentNameLookup {
    data class Match(val key: String, val title: String, val id: String)

    fun matchingTitles(
        db: SQLiteConnection,
        question: String,
        scope: List<String>?
    ): List<Match> {
        val candidates = RetrievalQuery.names(question)
        val keys = candidates.map { it.key }.distinct()
        if (keys.isEmpty()) return emptyList()
        val placeholders = keys.joinToString(",") { "?" }
        val matches = db.prepare(
            "SELECT n.name,min(d.title),min(d.id) FROM document_names n JOIN documents d ON d.id=n.doc_id WHERE n.name IN ($placeholders) GROUP BY n.name HAVING count(*)=1"
        ).use { statement ->
            keys.forEachIndexed { index, key -> statement.bindText(index + 1, key) }
            buildMap {
                while (statement.step()) {
                    if (scope == null || statement.getText(2) in scope) {
                        put(
                            statement.getText(0),
                            Match(
                                key = statement.getText(0),
                                title = statement.getText(1),
                                id = statement.getText(2)
                            )
                        )
                    }
                }
            }
        }
        val occupied = mutableSetOf<Int>()
        val titles = linkedMapOf<String, Match>()
        for (candidate in candidates) {
            val match = matches[candidate.key] ?: continue
            if (candidate.words.any { it in occupied }) continue
            occupied.addAll(candidate.words)
            titles.putIfAbsent(match.id, match)
            if (titles.size == MAX_TITLE_MATCHES) break
        }
        return titles.values.toList()
    }

    private companion object {
        const val MAX_TITLE_MATCHES = 4
    }
}
