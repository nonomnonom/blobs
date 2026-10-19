package dev.blobs.app.data.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.blobs.app.data.local.PackageEntity
import java.io.File

data class LibraryDocument(
    val id: String,
    val title: String,
    val publisher: String,
    val url: String,
    val license: String,
    val updated: String,
    val saved: String,
    val body: String,
    val collection: String
)

class LibraryStore {
    private data class NameMatch(val key: String, val title: String, val id: String)
    private data class RetrievalCandidate(
        val evidence: Evidence,
        val passageId: Long,
        val titlePriority: Boolean,
        val titleOverlap: Int,
        val directTitleMatch: Boolean
    )

    fun validate(file: File, catalogDigestPinned: Boolean = false) {
        open(file.path).use { db ->
            if (!catalogDigestPinned) {
                db.prepare("PRAGMA quick_check").use { check(it.step() && it.getText(0) == "ok") }
            }
            val version = version(db)
            check(version in 1L..3L)
            db.prepare("SELECT count(*) FROM sqlite_master WHERE type IN ('view','trigger')").use {
                check(it.step() && it.getLong(0) == 0L)
            }
            db.prepare(
                "SELECT id,title,publisher,url,license,updated,saved,body,collection FROM documents LIMIT 0"
            ).use { }
            val passageColumns = if (version == 3L) {
                "SELECT id,doc_id,section,title FROM passages LIMIT 0"
            } else {
                "SELECT id,doc_id,body,section FROM passages LIMIT 0"
            }
            db.prepare(passageColumns).use { }
            db.prepare("SELECT 1 FROM documents LIMIT 1").use { check(it.step()) }
            db.prepare("SELECT 1 FROM passages LIMIT 1").use { check(it.step()) }
            db.prepare("SELECT rowid FROM passages_fts LIMIT 1").use { check(it.step()) }
            val limits = mapOf(
                "id" to 100, "title" to 300, "publisher" to 300, "url" to 2000,
                "license" to 4000, "updated" to 80, "saved" to 80, "collection" to 200,
                "body" to 1_000_000
            )
            val invalid = limits.entries.joinToString(" OR ") { (column, limit) ->
                "typeof($column)!='text' OR length($column)>$limit"
            }
            if (version == 3L) {
                validateSpanBackedPassages(db, invalid, validateContents = !catalogDigestPinned)
            } else if (!catalogDigestPinned) {
                validateDocuments(db, invalid)
            }
            if (version == 2L) {
                db.prepare("SELECT title,section FROM passages_fts LIMIT 1").use {
                    check(it.step())
                }
            }
            if (version == 2L && !catalogDigestPinned) {
                db.prepare(
                    "SELECT count(*) FROM passages p JOIN documents d ON d.id=p.doc_id WHERE typeof(p.title)!='text' OR (p.title!='' AND p.title!=d.title) OR length(p.body)>1400"
                ).use { check(it.step() && it.getLong(0) == 0L) }
            }
            if (version >= 2L && hasNames(db)) {
                db.prepare("SELECT name,doc_id FROM document_names LIMIT 0").use { }
                if (!catalogDigestPinned) {
                    db.prepare(
                        "SELECT count(*),sum(CASE WHEN d.id IS NULL OR typeof(n.name)!='text' OR length(n.name) NOT BETWEEN 1 AND 300 OR instr(n.name,char(0))>0 THEN 1 ELSE 0 END) FROM document_names n LEFT JOIN documents d ON d.id=n.doc_id"
                    ).use {
                        check(
                            it.step() && it.getLong(0) <= 10_000_000L && it.getLong(1) == 0L
                        )
                    }
                    db.prepare(
                        "SELECT count(*) FROM (SELECT name,doc_id FROM document_names GROUP BY name,doc_id HAVING count(*)>1)"
                    ).use { check(it.step() && it.getLong(0) == 0L) }
                }
            }
            if (version < 3L && !catalogDigestPinned) {
                db.prepare(
                    "SELECT count(*) FROM passages p LEFT JOIN documents d ON d.id=p.doc_id WHERE d.id IS NULL OR typeof(p.body)!='text' OR length(p.body) NOT BETWEEN 1 AND 8000 OR typeof(p.section)!='text' OR length(p.section)>300"
                ).use { check(it.step() && it.getLong(0) == 0L) }
            }
        }
    }

