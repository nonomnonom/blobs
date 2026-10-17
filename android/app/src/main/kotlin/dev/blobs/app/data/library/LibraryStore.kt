package dev.blobs.app.data.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.blobs.app.data.local.PackageEntity
import java.io.File

class LibraryStore {
    fun validate(file: File) {
        open(file.path).use { db ->
            db.prepare("PRAGMA quick_check").use { check(it.step() && it.getText(0) == "ok") }
            val version = version(db)
            check(version in 1L..3L)
            db.prepare("SELECT count(*) FROM sqlite_master WHERE type IN ('view','trigger')").use {
                check(it.step() && it.getLong(0) == 0L)
            }
            db.prepare(
                "SELECT id,title,publisher,url,license,updated,saved,body,collection FROM documents LIMIT 1"
            ).use {
                check(it.step())
            }
            val passageColumns = if (version == 3L) {
                "SELECT id,doc_id,section,title FROM passages LIMIT 1"
            } else {
                "SELECT id,doc_id,body,section FROM passages LIMIT 1"
            }
            db.prepare(passageColumns).use {
                check(it.step())
            }
            db.prepare(
                "SELECT count(*) FROM passages_fts WHERE passages_fts MATCH 'knowledge'"
            ).use {
                check(it.step())
            }
            db.prepare("SELECT max(length(body)), count(*) FROM documents").use {
                check(it.step() && it.getLong(0) <= 1_000_000 && it.getLong(1) <= 5_000_000)
            }
            val limits = mapOf(
                "id" to 100, "title" to 300, "publisher" to 300, "url" to 2000,
                "license" to 4000, "updated" to 80, "saved" to 80, "collection" to 200,
                "body" to 1_000_000
            )
            val invalid = limits.entries.joinToString(" OR ") { (column, limit) ->
                "typeof($column)!='text' OR length($column)>$limit"
            }
            db.prepare(
                "SELECT count(*) FROM documents WHERE $invalid OR length(id)=0 OR id GLOB '*[^a-zA-Z0-9_-]*'"
            ).use { check(it.step() && it.getLong(0) == 0L) }
            if (version >= 2L) {
                db.prepare("SELECT title,section FROM passages_fts LIMIT 1").use {
                    check(it.step())
                }
                if (version == 2L) {
                    db.prepare(
                        "SELECT count(*) FROM passages p JOIN documents d ON d.id=p.doc_id WHERE typeof(p.title)!='text' OR (p.title!='' AND p.title!=d.title) OR length(p.body)>1400"
                    ).use { check(it.step() && it.getLong(0) == 0L) }
                } else {
                    validateSpanBackedPassages(db)
                }
                if (hasNames(db)) {
                    db.prepare("SELECT count(*) FROM document_names").use {
                        check(it.step() && it.getLong(0) <= 10_000_000)
                    }
                    db.prepare(
                        "SELECT count(*) FROM document_names n LEFT JOIN documents d ON d.id=n.doc_id WHERE d.id IS NULL OR typeof(n.name)!='text' OR length(n.name) NOT BETWEEN 1 AND 300 OR instr(n.name,char(0))>0"
                    ).use { check(it.step() && it.getLong(0) == 0L) }
                    db.prepare(
                        "SELECT count(*) FROM (SELECT name,doc_id FROM document_names GROUP BY name,doc_id HAVING count(*)>1)"
                    ).use { check(it.step() && it.getLong(0) == 0L) }
                }
            }
            db.prepare("SELECT count(*)=count(DISTINCT id) FROM documents").use {
                check(it.step() && it.getLong(0) == 1L)
            }
            if (version < 3L) {
                db.prepare(
                    "SELECT count(*) FROM passages p LEFT JOIN documents d ON d.id=p.doc_id WHERE d.id IS NULL OR typeof(p.body)!='text' OR length(p.body) NOT BETWEEN 1 AND 8000 OR typeof(p.section)!='text' OR length(p.section)>300"
                ).use { check(it.step() && it.getLong(0) == 0L) }
            }
        }
    }

