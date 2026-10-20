package dev.blobs.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.library.RetrievalQuery
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.ProductViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Run only in a fresh app sandbox with hash-pinned downloads staged in its private cache. */
class FirstRunJourneyTest {
    @get:Rule(order = 0)
    val fontScale = SystemFontScaleRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @ManualCheck
    @Test
    fun reopeningAPausedLibraryDownloadRestoresItsContext() {
        val expected = InstrumentationRegistry.getArguments().getString("pausedPackageId")
        assumeTrue(expected != null)
        val model = ViewModelProvider(compose.activity)[ProductViewModel::class.java]
        fun text(id: Int) = compose.activity.getString(id)
        compose.waitUntil(15_000) { model.state.value.loaded && model.work.value.ready }
        assertEquals(expected, model.work.value.download?.manifest?.id)
        assertEquals(512L, model.work.value.progress)
        assertFalse(model.work.value.transferActive)
        assertEquals(Page.CHAT, model.state.value.page)
        compose.onNodeWithText(text(R.string.paused_import)).performClick()
        compose.waitUntil(10_000) { model.state.value.page == Page.IMPORT }
        compose.onNodeWithText(text(R.string.resume_download)).performScrollTo().assertIsDisplayed()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir("ui-review"), "first-run-restored-download.png")
            .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.waitUntil(10_000) { model.state.value.page == Page.LIBRARY }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { model.state.value.page == Page.CHAT }
        compose.onNodeWithText(text(R.string.paused_import)).performClick()
        compose.onNodeWithText(text(R.string.discard_download)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.discard_download_title)).assertIsDisplayed()
        val discardButtons = compose.onAllNodesWithText(text(R.string.discard_download))
        discardButtons[discardButtons.fetchSemanticsNodes().lastIndex].performClick()
        compose.waitUntil(10_000) { !model.work.value.busy && !model.work.value.hasPendingTransfer }
    }

    @ManualCheck
    @Test
    fun welcomeThroughDownloadsFirstQuestionAndInAppSource() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firstRun") == "true")
        val model = ViewModelProvider(compose.activity)[ProductViewModel::class.java]
        fun text(id: Int) = compose.activity.getString(id)
        fun tap(id: Int) {
            val button = compose.onNodeWithText(text(id))
            if (!button.isDisplayed()) button.performScrollTo()
            button.performClick()
        }
        fun capture(name: String) {
            compose.waitForIdle()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            File(
                compose.activity.getExternalFilesDir("ui-review"),
                "first-run-$name.png"
            ).outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        }
        compose.waitUntil(15_000) { model.state.value.loaded && model.work.value.ready }
        assertFalse("Use a fresh app sandbox", model.state.value.options.welcomed)
        assertEquals(emptyList<Any>(), model.packages.value)
        capture("welcome")
        tap(R.string.welcome_start)
        compose.onNodeWithText(text(R.string.setup_model_title)).assertIsDisplayed()
        capture("model")
        tap(R.string.download_selected_model)
        compose.waitUntil(90_000) { model.work.value.importComplete && !model.work.value.busy }
        capture("model-saved")
        tap(R.string.setup_next_library)
        compose.onNodeWithText(text(R.string.setup_library_title)).assertIsDisplayed()
        capture("starter")
        tap(R.string.download_starter)
        compose.waitUntil(90_000) { model.work.value.importComplete && !model.work.value.busy }
        tap(R.string.return_setup)
        capture("ready")
        tap(R.string.setup_first_question)
        compose.onNodeWithText(text(R.string.home_title)).assertIsDisplayed()
        capture("home")
        val container = (compose.activity.application as BlobsApplication).container
        for (suggestion in listOf(R.string.question_printing, R.string.question_heat)) {
            assertTrue(
                "A starter suggestion must have locally available sources",
                container.library.retrieve(
                    model.packages.value,
                    RetrievalQuery.resolve(text(suggestion), emptyList()),
                    null
                ).isNotEmpty()
            )
        }
        compose.onNodeWithContentDescription(
            text(R.string.question_hint)
        ).performTextInput("What is photosynthesis?")
        compose.onNodeWithContentDescription(text(R.string.send)).performClick()
        compose.waitUntil(30_000) { model.work.value.evidence != "[]" }
        compose.onNodeWithContentDescription(text(R.string.stop_answer)).performClick()
        compose.waitUntil(15_000) { !model.work.value.busy }
        val source = Evidence.decode(model.work.value.evidence).first()
        compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.read_source, 1, source.title)
        )
            .performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.page == Page.READER }
        capture("source")
        tap(R.string.read_full_source)
        compose.waitUntil(10_000) { model.state.value.readerIsFull }
        capture("full-source")
        tap(R.string.ask_source)
        compose.waitUntil(10_000) { model.state.value.page == Page.CHAT }
        assertEquals(setOf(source.id), model.selectedScope())
    }
}
