package dev.blobs.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.ProductViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@ManualCheck
class OnboardingJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private val model get() = ViewModelProvider(compose.activity)[ProductViewModel::class.java]

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val folder = File(compose.activity.filesDir, "onboarding-journey").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun localSetupThroughAnswerAndSettings() {
        val args = InstrumentationRegistry.getArguments()
        val container = (compose.activity.application as BlobsApplication).container
        compose.waitUntil(10_000) { model.state.value.loaded }
        runBlocking {
            container.preferences.setThemeMode(ThemeMode.LIGHT)
            container.preferences.setTextScale(1f)
        }
        if (args.getString("resumeReading") != "true") {
            if (args.getString("showWelcome") == "true") {
                compose.runOnUiThread { model.navigate(Page.WELCOME) }
            }
            if (compose.onAllNodesWithText(
                    text(R.string.meet_blobs)
                ).fetchSemanticsNodes().isNotEmpty()
            ) {
                capture("01-welcome")
                compose.onNodeWithText(text(R.string.meet_blobs)).performScrollTo().performClick()
                compose.onNodeWithContentDescription(text(R.string.back)).performClick()
                compose.onNodeWithText(text(R.string.have_files)).assertIsDisplayed()
                compose.onNodeWithText(text(R.string.meet_blobs)).performScrollTo().performClick()
            } else {
                compose.runOnUiThread { model.navigate(Page.INTRO) }
            }
            capture("02-intro")
            compose.onNodeWithText(text(R.string.get_ready)).assertIsDisplayed().performClick()
            capture("03-setup")
            for ((name, key) in listOf("model" to "modelPackage", "library" to "libraryPackage")) {
                val path = requireNotNull(args.getString(key))
                val file = File(path)
                assertTrue("Missing local package: $path", file.isFile)
                compose.runOnUiThread { model.inspect(Uri.fromFile(file)) }
                compose.waitUntil(10_000) { model.state.value.manifest != null }
                capture("04-review-$name")
                if (name == "model") {
                    runBlocking { container.preferences.setTextScale(2f) }
                    compose.waitUntil(10_000) { model.state.value.options.textScale == 2f }
                    compose.onNodeWithText(text(R.string.add)).assertIsDisplayed()
                    compose.onNodeWithText(text(R.string.cancel)).assertIsDisplayed()
                    capture("04-large-review-model")
                    runBlocking { container.preferences.setTextScale(1f) }
                    compose.waitUntil(10_000) { model.state.value.options.textScale == 1f }
                }
                if (compose.onAllNodesWithText(
                        text(R.string.already_added)
                    ).fetchSemanticsNodes().isNotEmpty()
                ) {
                    compose.onNodeWithText(text(R.string.cancel)).performClick()
                } else {
                    compose.onNodeWithText(text(R.string.add)).performClick()
                    capture("05-import-$name")
                    compose.waitUntil(120_000) { model.work.value.importComplete }
                    capture("06-imported-$name")
                    compose.onNodeWithText(
                        text(R.string.return_setup)
                    ).performScrollTo().performClick()
                    assertEquals(Page.SETUP, model.state.value.page)
                }
            }
            capture("07-ready")
            compose.onNodeWithText(text(R.string.start_chat)).performScrollTo().performClick()
            capture("08-home")
            compose.onNodeWithContentDescription(text(R.string.question_hint))
                .performTextInput(
                    "How do trees reduce urban heat? Answer in two sentences with sources."
                )
            compose.onNodeWithContentDescription(text(R.string.send)).performClick()
            compose.waitUntil(10_000) { model.work.value.busy }
            capture("09-answering")
            compose.waitUntil(600_000) { !model.work.value.busy }
        }
        val thread = model.conversationId.value
        val turn = runBlocking { container.database.conversations().turns(thread).last() }
        assertTrue(turn.status, turn.status in setOf("COMPLETE", "LIMITED"))
        assertTrue(turn.answer.isNotBlank())
        capture("10-answer")
        val sources = dev.blobs.app.data.library.Evidence.decode(turn.evidence)
        assertTrue(sources.isNotEmpty())
        compose.onNode(
            hasContentDescription(
                compose.activity.getString(R.string.read_source, 1, sources.first().title)
            ) and
                hasText(sources.first().title)
        ).performScrollTo().performClick()
        capture("11-citation")
        compose.onNodeWithText(text(R.string.read_full_source)).assertIsDisplayed().performClick()
        capture("12-full-source")
        compose.onNodeWithText(text(R.string.ask_source)).assertIsDisplayed().performClick()
        assertEquals(setOf(sources.first().id), model.selectedScope())
        compose.onNodeWithContentDescription(text(R.string.follow_up_hint))
            .performTextReplacement("Keep my next question")
        compose.onNodeWithContentDescription(text(R.string.history)).performClick()
        capture("13-history")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText("Keep my next question").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.library)).performClick()
        capture("14-library")
        compose.onNodeWithText(text(R.string.browse)).performScrollTo().performClick()
        capture("15-browse")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.models)).performScrollTo().performClick()
        capture("16-models")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.settings)).performClick()
        capture("17-settings")
        compose.onNodeWithText(text(R.string.appearance)).performClick()
        capture("18-appearance")
        compose.onNodeWithText(text(R.string.theme_dark)).performClick()
        capture("19-dark-appearance")
        runBlocking { container.preferences.setTextScale(2f) }
        capture("20-large-appearance")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        capture("21-large-settings")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        capture("22-large-library")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        capture("23-large-answer")
        compose.onNodeWithText(text(R.string.follow_up)).performClick()
        compose.onNodeWithText("Keep my next question").assertIsDisplayed()
        capture("24-large-composer")
        compose.onNodeWithContentDescription(text(R.string.follow_up_hint)).performClick()
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        capture("25-large-keyboard")
        compose.onNodeWithContentDescription(text(R.string.history)).assertIsDisplayed()
        runBlocking {
            container.preferences.setTextScale(1f)
            container.preferences.setThemeMode(ThemeMode.LIGHT)
        }
        compose.runOnUiThread { model.newConversation() }
    }
}
