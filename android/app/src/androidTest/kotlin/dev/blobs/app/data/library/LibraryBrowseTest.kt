package dev.blobs.app.data.library

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.local.PackageEntity
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryBrowseTest {
    @Test
    fun pagesMergeEnabledLibrariesInGlobalTitleAndIdentityOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "browse-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val rows = (0 until 93).map { index ->
                "doc-$index" to if (index % 9 == 0) "Same title" else "Place %03d".format(index)
            } + listOf("literal" to "100% trail_a\\path")
            val first = library(folder, "a", rows)
            val second = library(folder, "b", rows.reversed())
            val packs = listOf(second.copy(key = "disabled", enabled = false), second, first)
            val expected = listOf("a", "b").flatMap { key ->
                rows.map { (id, title) -> title to "$key/$id" }
            }.sortedWith(compareBy({ it.first }, { it.second })).map { it.second }
            val store = LibraryStore()
            for (offset in listOf(0, 50, 100, 150, 200)) {
                assertEquals(
                    "Global page at offset $offset",
                    expected.drop(offset).take(51),
                    store.browse(packs, "", offset).map { it.id }
                )
            }
            for (query in listOf("%", "_", "\\")) {
                assertEquals(
                    listOf("a/literal", "b/literal"),
                    store.browse(packs, query).map { it.id }
                )
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun rejectsOffsetsThatCouldOverflowThePageLimit() {
        val store = LibraryStore()
        for (offset in listOf(-1, Int.MAX_VALUE - 50, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                store.browse(emptyList(), "", offset)
            }
        }
        assertEquals(emptyList<Evidence>(), store.browse(emptyList(), "", Int.MAX_VALUE - 51))
    }

    private fun library(
        folder: File,
        key: String,
        rows: List<Pair<String, String>>
    ): PackageEntity {
        val file = File(folder, "$key.sqlite")
        BundledSQLiteDriver().open(file.path).use { db ->
            db.prepare(
                "CREATE TABLE documents(id TEXT,title TEXT,publisher TEXT,url TEXT,license TEXT,updated TEXT,saved TEXT,collection TEXT,body TEXT)"
            ).use { it.step() }
            db.prepare(
                "INSERT INTO documents VALUES (?,?,'Author','local:test','Test','','','Test','Text')"
            )
                .use { statement ->
                    for ((id, title) in rows) {
                        statement.bindText(1, id)
                        statement.bindText(2, title)
                        statement.step()
                        statement.reset()
                    }
                }
        }
        return PackageEntity(
            key, key, "1", "library", "Test $key", file.path, file.length(),
            "0".repeat(64), "Test", "local:test", 1
        )
    }
}
