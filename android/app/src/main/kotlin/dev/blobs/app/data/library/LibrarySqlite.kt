package dev.blobs.app.data.library

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.blobs.app.data.local.PackageEntity

/** Read-only SQLite boundary for installed library packages. */
internal object LibrarySqlite {
    fun open(path: String): SQLiteConnection = BundledSQLiteDriver().open(path, 1).also { db ->
        db.prepare("PRAGMA trusted_schema=OFF").use { it.step() }
        db.prepare("PRAGMA query_only=ON").use { it.step() }
    }

    fun version(db: SQLiteConnection): Long = db.prepare("PRAGMA user_version").use {
        check(it.step())
        it.getLong(0)
    }

    fun hasDocumentNames(db: SQLiteConnection): Boolean = db.prepare(
        "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='document_names'"
    ).use { it.step() && it.getLong(0) == 1L }

    fun readEvidence(row: SQLiteStatement, pack: PackageEntity, passageId: Long? = null) = Evidence(
        "${pack.key}/${row.getText(0)}", row.getText(1), row.getText(2), row.getText(3),
        row.getText(4), row.getText(5), row.getText(6), row.getText(7), row.getText(8),
        "${pack.title} · ${pack.version} · ${pack.sha256}", passageId
    )
}
