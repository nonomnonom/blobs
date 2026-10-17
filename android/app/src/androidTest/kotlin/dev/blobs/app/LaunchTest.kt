package dev.blobs.app

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.blobs.app.data.preferences.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun appRendersAfterActivityRecreation() {
        val wordmark = compose.activity.getString(R.string.app_wordmark)
        val history = compose.activity.getString(R.string.history)
        fun rendered() = compose.onAllNodesWithText(wordmark).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithContentDescription(history).fetchSemanticsNodes().isNotEmpty()
        compose.waitUntil(10_000) {
            rendered()
        }
        if (compose.onAllNodesWithText(wordmark).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(wordmark).assertIsDisplayed()
        } else {
            compose.onNodeWithContentDescription(history).assertIsDisplayed()
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            rendered()
        }
        if (compose.onAllNodesWithText(wordmark).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(wordmark).assertIsDisplayed()
        } else {
            compose.onNodeWithContentDescription(history).assertIsDisplayed()
        }
    }

    @Test
    fun draftHistoryLibraryAndAppearanceFormAContinuousJourney() {
        fun text(id: Int) = compose.activity.getString(id)
        runBlocking {
            val app = compose.activity.application as BlobsApplication
            app.container.preferences.setTextScale(1f)
            app.container.preferences.setThemeMode(ThemeMode.SYSTEM)
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.explore)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithContentDescription(
                    text(R.string.history)
                ).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText(text(R.string.explore)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(text(R.string.explore)).performScrollTo().performClick()
        }
        compose.onNodeWithContentDescription(text(R.string.history)).performClick()
        compose.onNodeWithContentDescription(text(R.string.new_chat)).performClick()
        compose.onNodeWithText(
            text(R.string.question_hint)
        ).performTextInput("Remember my question")
        compose.onNodeWithContentDescription(text(R.string.history)).performClick()
        compose.onNodeWithText(text(R.string.search_history)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText("Remember my question").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Remember my question").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(text(R.string.library)).performClick()
        compose.onNodeWithText(text(R.string.settings)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.appearance)).performClick()
        compose.onNodeWithText(text(R.string.theme_dark)).performClick()
        compose.onNodeWithText(text(R.string.theme_system)).performClick()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.privacy)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.privacy_body)).assertIsDisplayed()
    }

    @Test
    @Suppress("DEPRECATION")
    fun applicationHasNoNetworkPermission() {
        val activity = compose.activity
        val permissions =
            activity.packageManager
                .getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
    }
}
