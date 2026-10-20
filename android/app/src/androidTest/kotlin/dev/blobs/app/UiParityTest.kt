package dev.blobs.app

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.ProductViewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UiParityTest {
    @get:Rule(order = 0)
    val fontScale = SystemFontScaleRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private fun revealFollowUp() {
        compose.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasContentDescription(text(R.string.follow_up_hint)))
        compose.onNodeWithContentDescription(text(R.string.follow_up_hint)).performScrollTo()
    }

    @Test
    fun importReviewReturnsToOriginAndRejectsDuplicatesWithoutLosingDraft() {
        val container = (compose.activity.application as BlobsApplication).container
        val model = ViewModelProvider(compose.activity)[ProductViewModel::class.java]
        val file = File(compose.activity.cacheDir, "ui-import.blobs")
        InstrumentationRegistry.getInstrumentation().context.assets.open("corpus-fixture.blobs")
            .use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = Uri.fromFile(file)
        val manifest = runBlocking { container.installer.inspect(uri) }
        val conversation = ConversationEntity("ui-import-${UUID.randomUUID()}", "", "", 1, 1)
        try {
            runBlocking {
                container.preferences.welcome()
                container.database.conversations().save(conversation)
            }
            compose.waitUntil(10_000) { model.state.value.loaded && model.work.value.ready }
            compose.runOnUiThread { model.openConversation(conversation) }
            compose.waitUntil(10_000) { model.conversationId.value == conversation.id }
            compose.runOnUiThread {
                model.draft("Preserve this import draft")
                model.navigate(Page.LIBRARY)
                model.inspect(uri)
                model.dismissImport()
            }
            compose.waitForIdle()
            assertEquals(null, model.state.value.importUri)
            assertEquals(null, model.state.value.manifest)
            compose.runOnUiThread { model.inspect(uri) }
            compose.waitUntil(10_000) { model.state.value.manifest != null }
            fontScale.set(2f)
            compose.onNodeWithText(text(R.string.add)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.cancel)).assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.back)).performClick()
            assertEquals(null, model.state.value.importUri)
            assertEquals(Page.LIBRARY, model.state.value.page)
            assertEquals("Preserve this import draft", model.state.value.draft)
            fontScale.set(1f)
            compose.runOnUiThread { model.inspect(uri) }
            compose.waitUntil(10_000) { model.state.value.manifest != null }
            compose.onNodeWithText(text(R.string.add)).performClick()
            compose.waitUntil(10_000) { model.work.value.importComplete }
            compose.onNodeWithText(text(R.string.done)).performScrollTo().performClick()
            assertEquals(Page.LIBRARY, model.state.value.page)
            compose.runOnUiThread { model.inspect(uri) }
            compose.waitUntil(10_000) { model.state.value.manifest != null }
            fontScale.set(2f)
            compose.onNodeWithText(
                text(R.string.already_added)
            ).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.add)).assertIsNotEnabled()
            compose.onNodeWithText(text(R.string.cancel)).performClick()
            fontScale.set(1f)
            compose.onNodeWithContentDescription(text(R.string.back)).performClick()
            compose.onNodeWithText("Preserve this import draft").assertIsDisplayed()
            val export = File(compose.activity.cacheDir, "ui-conversations.md")
            compose.runOnUiThread { model.export(compose.activity, Uri.fromFile(export)) }
            compose.waitUntil(10_000) { model.state.value.notice == R.string.export_saved }
            org.junit.Assert.assertTrue(export.readText().contains("Preserve this import draft"))
            export.delete()
            compose.runOnUiThread { model.navigate(Page.LIBRARY) }
            val toggle = compose.onNodeWithContentDescription(
                compose.activity.getString(R.string.use_collection_for_answers, manifest.title)
            )
            toggle.performScrollTo().performClick()
            compose.waitUntil(10_000) {
                model.packages.value.any { it.key == manifest.key && !it.enabled }
            }
            toggle.performClick()
            compose.waitUntil(10_000) {
                model.packages.value.any { it.key == manifest.key && it.enabled }
            }
            compose.runOnUiThread { model.navigate(Page.STORAGE) }
            compose.onNodeWithContentDescription(
                compose.activity.getString(R.string.remove_package_name, manifest.title)
            ).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.remove)).performClick()
            compose.waitUntil(10_000) { model.packages.value.none { it.key == manifest.key } }
            assertEquals("Preserve this import draft", model.state.value.draft)
        } finally {
            runBlocking {
                container.database.packages().all().find { it.key == manifest.key }?.let {
                    container.installer.remove(it)
                }
                container.preferences.selectConversation("")
                container.database.conversations().delete(conversation.id)
            }
            file.delete()
        }
    }

    @Test
    fun renamingAndDeletingAConversationKeepsOtherDrafts() {
        val container = (compose.activity.application as BlobsApplication).container
        val model = ViewModelProvider(compose.activity)[ProductViewModel::class.java]
        val dao = container.database.conversations()
        val id = "history-${UUID.randomUUID()}"
        val title = "History smoke $id"
        val target = ConversationEntity(id, title, "Keep target draft", 1, 1)
        val other = ConversationEntity("$id-other", "Other conversation", "Keep other draft", 1, 1)
        runBlocking {
            dao.save(target)
            dao.save(other)
            dao.saveTurn(
                TurnEntity(
                    "$id-turn", id, "Saved question", "Saved answer", "COMPLETE",
                    "Test model", "*", "[]", 1
                )
            )
        }
        try {
            compose.waitUntil(10_000) { model.state.value.loaded && model.work.value.ready }
            compose.runOnUiThread { model.navigate(Page.HISTORY) }
            compose.onNodeWithContentDescription(
                text(R.string.search_history)
            ).performTextInput(title)
            compose.onNodeWithContentDescription(text(R.string.rename)).performClick()
            compose.onNodeWithText(text(R.string.conversation_title))
                .performTextReplacement("$title renamed")
            compose.onNodeWithText(text(R.string.save)).performClick()
            compose.onNodeWithText("$title renamed").assertIsDisplayed()
            runBlocking {
                assertEquals("Keep target draft", dao.find(id)?.draft)
                assertEquals("Saved answer", dao.turns(id).single().answer)
            }
            compose.onNodeWithContentDescription(text(R.string.delete)).performClick()
            compose.onNodeWithText(text(R.string.cancel)).performClick()
            compose.onNodeWithText("$title renamed").assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.delete)).performClick()
            compose.onNodeWithText(text(R.string.delete)).performClick()
            compose.onNodeWithText(text(R.string.history_no_match)).assertIsDisplayed()
            runBlocking {
                assertEquals(null, dao.find(id))
                assertEquals(emptyList<TurnEntity>(), dao.turns(id))
                assertEquals("Keep other draft", dao.find(other.id)?.draft)
            }
        } finally {
            runBlocking {
                dao.delete(id)
                dao.delete(other.id)
            }
        }
    }

    @Test
    fun readingControlsKeepDraftAndOpenSavedSourcesAtDoubleText() {
        val container = (compose.activity.application as BlobsApplication).container
        val id = "ui-${UUID.randomUUID()}"
        val source = Evidence(
            "saved-test-source", "UI test source", "Test publisher", "", "Test fixture",
            "2026-10-17", "2026-10-17", "Saved passage", "A saved passage for the UI test.", "test"
        )
        runBlocking {
            container.preferences.welcome()
            container.preferences.setThemeMode(ThemeMode.LIGHT)
            container.database.conversations().save(
                ConversationEntity(id, "UI reading test", "", 1, 1)
            )
            container.database.conversations().saveTurn(
                TurnEntity(
                    "$id-turn", id, "UI reading test",
                    """A saved $\text{CO}_2$ answer [1].""", "STOPPED",
                    "Test model", "*", Evidence.encode(listOf(source)), 1
                )
            )
            container.preferences.selectConversation(id)
        }
        try {
            fontScale.set(2f)
            val conversation =
                runBlocking { requireNotNull(container.database.conversations().find(id)) }
            compose.runOnUiThread {
                ViewModelProvider(compose.activity)[ProductViewModel::class.java]
                    .openConversation(conversation)
            }
            compose.waitUntil(10_000) {
                ViewModelProvider(compose.activity)[ProductViewModel::class.java]
                    .conversationId.value == id
            }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) {
                ViewModelProvider(compose.activity)[ProductViewModel::class.java]
                    .turns.value.any { it.id == "$id-turn" }
            }
            val formattedAnswer = hasText("A saved CO₂ answer", substring = true)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(formattedAnswer)
            compose.onNode(formattedAnswer).assertIsDisplayed()
            revealFollowUp()
            compose.onNodeWithContentDescription(text(R.string.follow_up_hint))
                .performTextInput("Keep this follow-up")
            compose.activityRule.scenario.onActivity { activity ->
                androidx.core.view.WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView
                )
                    .hide(WindowInsetsCompat.Type.ime())
            }
            revealFollowUp()
            compose.onNodeWithText("Keep this follow-up").assertIsDisplayed()
            val selectedTitle = ViewModelProvider(compose.activity)[ProductViewModel::class.java]
                .selectedModel()?.title ?: text(R.string.model_missing)
            compose.onNodeWithContentDescription(
                compose.activity.getString(R.string.model_choice, selectedTitle)
            ).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.manage_models)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.done)).performClick()
            compose.onNodeWithText("Keep this follow-up").assertIsDisplayed()
            compose.activityRule.scenario.onActivity { activity ->
                androidx.core.view.WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView
                )
                    .hide(WindowInsetsCompat.Type.ime())
            }
            val label = compose.activity.getString(R.string.read_source, 1, source.title)
            val sourceRow = hasContentDescription(label) and hasText(source.title)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(sourceRow)
            compose.onNode(sourceRow).performScrollTo().performClick()
            compose.onNodeWithText(source.text).assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.back)).performClick()
            compose.onNodeWithContentDescription(text(R.string.open_navigation)).performClick()
            compose.onNodeWithText(text(R.string.search_conversations)).performClick()
            compose.onNodeWithContentDescription(text(R.string.search_history))
                .performTextInput("no-match-for-this-ui-test")
            compose.onNodeWithText(text(R.string.history_no_match)).performScrollTo()
                .assertIsDisplayed()
            compose.onNodeWithText(text(R.string.clear_search)).performScrollTo().performClick()
            compose.onNodeWithContentDescription(text(R.string.search_history))
                .performTextInput("UI reading")
            compose.onNode(hasText("UI reading test") and hasClickAction()).performClick()
            compose.waitUntil(10_000) {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == false
            }
            revealFollowUp()
            compose.onNodeWithText("Keep this follow-up").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) {
                ViewModelProvider(compose.activity)[ProductViewModel::class.java]
                    .turns.value.any { it.id == "$id-turn" }
            }
            revealFollowUp()
            compose.onNodeWithText("Keep this follow-up").assertIsDisplayed()
            runBlocking {
                assertEquals(
                    "Keep this follow-up",
                    container.database.conversations().find(id)?.draft
                )
                assertEquals(
                    "STOPPED",
                    container.database.conversations().turns(id).single().status
                )
                assertEquals(
                    """A saved $\text{CO}_2$ answer [1].""",
                    container.database.conversations().turns(id).single().answer
                )
            }
        } finally {
            runBlocking {
                container.preferences.setThemeMode(ThemeMode.SYSTEM)
                container.preferences.selectConversation("")
                container.database.conversations().delete(id)
            }
        }
    }
}
