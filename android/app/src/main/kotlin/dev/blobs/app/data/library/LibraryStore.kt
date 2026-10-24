package dev.blobs.app.data.library

import dev.blobs.app.data.local.PackageEntity

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
    private val validator = LibraryValidator()
    private val reader = LibraryReader()
    private val nameLookup = DocumentNameLookup()

    private data class RetrievalCandidate(
        val evidence: Evidence,
        val passageId: Long,
        val titlePriority: Boolean,
        val directTitleMatch: Boolean,
        val subject: String?,
        val titleOverlap: Int,
        val topicOverlap: Int,
        val evidenceOverlap: Int,
        val order: Int
    )

    fun validate(
        file: java.io.File,
        catalogDigestPinned: Boolean = false,
        recordsSha256: String = "",
        expectedRecordCount: Long? = null
    ) = validator.validate(file, catalogDigestPinned, recordsSha256, expectedRecordCount)

    fun browse(packages: List<PackageEntity>, query: String, offset: Int = 0): List<Evidence> =
        reader.browse(packages, query, offset)

    fun readDocument(packages: List<PackageEntity>, id: String): Evidence? =
        reader.readDocument(packages, id)

    fun readPassage(packages: List<PackageEntity>, documentId: String, passageId: Long): Evidence? =
        reader.readPassage(packages, documentId, passageId)

    fun forEachDocument(pack: PackageEntity, consume: (LibraryDocument) -> Unit) =
        reader.forEachDocument(pack, consume)

    /**
     * Resolves reviewed names, runs one bounded FTS query for each selected
     * library, then applies deterministic local ranking and source limits.
     */
    fun retrieve(
        packages: List<PackageEntity>,
        question: String,
        scope: Set<String>?,
        maximumResults: Int = MAX_RETRIEVAL_RESULTS
    ): List<Evidence> {
        require(maximumResults in 1..MAX_RETRIEVAL_RESULTS)
        if (scope?.isEmpty() == true) return emptyList()

        val searchQuestion = RetrievalQuery.forSearch(question)
        val lexicalQuery = RetrievalQuery.match(searchQuestion)
        if (lexicalQuery.isEmpty()) return emptyList()

        val titleTerms = RetrievalQuery.titleTerms(searchQuestion)
        val enabledLibraries = packages.filter { it.kind == "library" && it.enabled }
        val candidates = enabledLibraries.flatMap { pack ->
            val selectedWholeLibrary = pack.key in scope.orEmpty()
            val documentIds = when {
                scope == null || selectedWholeLibrary -> null

                else -> scope.filter { it.startsWith("${pack.key}/") }
                    .map { it.substringAfter('/') }
            }
            if (scope != null && !selectedWholeLibrary && documentIds.isNullOrEmpty()) {
                emptyList()
            } else {
                retrieveFromLibrary(
                    pack,
                    searchQuestion,
                    lexicalQuery,
                    titleTerms,
                    documentIds,
                    maximumResults
                )
            }
        }
        return selectEvidence(candidates, titleTerms, maximumResults)
    }

    private fun retrieveFromLibrary(
        pack: PackageEntity,
        searchQuestion: String,
        lexicalQuery: String,
        titleTerms: Set<String>,
        documentIds: List<String>?,
        maximumResults: Int
    ): List<RetrievalCandidate> = LibrarySqlite.open(pack.path).use { db ->
        val schema = LibrarySqlite.version(db)
        val currentSchema = schema >= 2L
        val titleMatches = if (currentSchema && LibrarySqlite.hasDocumentNames(db)) {
            nameLookup.matchingTitles(db, searchQuestion, documentIds)
        } else {
            emptyList()
        }
        val directTitleIds = titleMatches.filter {
            RetrievalQuery.isDirectTitleMatch(searchQuestion, it.key) ||
                RetrievalQuery.isDirectTitleMatch(searchQuestion, it.title)
        }.mapTo(mutableSetOf()) { it.id }
        val priorityTitleIds = titleMatches.filter {
            it.id in directTitleIds && RetrievalQuery.isBroadTitleQuestion(searchQuestion, it.title)
        }.mapTo(mutableSetOf()) { it.id }
        val subjectTitles = titleMatches.filter { it.id in directTitleIds }.map { it.title }
        val topicTerms = RetrievalQuery.topicTerms(searchQuestion, subjectTitles)
        val query = if (titleMatches.isEmpty()) {
            lexicalQuery
        } else {
            RetrievalQuery.match(searchQuestion, titleMatches.map { it.title })
        }
        val excerpt = when (schema) {
            3L -> "substr(d.body,s.start+1,s.end-s.start)"
            2L -> "p.body"
            else -> "snippet(passages_fts,0,'','',' … ',64)"
        }
        val spanJoin = if (schema == 3L) " JOIN passage_spans s ON s.passage_id=p.id" else ""
        val documentFilter = documentIds?.joinToString(",", " AND d.id IN (", ")") { "?" }
            .orEmpty()
        val contentFilter = if (currentSchema) {
            " AND (p.title!='' OR lower(trim(p.section)) NOT IN " +
                "('references','external links','see also','further reading','bibliography'))"
        } else {
            ""
        }
        val scoped = documentIds != null
        val from = if (scoped) {
            "documents d CROSS JOIN passages p ON p.doc_id=d.id$spanJoin " +
                "CROSS JOIN passages_fts ON passages_fts.rowid=p.id"
        } else {
            "passages_fts JOIN passages p ON p.id=passages_fts.rowid$spanJoin " +
                "JOIN documents d ON d.id=p.doc_id"
        }
        val ranking = if (currentSchema) "rank" else "bm25(passages_fts)"
        val order = if (scoped && currentSchema) "+rank, p.id" else ranking
        val titleFlag = if (currentSchema) "CASE WHEN p.title!='' THEN 1 ELSE 0 END" else "0"
        val limit = (maximumResults * CANDIDATES_PER_RESULT).toLong()

        db.prepare(
            "SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,p.section," +
                "$excerpt,$titleFlag,p.id FROM $from WHERE passages_fts MATCH ?$documentFilter" +
                "$contentFilter ORDER BY $order LIMIT ?"
        ).use { statement ->
            statement.bindText(1, query)
            documentIds?.forEachIndexed { index, id -> statement.bindText(index + 2, id) }
            statement.bindLong((documentIds?.size ?: 0) + 2, limit)
            buildList {
                var orderIndex = 0
                while (statement.step()) {
                    val localId = statement.getText(0)
                    val title = statement.getText(1)
                    val evidence = LibrarySqlite.readEvidence(statement, pack)
                    val subject = subjectTitles.firstOrNull {
                        title == it || title.startsWith("$it/") || title.endsWith(" $it")
                    }
                    add(
                        RetrievalCandidate(
                            evidence = evidence,
                            passageId = statement.getLong(10),
                            titlePriority =
                                statement.getLong(9) != 0L && localId in priorityTitleIds,
                            directTitleMatch = localId in directTitleIds,
                            subject = subject,
                            titleOverlap = RetrievalQuery.titleOverlap(titleTerms, title),
                            topicOverlap = RetrievalQuery.evidenceOverlap(
                                topicTerms,
                                title,
                                evidence.section,
                                evidence.text
                            ),
                            evidenceOverlap = RetrievalQuery.evidenceOverlap(
                                titleTerms,
                                title,
                                evidence.section,
                                evidence.text
                            ),
                            order = orderIndex++
                        )
                    )
                }
            }
        }
    }

    private fun selectEvidence(
        candidates: List<RetrievalCandidate>,
        titleTerms: Set<String>,
        maximumResults: Int
    ): List<Evidence> {
        val ranked = candidates.sortedWith(
            compareByDescending<RetrievalCandidate> { it.titlePriority }
                .thenByDescending { if (it.subject != null) 1 else 0 }
                .thenByDescending { it.topicOverlap }
                .thenByDescending { it.evidenceOverlap }
                .thenByDescending { it.titleOverlap }
                .thenByDescending { it.directTitleMatch }
                .thenBy { it.order }
        )
        val directSubjects = ranked.filter { it.subject != null }
        val broadTitles = ranked.filter { it.titlePriority }.map { it.evidence.title }.distinct()
        val focused = when {
            directSubjects.isNotEmpty() -> directSubjects

            broadTitles.isEmpty() -> ranked

            else -> ranked.filter { candidate ->
                broadTitles.any {
                    candidate.evidence.title == it || candidate.evidence.title.startsWith("$it/")
                }
            }
        }
        val minimumOverlap = RetrievalQuery.minimumEvidenceOverlap(titleTerms)
        val relevant = focused.filter { candidate ->
            candidate.titlePriority || candidate.directTitleMatch || candidate.subject != null ||
                minimumOverlap == null ||
                candidate.evidenceOverlap >= minimumOverlap
        }

        fun documentKey(evidence: Evidence) = Triple(
            evidence.url,
            evidence.id.substringAfter('/'),
            evidence.title
        )
        val unique = relevant.distinctBy {
            Triple(documentKey(it.evidence), it.evidence.section, it.evidence.text)
        }
        val selected = mutableListOf<Evidence>()
        val selectedPassages = mutableSetOf<Pair<String, Long>>()
        val perDocument = mutableMapOf<Triple<String, String, String>, Int>()
        var characters = 0
        fun append(candidate: RetrievalCandidate) {
            val evidence = candidate.evidence
            val document = documentKey(evidence)
            val evidenceCost = minOf(evidence.text.length, MAX_EVIDENCE_CHARACTERS_PER_SOURCE)
            if (
                selected.size < maximumResults &&
                perDocument.getOrDefault(document, 0) < MAX_PASSAGES_PER_DOCUMENT &&
                characters + evidenceCost <= MAX_EVIDENCE_CHARACTERS &&
                selectedPassages.add(evidence.id to candidate.passageId)
            ) {
                selected += evidence.copy(passageId = candidate.passageId)
                perDocument[document] = perDocument.getOrDefault(document, 0) + 1
                characters += evidenceCost
            }
        }

        val subjectCandidates = unique.filter { it.subject != null }
        subjectCandidates.distinctBy { it.subject }.forEach(::append)
        // Each named subject keeps a slot, and the most topically relevant article among them
        // then gets room for its own sections instead of only its leading passage.
        subjectCandidates.groupBy { documentKey(it.evidence) }
            .values
            .sortedByDescending { group -> group.maxOf { it.topicOverlap } }
            .forEach { group -> group.take(MAX_PASSAGES_PER_DOCUMENT).forEach(::append) }
        unique.distinctBy { documentKey(it.evidence) }.forEach(::append)
        unique.forEach(::append)
        return selected
    }

    companion object {
        private const val MAX_RETRIEVAL_RESULTS = 6
        private const val CANDIDATES_PER_RESULT = 4
        private const val MAX_PASSAGES_PER_DOCUMENT = 3
        private const val MAX_EVIDENCE_CHARACTERS = 6000
        private const val MAX_EVIDENCE_CHARACTERS_PER_SOURCE = 2000

        internal val multiLibraryCandidateLimit: Int
            get() = MAX_RETRIEVAL_RESULTS * CANDIDATES_PER_RESULT

        fun searchTerms(question: String): String =
            RetrievalQuery.match(RetrievalQuery.forSearch(question))
    }
}
