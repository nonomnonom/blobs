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
import dev.blobs.app.data.packages.DownloadFormat
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
            "v0.3.1",
            catalog.models.first().url.substringAfter("/download/").substringBefore('/')
        )
        assertEquals(3, catalog.models.size)
        assertEquals("gemma4-e2b-q4", catalog.models[1].manifest.id)
        assertEquals(DownloadFormat.GGUF, catalog.models[1].format)
        assertTrue(
            catalog.models[1].url.contains("/resolve/675cff42a74c774d6cb76f76d8eacb49b48c9b93/")
        )
        assertEquals("gemma4-e4b-q4", catalog.models[2].manifest.id)
        assertEquals("model", catalog.models.first().manifest.kind)
        assertEquals("library", catalog.starterLibrary.manifest.kind)
        assertEquals("wikipedia-starter", catalog.starterLibrary.manifest.id)
        assertEquals(1, catalog.additionalLibraries.size)
        assertEquals("optional", catalog.additionalLibraries.single().role.name.lowercase())
        assertEquals(
            catalog.models.first(),
            catalog.recommendedModel(3L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024)
        )
        assertEquals(
            catalog.models[1],
            catalog.recommendedModel(8L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024)
        )
        assertEquals(
            catalog.models[2],
            catalog.recommendedModel(16L * 1024 * 1024 * 1024, 12L * 1024 * 1024 * 1024)
        )
        assertTrue(catalog.starterLibrary.downloadSha256.matches(Regex("[a-f0-9]{64}")))

        val model = ViewModelProvider(compose.activity)[ProductViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        compose.runOnUiThread { model.navigate(Page.WELCOME) }
        compose.onAllNodesWithText(compose.activity.getString(R.string.have_local_packages))
            .assertCountEquals(0)
        compose.onAllNodesWithText(compose.activity.getString(R.string.progress_percent, 0))
            .assertCountEquals(0)
        compose.onNodeWithText(compose.activity.getString(R.string.diagnosis_title))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.choose_model))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { model.state.value.page == Page.SETUP }
        if (model.packages.value.none { it.kind == "model" }) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.progress_percent, 0))
                .assertCountEquals(0)
            compose.onAllNodesWithText(compose.activity.getString(R.string.setup_model_step))
                .assertCountEquals(0)
            compose.onNodeWithText(compose.activity.getString(R.string.setup_model_title))
                .assertIsDisplayed()
            compose.onNodeWithText(catalog.models[1].manifest.title)
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(catalog.models[2].manifest.title)
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.download_selected_model))
                .performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText(compose.activity.getString(R.string.download_library))
                .assertCountEquals(0)
            compose.onAllNodesWithText(compose.activity.getString(R.string.have_local_packages))
                .assertCountEquals(0)
        } else if (model.packages.value.none {
                it.packageId == catalog.starterLibrary.manifest.id &&
                    it.version == catalog.starterLibrary.manifest.version
            }
        ) {
            compose.onNodeWithText(compose.activity.getString(R.string.download_starter))
                .performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText(compose.activity.getString(R.string.have_local_packages))
                .assertCountEquals(0)
        }
    }
}