    fun browse(packages: List<PackageEntity>, query: String, offset: Int = 0): List<Evidence> =
        packages.filter { it.kind == "library" && it.enabled }.flatMap { pack ->
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

    fun documentsForExport(pack: PackageEntity): List<LibraryDocument> {
        require(pack.kind == "library")
        return open(pack.path).use { db ->
            db.prepare(
                "SELECT id,title,publisher,url,license,updated,saved,body,collection FROM documents ORDER BY id"
            ).use { statement ->
                buildList {
                    while (statement.step()) {
                        add(
                            LibraryDocument(
                                statement.getText(0), statement.getText(1), statement.getText(2),
                                statement.getText(3), statement.getText(4), statement.getText(5),
                                statement.getText(6), statement.getText(7), statement.getText(8)
                            )
                        )
                    }
                }
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
        val titleTerms = RetrievalQuery.titleTerms(question)
        val enabledLibraries = packages.filter { it.kind == "library" && it.enabled }
        return enabledLibraries.flatMap { pack ->
            val ids = scope?.filter {
                it.startsWith(pack.key + "/")
            }?.map { it.substringAfter('/') }
            if (ids?.isEmpty() == true) return@flatMap emptyList()
            open(pack.path).use { db ->
                val filter = ids?.joinToString(",", " AND d.id IN (", ")") { "?" }.orEmpty()
                val schema = version(db)
                val current = schema >= 2L
                val titleMatches = if (current && hasNames(db)) {
                    matchingTitles(db, question, ids)
                } else {
                    emptyList()
                }
                val query = if (current && hasNames(db)) {
                    RetrievalQuery.match(question, titleMatches.map { it.title })
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
                val directTitleIds = titleMatches.filter {
                    it.key == RetrievalQuery.nameKey(it.title)
                }.map { it.id }.distinct()
                val titlePriorityIds = titleMatches.filter {
                    it.id in directTitleIds &&
                        RetrievalQuery.isBroadTitleQuestion(question, it.title)
                }.map { it.id }.toSet()
                val titleFlag = if (current) "CASE WHEN p.title!='' THEN 1 ELSE 0 END" else "0"
                val candidateLimit = if (enabledLibraries.size > 1) {
                    MULTI_LIBRARY_CANDIDATE_LIMIT
                } else {
                    SINGLE_LIBRARY_CANDIDATE_LIMIT
                }
                val ranked = db.prepare(
                    "SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,p.section,$excerpt,$titleFlag,p.id FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid$spanJoin JOIN documents d ON d.id=p.doc_id WHERE passages_fts MATCH ?$filter$contentFilter ORDER BY $ranking LIMIT ?"
                ).use { statement ->
                    statement.bindText(1, query)
                    var parameter = 2
                    ids?.forEach { id -> statement.bindText(parameter++, id) }
                    statement.bindLong(parameter, candidateLimit.toLong())
                    buildList {
                        while (statement.step()) {
                            add(
                                RetrievalCandidate(
                                    evidence = read(statement, pack),
                                    passageId = statement.getLong(10),
                                    titlePriority = statement.getLong(9) != 0L &&
                                        statement.getText(0) in titlePriorityIds,
                                    titleOverlap = 0,
                                    directTitleMatch = statement.getText(0) in directTitleIds
                                )
                            )
                        }
                    }
                }
                val routed = if (current && directTitleIds.isNotEmpty()) {
                    val routeFilter = directTitleIds.joinToString(",", "d.id IN (", ")") { "?" }
                    val routeExcerpt = if (schema == 3L) {
                        "substr(d.body,s.start+1,s.end-s.start)"
                    } else {
                        "p.body"
                    }
                    db.prepare(
                        "SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,p.section,$routeExcerpt,p.id FROM passages p$spanJoin JOIN documents d ON d.id=p.doc_id WHERE $routeFilter AND p.title!='' ORDER BY p.id LIMIT 8"
                    ).use { statement ->
                        directTitleIds.forEachIndexed { index, id ->
                            statement.bindText(index + 1, id)
                        }
                        buildList {
                            while (statement.step()) {
                                val id = statement.getText(0)
                                add(
                                    RetrievalCandidate(
                                        evidence = read(statement, pack),
                                        passageId = statement.getLong(9),
                                        titlePriority = id in titlePriorityIds,
                                        titleOverlap = 0,
                                        directTitleMatch = id in directTitleIds
                                    )
                                )
                            }
                        }
                    }
                } else {
                    emptyList()
                }
                val overlapByTitle = mutableMapOf<String, Int>()
                val ordered = buildList {
                    addAll(routed.filter { it.titlePriority })
                    ranked.firstOrNull()?.let(::add)
                    addAll(routed.filterNot { it.titlePriority })
                    addAll(ranked.drop(1))
                }.distinctBy { it.passageId }
                    .map { candidate ->
                        candidate.copy(
                            titleOverlap = overlapByTitle.getOrPut(candidate.evidence.title) {
                                RetrievalQuery.titleOverlap(titleTerms, candidate.evidence.title)
                            }
                        )
                    }
                    .withIndex()
                    .sortedWith(
                        compareByDescending<IndexedValue<RetrievalCandidate>> {
                            it.value.titleOverlap
                        }.thenByDescending {
                            it.value.directTitleMatch
                        }.thenBy {
                            it.index
                        }
                    )
                    .map { it.value }
                ordered.withIndex().toList()
            }
        }.let { matched ->
            val ranked = if (enabledLibraries.size > 1) {
                matched.sortedWith(
                    compareByDescending<IndexedValue<RetrievalCandidate>> {
                        it.value.titlePriority
                    }.thenByDescending {
                        it.value.directTitleMatch
                    }.thenByDescending {
                        val evidence = it.value.evidence
                        RetrievalQuery.evidenceOverlap(
                            titleTerms,
                            evidence.title,
                            evidence.section,
                            evidence.text
                        )
                    }.thenBy {
                        it.index
                    }
                )
            } else {
                matched.sortedBy { it.index }
            }
            ranked.map { it.value }
        }.let { candidates ->
            val diverse = candidates.distinctBy { it.evidence.id }
            val selected = mutableListOf<Evidence>()
            val selectedPassages = mutableSetOf<Pair<String, Long>>()
            val perDocument = mutableMapOf<String, Int>()
            var characters = 0
            fun append(candidate: RetrievalCandidate) {
                val evidence = candidate.evidence
                val passageKey = evidence.id to candidate.passageId
                if (
                    selected.size < 6 &&
                    perDocument.getOrDefault(evidence.id, 0) < 2 &&
                    characters + evidence.text.length <= 6000 &&
                    selectedPassages.add(passageKey)
                ) {
                    selected += evidence
                    perDocument[evidence.id] = perDocument.getOrDefault(evidence.id, 0) + 1
                    characters += evidence.text.length
                }
            }
            diverse.forEach(::append)
            candidates.forEach(::append)
            selected
        }
    }

    private fun hasNames(db: SQLiteConnection): Boolean = db.prepare(
        "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='document_names'"
    ).use { it.step() && it.getLong(0) == 1L }

    private fun validateDocuments(db: SQLiteConnection, invalid: String) {
        db.prepare(
            "SELECT count(*),count(DISTINCT id),sum(CASE WHEN $invalid OR length(id)=0 OR id GLOB '*[^a-zA-Z0-9_-]*' OR instr(body,char(0))>0 THEN 1 ELSE 0 END) FROM documents"
        ).use {
            check(
                it.step() &&
                    it.getLong(0) in 1L..5_000_000L &&
                    it.getLong(0) == it.getLong(1) &&
                    it.getLong(2) == 0L
            )
        }
    }

    private fun validateSpanBackedPassages(
        db: SQLiteConnection,
        invalid: String,
        validateContents: Boolean
    ) {
        db.prepare("PRAGMA table_info(passages)").use { columns ->
            val names = buildList {
                while (columns.step()) add(columns.getText(1))
            }
            check(names == listOf("id", "doc_id", "section", "title"))
        }
        db.prepare("SELECT sql FROM sqlite_master WHERE type='table' AND name='passages_fts'").use {
            check(it.step() && it.getText(0).replace(" ", "").contains("content=''"))
        }
        db.prepare("SELECT passage_id,start,end,section_path FROM passage_spans LIMIT 0").use { }
        if (!validateContents) return
        db.prepare(
            """
            WITH docs AS MATERIALIZED (
                SELECT id,title,length(body) AS body_length,
                       CASE WHEN $invalid OR length(id)=0 OR id GLOB '*[^a-zA-Z0-9_-]*'
                                 OR instr(body,char(0))>0
                            THEN 1 ELSE 0 END AS invalid
                FROM documents
            ),
            document_status AS (
                SELECT count(*),count(DISTINCT id),sum(invalid) FROM docs
            ),
            passage_status AS (
                SELECT count(*),sum(CASE
                    WHEN d.id IS NULL OR s.passage_id IS NULL OR f.rowid IS NULL
                      OR typeof(p.title)!='text' OR (p.title!='' AND p.title!=d.title)
                      OR typeof(p.section)!='text' OR length(p.section)>300
                      OR typeof(s.start)!='integer' OR typeof(s.end)!='integer'
                      OR s.start<0 OR s.end<=s.start OR s.end>d.body_length
                      OR s.end-s.start NOT BETWEEN 1 AND 1400
                      OR typeof(s.section_path)!='text'
                      OR length(s.section_path) NOT BETWEEN 1 AND 4000
                      OR instr(s.section_path,char(0))>0
                    THEN 1 ELSE 0 END)
                FROM passages p
                LEFT JOIN docs d ON d.id=p.doc_id
                LEFT JOIN passage_spans s ON s.passage_id=p.id
                LEFT JOIN passages_fts f ON f.rowid=p.id
            )
            SELECT document_status.*,passage_status.*
            FROM document_status,passage_status
            """.trimIndent()
        ).use {
            check(
                it.step() &&
                    it.getLong(0) in 1L..5_000_000L &&
                    it.getLong(0) == it.getLong(1) &&
                    it.getLong(2) == 0L &&
                    it.getLong(3) > 0L &&
                    it.getLong(4) == 0L
            )
        }
        db.prepare(
            "SELECT count(*) FROM passage_spans s LEFT JOIN passages p ON p.id=s.passage_id WHERE p.id IS NULL"
        ).use { check(it.step() && it.getLong(0) == 0L) }
        db.prepare(
            "SELECT count(*) FROM passages_fts LEFT JOIN passages p ON p.id=passages_fts.rowid WHERE p.id IS NULL"
        ).use { check(it.step() && it.getLong(0) == 0L) }
    }

    private fun matchingTitles(
        db: SQLiteConnection,
        question: String,
        scope: List<String>?
    ): List<NameMatch> {
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
                            NameMatch(
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
        val titles = linkedMapOf<String, NameMatch>()
        for (candidate in candidates) {
            val match = matches[candidate.key] ?: continue
            if (candidate.words.any { it in occupied }) continue
            occupied.addAll(candidate.words)
            titles.putIfAbsent(match.id, match)
            if (titles.size == 4) break
        }
        return titles.values.toList()
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
        private const val MULTI_LIBRARY_CANDIDATE_LIMIT = 12
        private const val SINGLE_LIBRARY_CANDIDATE_LIMIT = 48

        fun searchTerms(question: String): String = RetrievalQuery.match(question)
    }
}