    fun browse(packages: List<PackageEntity>, query: String, offset: Int = 0): List<Evidence> =
        packages.filter { it.kind == "library" }.flatMap { pack ->
            open(pack.path).use { db ->
                db.prepare(
                    "SELECT id,title,publisher,url,license,updated,saved,collection,substr(body,1,300) FROM documents WHERE title LIKE ? ESCAPE '\\' OR publisher LIKE ? ESCAPE '\\' ORDER BY title,id LIMIT ?"
                ).use { statement ->
                    val search = "%${query.replace(
                        "\\",
                        "\\\\"
                    ).replace("%", "\\%").replace("_", "\\_")}%"
                    statement.bindText(1, search)
                    statement.bindText(2, search)
                    statement.bindLong(3, (offset + 51).toLong())
                    buildList { while (statement.step()) add(read(statement, pack)) }
                }
            }
        }.sortedWith(compareBy({ it.title }, { it.id })).drop(offset).take(51)

    fun readDocument(packages: List<PackageEntity>, id: String): Evidence? {
        val pack = packages.find { id.startsWith(it.key + "/") } ?: return null
        return open(pack.path).use { db ->
            db.prepare(
                "SELECT id,title,publisher,url,license,updated,saved,collection,body FROM documents WHERE id=?"
            ).use { row ->
                row.bindText(1, id.substringAfter('/'))
                if (row.step()) read(row, pack) else null
            }
        }
    }

    fun retrieve(
        packages: List<PackageEntity>,
        question: String,
        scope: Set<String>?
    ): List<Evidence> {
        if (scope?.isEmpty() == true) return emptyList()
        val terms = searchTerms(question)
        if (terms.isEmpty()) return emptyList()
        return packages.filter { it.kind == "library" }.flatMap { pack ->
            val ids = scope?.filter {
                it.startsWith(pack.key + "/")
            }?.map { it.substringAfter('/') }
            if (ids?.isEmpty() == true) return@flatMap emptyList()
            open(pack.path).use { db ->
                val filter = ids?.joinToString(",", " AND d.id IN (", ")") { "?" }.orEmpty()
                val schema = version(db)
                val current = schema >= 2L
                val query = if (current && hasNames(db)) {
                    RetrievalQuery.match(question, matchingTitles(db, question, ids))
                } else {
                    terms
                }
                val excerpt = when (schema) {
                    3L -> "substr(d.body,s.start+1,s.end-s.start)"
                    2L -> "p.body"
                    else -> "snippet(passages_fts,0,'','',' … ',64)"
                }
                val ranking = if (current) "rank" else "bm25(passages_fts)"
                val contentFilter = if (current) {
                    " AND (p.title!='' OR lower(trim(p.section)) NOT IN ('references','external links','see also','further reading','bibliography'))"
                } else {
                    ""
                }
                val spanJoin = if (schema == 3L) {
                    " JOIN passage_spans s ON s.passage_id=p.id"
                } else {
                    ""
                }
                db.prepare(
                    "SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,p.section,$excerpt FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid$spanJoin JOIN documents d ON d.id=p.doc_id WHERE passages_fts MATCH ?$filter$contentFilter ORDER BY $ranking,p.id LIMIT 48"
                ).use { statement ->
                    statement.bindText(1, query)
                    ids?.forEachIndexed { index, id -> statement.bindText(index + 2, id) }
                    buildList { while (statement.step()) add(read(statement, pack)) }
                        .withIndex().toList()
                }
            }
        }.sortedBy { it.index }.map { it.value }.let { candidates ->
            val perDocument = mutableMapOf<String, Int>()
            var characters = 0
            candidates.filter {
                val count = perDocument.getOrDefault(it.id, 0)
                val accepted = count < 2 && characters + it.text.length <= 6000
                if (accepted) {
                    perDocument[it.id] = count + 1
                    characters += it.text.length
                }
                accepted
            }.take(6)
        }
    }

    private fun hasNames(db: SQLiteConnection): Boolean = db.prepare(
        "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='document_names'"
    ).use { it.step() && it.getLong(0) == 1L }

    private fun validateSpanBackedPassages(db: SQLiteConnection) {
        db.prepare("PRAGMA table_info(passages)").use { columns ->
            val names = buildList {
                while (columns.step()) add(columns.getText(1))
            }
            check(names == listOf("id", "doc_id", "section", "title"))
        }
        db.prepare("SELECT sql FROM sqlite_master WHERE type='table' AND name='passages_fts'").use {
            check(it.step() && it.getText(0).replace(" ", "").contains("content=''"))
        }
        db.prepare(
            "SELECT count(*) FROM passages p LEFT JOIN documents d ON d.id=p.doc_id LEFT JOIN passage_spans s ON s.passage_id=p.id WHERE d.id IS NULL OR s.passage_id IS NULL OR typeof(p.title)!='text' OR (p.title!='' AND p.title!=d.title) OR typeof(p.section)!='text' OR length(p.section)>300 OR typeof(s.start)!='integer' OR typeof(s.end)!='integer' OR s.start<0 OR s.end<=s.start OR s.end>length(d.body) OR length(substr(d.body,s.start+1,s.end-s.start)) NOT BETWEEN 1 AND 1400 OR typeof(s.section_path)!='text' OR length(s.section_path) NOT BETWEEN 1 AND 4000 OR instr(s.section_path,char(0))>0"
        ).use { check(it.step() && it.getLong(0) == 0L) }
        db.prepare(
            "SELECT count(*) FROM passage_spans s LEFT JOIN passages p ON p.id=s.passage_id WHERE p.id IS NULL"
        ).use { check(it.step() && it.getLong(0) == 0L) }
        db.prepare(
            "SELECT count(*) FROM passages p LEFT JOIN passages_fts ON passages_fts.rowid=p.id WHERE passages_fts.rowid IS NULL"
        ).use { check(it.step() && it.getLong(0) == 0L) }
        db.prepare(
            "SELECT count(*) FROM passages_fts LEFT JOIN passages p ON p.id=passages_fts.rowid WHERE p.id IS NULL"
        ).use { check(it.step() && it.getLong(0) == 0L) }
    }

    private fun matchingTitles(
        db: SQLiteConnection,
        question: String,
        scope: List<String>?
    ): List<String> {
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
                        put(statement.getText(0), statement.getText(1))
                    }
                }
            }
        }
        val occupied = mutableSetOf<Int>()
        val titles = mutableSetOf<String>()
        for (candidate in candidates) {
            val title = matches[candidate.key] ?: continue
            if (candidate.words.any { it in occupied }) continue
            occupied.addAll(candidate.words)
            titles.add(title)
            if (titles.size == 4) break
        }
        return titles.toList()
    }

    private fun version(db: SQLiteConnection): Long = db.prepare("PRAGMA user_version").use {
        check(it.step())
        it.getLong(0)
    }

    private fun read(row: SQLiteStatement, pack: PackageEntity) = Evidence(
        "${pack.key}/${row.getText(0)}", row.getText(1), row.getText(2), row.getText(3),
        row.getText(4), row.getText(5), row.getText(6), row.getText(7), row.getText(8),
        "${pack.title} · ${pack.version} · ${pack.sha256}"
    )

    private fun open(path: String): SQLiteConnection =
        BundledSQLiteDriver().open(path, 1).also { db ->
            db.prepare("PRAGMA trusted_schema=OFF").use { it.step() }
            db.prepare("PRAGMA query_only=ON").use { it.step() }
        }

    companion object {
        fun searchTerms(question: String): String = RetrievalQuery.match(question)
    }
}
