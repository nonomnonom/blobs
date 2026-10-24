package dev.blobs.app.data.library

import androidx.sqlite.SQLiteConnection
import java.io.File
import org.json.JSONObject

/** Validates an installed library before PackageInstaller activates it. */
internal class LibraryValidator {
    fun validate(
        file: File,
        catalogDigestPinned: Boolean,
        recordsSha256: String = "",
        expectedRecordCount: Long? = null
    ) {
        LibrarySqlite.open(file.path).use { db ->
            if (!catalogDigestPinned) {
                db.prepare("PRAGMA quick_check").use { check(it.step() && it.getText(0) == "ok") }
            }
            val version = LibrarySqlite.version(db)
            check(version in 1L..3L)
            if (recordsSha256.isNotEmpty() || expectedRecordCount != null) {
                validateBuildMetadata(db, recordsSha256, expectedRecordCount, version)
            }
            db.prepare("SELECT count(*) FROM sqlite_master WHERE type IN ('view','trigger')").use {
                check(it.step() && it.getLong(0) == 0L)
            }
            db.prepare(
                "SELECT id,title,publisher,url,license,updated,saved,body,collection FROM documents LIMIT 0"
            ).use { }
            db.prepare("SELECT doc_id,body_sha256,details FROM document_provenance LIMIT 0").use { }
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
                "typeof($column)!='text' OR length($column)>$limit OR " +
                    "instr($column,char(0))>0 OR instr($column,char(65533))>0"
            }
            if (version == 3L) {
                validateSpanBackedPassages(db, invalid, validateContents = !catalogDigestPinned)
            } else if (!catalogDigestPinned) {
                validateDocuments(db, invalid)
            }
            if (!catalogDigestPinned) validateDocumentProvenance(db)
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
            if (version >= 2L && LibrarySqlite.hasDocumentNames(db)) {
                db.prepare("SELECT name,doc_id FROM document_names LIMIT 0").use { }
                if (!catalogDigestPinned) {
                    db.prepare(
                        "SELECT count(*),sum(CASE WHEN d.id IS NULL OR typeof(n.name)!='text' OR length(n.name) NOT BETWEEN 1 AND 300 OR instr(n.name,char(0))>0 OR instr(n.name,char(65533))>0 THEN 1 ELSE 0 END) FROM document_names n LEFT JOIN documents d ON d.id=n.doc_id"
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
                    "SELECT count(*) FROM passages p LEFT JOIN documents d ON d.id=p.doc_id " +
                        "WHERE d.id IS NULL OR typeof(p.body)!='text' OR " +
                        "length(p.body) NOT BETWEEN 1 AND 8000 OR instr(p.body,char(0))>0 " +
                        "OR instr(p.body,char(65533))>0 OR typeof(p.section)!='text' OR " +
                        "length(p.section)>300 OR instr(p.section,char(0))>0 OR " +
                        "instr(p.section,char(65533))>0"
                ).use { check(it.step() && it.getLong(0) == 0L) }
            }
        }
    }

    private fun validateBuildMetadata(
        db: SQLiteConnection,
        expectedRecordsSha256: String,
        expectedRecordCount: Long?,
        schema: Long
    ) {
        db.prepare("SELECT value FROM build_metadata WHERE key='build'").use { metadata ->
            check(metadata.step())
            val build = JSONObject(metadata.getText(0))
            check(build.getInt("format") == 1)
            check(build.getLong("schema") == schema)
            if (expectedRecordsSha256.isNotEmpty()) {
                check(build.getString("records_sha256") == expectedRecordsSha256)
            }
            if (expectedRecordCount != null) {
                check(build.getLong("documents") == expectedRecordCount)
            }
        }
    }

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

    private fun validateDocumentProvenance(db: SQLiteConnection) {
        db.prepare(
            """
            SELECT count(*),count(DISTINCT p.doc_id),(SELECT count(*) FROM documents),sum(CASE
                WHEN d.id IS NULL
                  OR typeof(p.body_sha256)!='text'
                  OR length(p.body_sha256)!=64
                  OR p.body_sha256 GLOB '*[^0-9a-f]*'
                  OR typeof(p.details)!='text'
                  OR length(p.details) NOT BETWEEN 2 AND 20000
                  OR instr(p.details,char(0))>0
                  OR instr(p.details,char(65533))>0
                THEN 1 ELSE 0 END)
            FROM document_provenance p
            LEFT JOIN documents d ON d.id=p.doc_id
            """.trimIndent()
        ).use {
            check(
                it.step() &&
                    it.getLong(0) in 1L..5_000_000L &&
                    it.getLong(0) == it.getLong(1) &&
                    it.getLong(0) == it.getLong(2) &&
                    it.getLong(3) == 0L
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
            "SELECT (SELECT count(*) FROM passages),(SELECT count(DISTINCT id) FROM passages)," +
                "(SELECT count(*) FROM passage_spans)," +
                "(SELECT count(DISTINCT passage_id) FROM passage_spans)"
        ).use {
            check(
                it.step() &&
                    it.getLong(0) > 0L &&
                    it.getLong(0) == it.getLong(1) &&
                    it.getLong(0) == it.getLong(2) &&
                    it.getLong(0) == it.getLong(3)
            )
        }
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
                      OR instr(p.section,char(0))>0 OR instr(p.section,char(65533))>0
                      OR typeof(s.start)!='integer' OR typeof(s.end)!='integer'
                      OR s.start<0 OR s.end<=s.start OR s.end>d.body_length
                      OR s.end-s.start NOT BETWEEN 1 AND 1400
                      OR typeof(s.section_path)!='text'
                      OR length(s.section_path) NOT BETWEEN 1 AND 4000
                      OR instr(s.section_path,char(0))>0
                      OR instr(s.section_path,char(65533))>0
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
}
