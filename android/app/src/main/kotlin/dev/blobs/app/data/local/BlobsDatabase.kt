package dev.blobs.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [ConversationEntity::class, TurnEntity::class, PackageEntity::class],
    version = 3,
    exportSchema = true
)
abstract class BlobsDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao

    abstract fun packages(): PackageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                listOf(
                    "CREATE TABLE IF NOT EXISTS turns (id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, question TEXT NOT NULL, answer TEXT NOT NULL, status TEXT NOT NULL, model TEXT NOT NULL, scope TEXT NOT NULL, evidence TEXT NOT NULL, createdAt INTEGER NOT NULL, elapsedMs INTEGER NOT NULL, error TEXT NOT NULL, FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                    "CREATE INDEX IF NOT EXISTS index_turns_conversationId ON turns(conversationId)",
                    "CREATE TABLE IF NOT EXISTS packages (`key` TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, version TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, path TEXT NOT NULL, bytes INTEGER NOT NULL, sha256 TEXT NOT NULL, license TEXT NOT NULL, origin TEXT NOT NULL, installedAt INTEGER NOT NULL)"
                ).forEach { sql -> connection.prepare(sql).use { it.step() } }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.prepare(
                    "ALTER TABLE packages ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1"
                ).use { it.step() }
            }
        }

        fun open(context: Context, name: String = "blobs.db"): BlobsDatabase = Room
            .databaseBuilder(context.applicationContext, BlobsDatabase::class.java, name)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .build()
    }
}
