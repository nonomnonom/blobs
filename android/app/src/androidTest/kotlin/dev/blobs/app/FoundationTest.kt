package dev.blobs.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.data.preferences.PreferencesStore
import dev.blobs.app.data.preferences.ThemeMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun conversationDraftsSurviveReopenAndStayWithTheirConversation() = runBlocking {
        val name = "foundation-${UUID.randomUUID()}.db"
        var database = BlobsDatabase.open(context, name)
        try {
            val first = ConversationEntity("first", "First question", "First draft", 1, 1)
            val second = ConversationEntity("second", "Second question", "Second draft", 2, 2)
            database.conversations().save(first)
            database.conversations().save(second)
            database.conversations().save(first.copy(draft = "Updated draft", updatedAt = 3))
            database.close()
            database = BlobsDatabase.open(context, name)

            assertEquals("Updated draft", database.conversations().find("first")?.draft)
            assertEquals(second, database.conversations().find("second"))
            assertEquals(
                listOf("first", "second"),
                database
                    .conversations()
                    .observeConversations()
                    .first()
                    .map { it.id }
            )
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun appearancePreferenceSurvivesStoreRecreation() = runBlocking {
        val file = File(context.cacheDir, "foundation-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()

        fun openStore() = PreferencesStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + job),
                produceFile = { file }
            )
        )

        try {
            val first = openStore()
            assertEquals(ThemeMode.SYSTEM, first.themeMode.first())
            first.setThemeMode(ThemeMode.DARK)
            job.cancelAndJoin()
            job = SupervisorJob()
            assertEquals(ThemeMode.DARK, openStore().themeMode.first())
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun bundledSqliteCanSearchFts5WithoutNetwork() {
        BundledSQLiteDriver().open(":memory:").use { connection ->
            connection.prepare("CREATE VIRTUAL TABLE passages USING fts5(title, body)").use {
                it.step()
            }
            connection.prepare("INSERT INTO passages VALUES (?, ?)").use {
                it.bindText(1, "Tree shade")
                it.bindText(2, "Trees provide shade in a neighbourhood.")
                it.step()
            }
            connection.prepare(
                "SELECT title FROM passages WHERE passages MATCH ? ORDER BY bm25(passages)"
            ).use {
                it.bindText(1, "shade")
                assertTrue(it.step())
                assertEquals("Tree shade", it.getText(0))
                assertFalse(it.step())
            }
        }
    }
}
