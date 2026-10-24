package dev.blobs.app

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.PackageEntity
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRelevanceTest {
    @Test
    fun searchRanksAcrossDifferentLibraryTopicsAndDropsSingleWordCollisions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "library-categories-${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            val libraries = listOf(
                createLibrary(
                    directory,
                    "travel",
                    "Travel",
                    listOf(
                        Passage(
                            "transit",
                            "Paris public transport includes the Metro and local buses.",
                            "Public transport"
                        )
                    )
                ),
                createLibrary(
                    directory,
                    "food",
                    "Food",
                    listOf(
                        Passage(
                            "cafes",
                            "Paris cafes offer coffee, pastries, and milk drinks.",
                            "Cafe culture"
                        )
                    )
                ),
                createLibrary(
                    directory,
                    "gardening",
                    "Gardening",
                    listOf(
                        Passage(
                            "coffee-plants",
                            "Coffee plants grow in warm tropical climates and need regular rainfall.",
                            "Coffee cultivation"
                        )
                    )
                ),
                createLibrary(
                    directory,
                    "electronics",
                    "Electronics",
                    listOf(
                        Passage("capacitor", "A capacitor stores electric charge.", "Electronics"),
                        Passage("repair", "Radio repair uses proper tools and care.", "Repair")
                    )
                )
            )
            val store = LibraryStore()

            assertEquals(
                listOf("travel/transit"),
                store.retrieve(libraries, "Paris public transport options", null).map { it.id }
            )
            val coffee = store.retrieve(libraries, "how do coffee plants grow", null)
            assertEquals(listOf("gardening/coffee-plants"), coffee.map { it.id })
            val noMatch = store.retrieve(libraries, "repair a quantum flux capacitor", null)
            assertTrue("Unexpected passages: $noMatch", noMatch.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aSharedPlaceNameDoesNotKeepAnUnrelatedPassage() {
        withLibrary(
            listOf(
                Passage(
                    "cafes",
                    "Paris cafes offer coffee, pastries, and milk drinks.",
                    "Cafe culture"
                ),
                Passage(
                    "transit",
                    "Paris public transport includes the Metro and local buses.",
                    "Public transport"
                )
            )
        ) { library ->
            val result = LibraryStore().retrieve(
                listOf(library),
                "Paris public transport options",
                null
            )

            assertEquals(listOf("transit"), result.map { it.id.substringAfter('/') })
        }
    }

    @Test
    fun aNoMatchNeedsMoreThanOneAccidentalKeywordHit() {
        withLibrary(
            listOf(
                Passage("radio-repair", "Radio repair requires care and proper tools.", "Repair"),
                Passage("capacitor", "A capacitor stores an electric charge.", "Electronics")
            )
        ) { library ->
            val store = LibraryStore()

            val noMatch = store.retrieve(listOf(library), "repair a quantum flux capacitor", null)
            assertTrue("Unexpected passages: $noMatch", noMatch.isEmpty())
            assertEquals(
                listOf("capacitor"),
                store.retrieve(listOf(library), "capacitor", null)
                    .map { it.id.substringAfter('/') }
            )
        }
    }

    private data class Passage(val id: String, val text: String, val section: String)

    private fun withLibrary(passages: List<Passage>, run: (PackageEntity) -> Unit) {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "library-relevance-${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            run(createLibrary(directory, "library", "Test library", passages))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createLibrary(
        directory: File,
        key: String,
        title: String,
        passages: List<Passage>
    ): PackageEntity {
        val file = File(directory, "$key.sqlite")
        BundledSQLiteDriver().open(file.path).use { db ->
            listOf(
                "PRAGMA user_version=2",
                "CREATE TABLE documents(id TEXT PRIMARY KEY,title TEXT,publisher TEXT,url TEXT,license TEXT,updated TEXT,saved TEXT,body TEXT,collection TEXT)",
                "CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT,body TEXT,section TEXT,title TEXT)",
                "CREATE VIRTUAL TABLE passages_fts USING fts5(body,title,section,content='passages',content_rowid='id')"
            ).forEach { sql -> db.prepare(sql).use { it.step() } }

            passages.forEach { passage ->
                db.prepare(
                    "INSERT INTO documents VALUES (?,?,'Author','local:test','CC0','today','today',?,'Test')"
                ).use {
                    it.bindText(1, passage.id)
                    it.bindText(2, passage.section)
                    it.bindText(3, passage.text)
                    it.step()
                }
                db.prepare(
                    "INSERT INTO passages(doc_id,body,section,title) VALUES (?,?,?,?)"
                ).use {
                    it.bindText(1, passage.id)
                    it.bindText(2, passage.text)
                    it.bindText(3, passage.section)
                    it.bindText(4, passage.section)
                    it.step()
                }
            }
            db.prepare("INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')").use {
                it.step()
            }
        }
        return PackageEntity(
            key = key,
            packageId = key,
            version = "1",
            kind = "library",
            title = title,
            path = file.absolutePath,
            bytes = file.length(),
            sha256 = "0".repeat(64),
            license = "CC0",
            origin = "local:test",
            installedAt = 0L
        )
    }
}
