package dev.blobs.app

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.preferences.ThemeMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

@ManualCheck
class VisualCaptureTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int): String = compose.activity.getString(id)

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val folder = File(compose.activity.filesDir, "screens").apply { mkdirs() }
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
    fun captureInstalledProductAtNormalAndLargeText() {
        runBlocking {
            val application = compose.activity.application as BlobsApplication
            val preferences = application.container.preferences
            preferences.setTextScale(1f)
            preferences.setThemeMode(ThemeMode.SYSTEM)
            preferences.selectConversation("")
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.explore)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(
                    text(R.string.question_hint)
                ).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText(text(R.string.explore)).fetchSemanticsNodes().isNotEmpty()) {
            capture("welcome")
            compose.onNodeWithText(text(R.string.explore)).performScrollTo().performClick()
        }
        compose.onNodeWithContentDescription(text(R.string.history)).performClick()
        compose.onNodeWithContentDescription(text(R.string.new_chat)).performClick()
        capture("home")
        compose.onNodeWithContentDescription(text(R.string.history)).performClick()
        capture("history")
        compose.onNode(hasText("How do trees reduce urban heat?", substring = true)).performClick()
        capture("answer")
        compose.onNode(hasText("Read source 1:", substring = true)).performScrollTo().performClick()
        capture("citation")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.library)).performClick()
        capture("library")
        compose.onNodeWithText(text(R.string.browse)).performScrollTo().performClick()
        capture("browse")
        compose.onNodeWithText("Agriculture").performClick()
        capture("reader")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.models)).performScrollTo().performClick()
        capture("models")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.settings)).performScrollTo().performClick()
        capture("settings")
        compose.onNodeWithText(text(R.string.appearance)).performClick()
        capture("appearance")
        compose.onNodeWithText(text(R.string.theme_dark)).performClick()
        capture("dark-appearance")
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
        ).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) {
            it(2f)
        }
        capture("large-appearance")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        capture("large-settings")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        capture("large-library")
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        capture("large-answer")
    }
}
