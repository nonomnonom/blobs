package dev.blobs.app

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import dev.blobs.app.data.packages.PackageCatalog
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.ProductViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            app.container.preferences.welcome()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(text(R.string.history))
                .fetchSemanticsNodes().isNotEmpty()
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
    fun applicationCanDownloadSetupPackages() {
        val activity = compose.activity
        val permissions =
            activity.packageManager
                .getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
        assertTrue(permissions.contains("android.permission.INTERNET"))
    }

    @Test
    fun firstRunCatalogIsPinnedAndSetupOffersInAppDownloads() {
        val catalog = PackageCatalog(compose.activity)
        assertEquals(
            "v0.3.0",
            catalog.recommendedModel.url.substringAfter("/download/").substringBefore('/')
        )
        assertEquals("model", catalog.recommendedModel.manifest.kind)
        assertEquals("library", catalog.starterLibrary.manifest.kind)
        assertTrue(catalog.recommendedModel.archiveBytes > 1_000_000_000L)
        assertTrue(catalog.starterLibrary.archiveSha256.matches(Regex("[a-f0-9]{64}")))

        val model = ViewModelProvider(compose.activity)[ProductViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        compose.runOnUiThread { model.navigate(Page.WELCOME) }
        compose.onAllNodesWithText("I already have the files").assertCountEquals(0)
        compose.onNodeWithText(compose.activity.getString(R.string.prepare)).performClick()
        compose.waitUntil(10_000) { model.state.value.page == Page.SETUP }
        if (model.packages.value.none { it.kind == "model" } ||
            model.packages.value.none { it.kind == "library" }
        ) {
            compose.onNodeWithText(compose.activity.getString(R.string.download_model))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.download_library))
                .performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText(compose.activity.getString(R.string.have_local_packages))
                .assertCountEquals(0)
        }
    }
}
