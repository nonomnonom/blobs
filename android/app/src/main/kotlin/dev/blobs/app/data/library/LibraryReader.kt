package dev.blobs.app.data.library

import dev.blobs.app.data.local.PackageEntity
import java.util.PriorityQueue

/** Reads installed source records without applying retrieval ranking policy. */
internal class LibraryReader {
    fun browse(packages: List<PackageEntity>, query: String, offset: Int): List<Evidence> {
        require(offset in 0..Int.MAX_VALUE - 51)
        val limit = offset + 51
        val order = compareBy<Evidence>({ it.title }, { it.id })
        val candidates = PriorityQueue(51, order.reversed())
        val blankQuery = query.isBlank()
        val search = "%${query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        packages.filter { it.kind == "library" && it.enabled }.forEach { pack ->
            LibrarySqlite.open(pack.path).use { db ->
                db.prepare(
                    if (blankQuery) {
                        "SELECT id,title,publisher,url,license,updated,saved,collection," +
                            "substr(body,1,300) FROM documents ORDER BY title,id LIMIT ?"
                    } else {
                        "SELECT id,title,publisher,url,license,updated,saved,collection," +
                            "substr(body,1,300) FROM documents WHERE title LIKE ? ESCAPE '\\' " +
                            "OR publisher LIKE ? ESCAPE '\\' ORDER BY title,id LIMIT ?"
                    }
                ).use { statement ->
                    if (blankQuery) {
                        statement.bindLong(1, limit.toLong())
                    } else {
                        statement.bindText(1, search)
                        statement.bindText(2, search)
                        statement.bindLong(3, limit.toLong())
                    }
                    while (statement.step()) {
                        val item = LibrarySqlite.readEvidence(statement, pack)
                        if (candidates.size < limit) {
                            candidates.add(item)
                        } else if (order.compare(item, candidates.peek()) < 0) {
                            candidates.poll()
                            candidates.add(item)
                        }
                    }
                }
            }
        }
        return candidates.sortedWith(order).drop(offset)
    }

    fun readDocument(packages: List<PackageEntity>, id: String): Evidence? {
        val pack = packages.singleOrNull {
            it.kind == "library" && id.startsWith("${it.key}/")
        } ?: return null
        return LibrarySqlite.open(pack.path).use { db ->
            db.prepare(
                "SELECT id,title,publisher,url,license,updated,saved,collection,body FROM documents WHERE id=?"
            ).use { row ->
                row.bindText(1, id.substringAfter('/'))
                if (row.step()) LibrarySqlite.readEvidence(row, pack) else null
            }
        }
    }

    fun readPassage(packages: List<PackageEntity>, documentId: String, passageId: Long): Evidence? {
        if (passageId <= 0L) return null
        val pack = packages.singleOrNull {
            it.kind == "library" && documentId.startsWith("${it.key}/")
        } ?: return null
        val localDocumentId = documentId.substringAfter('/')
        return LibrarySqlite.open(pack.path).use { db ->
            val schema = LibrarySqlite.version(db)
            val spanJoin = if (schema >= 3L) " JOIN passage_spans s ON s.passage_id=p.id" else ""
            val text = if (schema >= 3L) {
                "substr(d.body,s.start+1,s.end-s.start)"
            } else {
                "p.body"
            }
            db.prepare(
                "SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved," +
                    "p.section,$text,p.id " +
                    "FROM passages p$spanJoin JOIN documents d ON d.id=p.doc_id " +
                    "WHERE d.id=? AND p.id=? LIMIT 1"
            ).use { row ->
                row.bindText(1, localDocumentId)
                row.bindLong(2, passageId)
                if (row.step()) LibrarySqlite.readEvidence(row, pack, passageId) else null
            }
        }
    }

    fun forEachDocument(pack: PackageEntity, consume: (LibraryDocument) -> Unit) {
        require(pack.kind == "library")
        LibrarySqlite.open(pack.path).use { db ->
            db.prepare(
                "SELECT id,title,publisher,url,license,updated,saved,body,collection FROM documents ORDER BY id"
            ).use { statement ->
                while (statement.step()) {
                    consume(
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
