package dev.blobs.app

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import dev.blobs.app.data.packages.CatalogRefreshOutcome
import dev.blobs.app.data.packages.CatalogSource
import dev.blobs.app.data.packages.DeviceProfile
import dev.blobs.app.data.packages.DownloadFormat
import dev.blobs.app.data.packages.PackageCatalog
import dev.blobs.app.data.packages.PackageRole
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Page
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
        val history = compose.activity.getString(R.string.open_navigation)
        fun rendered() = compose.onAllNodesWithText(wordmark).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithContentDescription(history).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText(
                compose.activity.getString(R.string.setup_header)
            ).fetchSemanticsNodes().isNotEmpty()
        compose.waitUntil(10_000) {
            rendered()
        }
        if (compose.onAllNodesWithText(wordmark).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(wordmark).assertIsDisplayed()
        } else {
            if (compose.onAllNodesWithContentDescription(
                    history
                ).fetchSemanticsNodes().isNotEmpty()
            ) {
                compose.onNodeWithContentDescription(history).assertIsDisplayed()
            } else {
                // The page title is the app bar, which has no scroll parent to reach.
                compose.onNodeWithText(
                    compose.activity.getString(R.string.setup_header)
                ).assertIsDisplayed()
            }
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            rendered()
        }
        if (compose.onAllNodesWithText(wordmark).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(wordmark).assertIsDisplayed()
        } else {
            if (compose.onAllNodesWithContentDescription(
                    history
                ).fetchSemanticsNodes().isNotEmpty()
            ) {
                compose.onNodeWithContentDescription(history).assertIsDisplayed()
            } else {
                compose.onNodeWithText(
                    compose.activity.getString(R.string.setup_header)
                ).assertIsDisplayed()
            }
        }
    }

    @Test
    fun draftHistoryLibraryAndAppearanceFormAContinuousJourney() {
        fun text(id: Int) = compose.activity.getString(id)
        runBlocking {
            val app = compose.activity.application as BlobsApplication
            app.container.preferences.setThemeMode(ThemeMode.SYSTEM)
            app.container.preferences.welcome()
        }
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        val previous = model.conversationId.value
        compose.runOnUiThread { model.newConversation() }
        compose.waitUntil(10_000) { model.conversationId.value != previous }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(text(R.string.open_navigation))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(text(R.string.open_navigation)).performClick()
        compose.onNodeWithText(text(R.string.new_chat)).performClick()
        compose.onNodeWithText(
            text(R.string.question_hint)
        ).performTextInput("Remember my question")
        compose.onNodeWithContentDescription(text(R.string.open_navigation)).performClick()
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        compose.onNodeWithText(text(R.string.search_conversations)).performClick()
        compose.onNodeWithText(text(R.string.search_history)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText("Remember my question").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Remember my question").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(text(R.string.open_navigation)).performClick()
        compose.onNodeWithText(text(R.string.library)).performClick()
        compose.onNodeWithText(text(R.string.your_collections)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.open_navigation)).performClick()
        compose.onNodeWithText(text(R.string.settings)).performClick()
        compose.onNodeWithText(text(R.string.appearance)).performClick()
        compose.onNodeWithText(text(R.string.theme_dark)).performClick()
        compose.onNodeWithText(text(R.string.theme_system)).performClick()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.privacy)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.privacy_local)).assertIsDisplayed()
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
        assertEquals(2, catalog.models.size)
        assertEquals("qwen35-2b-q4km", catalog.models[0].manifest.id)
        assertEquals(DownloadFormat.GGUF, catalog.models[0].format)
        assertTrue(catalog.models[0].url.contains("/unsloth/Qwen3.5-2B-GGUF/resolve/"))
        assertEquals("phi4-mini-q4km", catalog.models[1].manifest.id)
        assertEquals("model", catalog.models.first().manifest.kind)
        assertEquals("library", catalog.starterLibrary.manifest.kind)
        assertEquals("wikivoyage-en", catalog.starterLibrary.manifest.id)
        assertEquals(
            setOf(
                "wikipedia-top1m-mini",
                "wikipedia-starter",
                "nps-outdoor-safety",
                "ready-emergency-preparedness",
                "camping-safety",
                "backcountry-basics",
                "weather-safety",
                "earthquake-tsunami-safety",
                "emergency-food",
                "practical-living"
            ),
            catalog.additionalLibraries.map { it.manifest.id }.toSet()
        )
        assertTrue(catalog.additionalLibraries.all { it.role == PackageRole.OPTIONAL })
        val outdoorSafety = catalog.additionalLibraries.single {
            it.manifest.id == "nps-outdoor-safety"
        }
        assertEquals("Outdoor safety · U.S. national parks", outdoorSafety.manifest.title)
        assertTrue(outdoorSafety.description.contains("not affiliated with or endorsed by NPS"))
        assertEquals(DownloadFormat.BLOBS, outdoorSafety.format)
        val emergencyPreparedness = catalog.additionalLibraries.single {
            it.manifest.id == "ready-emergency-preparedness"
        }
        assertEquals(
            "Emergency preparedness · U.S. households",
            emergencyPreparedness.manifest.title
        )
        assertEquals(DownloadFormat.BLOBS, emergencyPreparedness.format)
        assertTrue(
            emergencyPreparedness.description.contains("follow current local instructions")
        )
        assertTrue(
            emergencyPreparedness.description.contains("not endorsed by FEMA")
        )
        assertEquals(
            catalog.models.first(),
            catalog.recommendedModel(3L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024)
        )
        assertEquals(
            catalog.models[0],
            catalog.recommendedModel(8L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024)
        )
        assertEquals(
            catalog.models[0],
            catalog.recommendedModel(16L * 1024 * 1024 * 1024, 12L * 1024 * 1024 * 1024)
        )
        assertTrue(catalog.starterLibrary.downloadSha256.matches(Regex("[a-f0-9]{64}")))

        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        compose.runOnUiThread { model.navigate(Page.WELCOME) }
        compose.onAllNodesWithText(compose.activity.getString(R.string.import_library_title))
            .assertCountEquals(0)
        compose.onAllNodesWithText(compose.activity.getString(R.string.progress_percent, 0))
            .assertCountEquals(0)
        compose.onNodeWithText(compose.activity.getString(R.string.welcome_headline))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.welcome_start))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { model.state.value.page == Page.SETUP }
        if (model.packages.value.none { it.kind == "model" }) {
            val profile = DeviceProfile.inspect(compose.activity, catalog)
            val recommended = profile.recommendedModel
            compose.onAllNodesWithText(compose.activity.getString(R.string.progress_percent, 0))
                .assertCountEquals(0)
            compose.onAllNodesWithText(compose.activity.getString(R.string.setup_model_step))
                .assertCountEquals(0)
            compose.onNodeWithText(compose.activity.getString(R.string.setup_model_title))
                .assertIsDisplayed()
            compose.onNodeWithText(recommended.manifest.title).assertIsDisplayed()
            compose.onNodeWithText(
                compose.activity.getString(
                    if (profile.recommendedModelFitsDevice) {
                        R.string.recommended
                    } else {
                        R.string.model_smallest_option
                    }
                )
            ).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.download_selected_model))
                .assertIsDisplayed()
            if (!profile.recommendedModelFitsStorage) {
                compose.onNodeWithText(
                    compose.activity.getString(
                        R.string.model_space_to_free,
                        android.text.format.Formatter.formatFileSize(
                            compose.activity,
                            (
                                catalog.requiredStorageBytes(recommended) -
                                    profile.freeStorageBytes
                                ).coerceAtLeast(0)
                        )
                    )
                ).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(
                    compose.activity.getString(R.string.download_selected_model)
                ).assertIsNotEnabled()
            }
            compose.onNodeWithText(
                compose.activity.getString(R.string.compare_models)
            ).performScrollTo().performClick()
            compose.onAllNodesWithText(catalog.models[1].manifest.title)[0]
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.download_selected_model))
                .assertIsDisplayed()
            compose.onAllNodesWithText(compose.activity.getString(R.string.download_library))
                .assertCountEquals(0)
            compose.onAllNodesWithText(compose.activity.getString(R.string.import_library_title))
                .assertCountEquals(0)
        } else if (model.packages.value.none {
                it.packageId == catalog.starterLibrary.manifest.id &&
                    it.version == catalog.starterLibrary.manifest.version
            }
        ) {
            compose.onNodeWithText(compose.activity.getString(R.string.download_starter))
                .assertIsDisplayed()
            compose.onAllNodesWithText(compose.activity.getString(R.string.import_library_title))
                .assertCountEquals(0)
        }
    }

    @ManualCheck
    @Test
    fun starterLibraryFromHubAppearsInYourLibrary() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val catalog = model.catalog
        runBlocking {
            (compose.activity.application as BlobsApplication).container.preferences.welcome()
        }
        compose.waitUntil(10_000) {
            model.state.value.loaded && model.state.value.options.welcomed
        }
        assertTrue(
            runBlocking { catalog.refreshNow() } in
                setOf(CatalogRefreshOutcome.UPDATED, CatalogRefreshOutcome.UNCHANGED)
        )
        assertEquals(CatalogSource.REMOTE, catalog.state.value.source)

        val starter = catalog.starterLibrary
        if (model.packages.value.none {
                it.packageId == starter.manifest.id && it.version == starter.manifest.version
            }
        ) {
            model.downloadPackage(starter)
        }
        compose.waitUntil(120_000) {
            model.packages.value.any {
                it.packageId == starter.manifest.id && it.version == starter.manifest.version
            }
        }
        compose.runOnUiThread { model.navigate(Page.LIBRARY) }
        compose.waitUntil(10_000) { model.state.value.page == Page.LIBRARY }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(starter.manifest.title)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(starter.manifest.title).performScrollTo().assertIsDisplayed()
    }

    @ManualCheck
    @Test
    fun emergencyPreparednessLibraryFromHubAppearsInYourLibrary() {
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val catalog = model.catalog
        runBlocking {
            (compose.activity.application as BlobsApplication).container.preferences.welcome()
        }
        compose.waitUntil(10_000) {
            model.state.value.loaded && model.state.value.options.welcomed
        }
        assertTrue(
            runBlocking { catalog.refreshNow() } in
                setOf(CatalogRefreshOutcome.UPDATED, CatalogRefreshOutcome.UNCHANGED)
        )
        assertEquals(CatalogSource.REMOTE, catalog.state.value.source)

        val library = catalog.additionalLibraries.single {
            it.manifest.id == "ready-emergency-preparedness"
        }
        if (model.packages.value.none {
                it.packageId == library.manifest.id && it.version == library.manifest.version
            }
        ) {
            model.downloadPackage(library)
        }
        compose.waitUntil(120_000) {
            model.packages.value.any {
                it.packageId == library.manifest.id && it.version == library.manifest.version
            }
        }
        compose.runOnUiThread { model.navigate(Page.LIBRARY) }
        compose.waitUntil(10_000) { model.state.value.page == Page.LIBRARY }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(library.manifest.title)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(library.manifest.title).performScrollTo().assertIsDisplayed()
    }
}
