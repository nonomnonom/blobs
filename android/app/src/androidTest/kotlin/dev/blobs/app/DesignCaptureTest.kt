package dev.blobs.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Page
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Optional native layout capture using verified local packages, without running inference. */
class DesignCaptureTest {
    @get:Rule(order = 0)
    val fontScale = SystemFontScaleRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @ManualCheck
    @Test
    fun captureImplementedScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val files = listOf("reviewModel", "reviewLibrary").map { args.getString(it)?.let(::File) }
        assumeTrue("Supply local model and starter package paths", files.all { it?.isFile == true })
        val container = (compose.activity.application as BlobsApplication).container
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val folder = requireNotNull(compose.activity.getExternalFilesDir("ui-review"))
        val prefix = args.getString("reviewName") ?: "native"
        fun text(id: Int) = compose.activity.getString(id)
        fun capture(name: String, waitForMarker: Boolean = true) {
            val marker = when (name) {
                "welcome" -> text(R.string.welcome_headline)
                "setup-model" -> text(R.string.setup_model_title)
                "setup-library" -> text(R.string.setup_library_title)
                "setup-ready" -> text(R.string.setup_ready_title)
                "home" -> text(R.string.home_title)
                "navigation" -> text(R.string.search_conversations)
                "library" -> text(R.string.your_collections)
                "discover" -> text(R.string.discover_heading)
                "sources" -> text(R.string.library_scope_help)
                "browse", "sources-selected", "sources-all" -> text(R.string.search_sources)
                "reader" -> model.state.value.reader?.title.orEmpty()
                "import-review" -> text(R.string.review_package)
                "import-complete" -> text(R.string.import_ready_title)
                "conversation-fixture" -> "How do sources stay with an answer?"
                "working-fixture" -> text(R.string.working)
                "history" -> text(R.string.search_history)
                "settings" -> text(R.string.appearance)
                "appearance" -> text(R.string.theme_dark)
                "storage" -> text(R.string.storage_body)
                "data" -> text(R.string.data_heading)
                "privacy" -> text(R.string.privacy_heading)
                "help" -> text(R.string.help_heading)
                "about" -> text(R.string.about_how)
                else -> text(R.string.models)
            }
            if (waitForMarker) {
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText(marker).fetchSemanticsNodes().isNotEmpty()
                }
            }
            compose.waitForIdle()
            instrumentation.uiAutomation.waitForIdle(100, 3000)
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(folder, "$prefix-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            if (name in setOf(
                    "welcome", "setup-model", "setup-library", "setup-ready", "home",
                    "library", "discover", "reader", "settings",
                    "appearance", "privacy", "help", "about", "storage", "data"
                ) &&
                compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
            ) {
                compose.onAllNodes(
                    hasScrollAction()
                )[0].performSemanticsAction(SemanticsActions.ScrollBy) {
                    it(0f, 10_000f)
                }
                capture("$name-later", waitForMarker = false)
            }
        }
        fun page(page: Page) {
            compose.runOnUiThread { model.navigate(page) }
            compose.waitForIdle()
        }
        compose.waitUntil(10_000) { model.state.value.loaded && model.work.value.ready }
        fontScale.set(args.getString("reviewScale")?.toFloat() ?: 1f)
        runBlocking {
            container.preferences.welcome()
            container.preferences.setReducedMotion(true)
            container.preferences.setThemeMode(
                if (args.getString("reviewTheme") == "dark") ThemeMode.DARK else ThemeMode.LIGHT
            )
            files.filterNotNull().forEach { file ->
                val uri = Uri.fromFile(file)
                val manifest = container.installer.inspect(uri)
                if (container.database.packages().all().none { it.key == manifest.key }) {
                    container.installer.install(uri, manifest) {}
                }
            }
            val selected = container.database.packages().all().first { it.kind == "model" }
            container.preferences.selectModel(selected.key)
        }
        compose.waitUntil(10_000) {
            model.selectedModel() != null && model.catalog.hasStarterLibrary(model.packages.value)
        }
        page(Page.WELCOME)
        capture("welcome")
        compose.runOnUiThread { model.welcome() }
        compose.waitForIdle()
        // Existing packages start at ready. Back exposes the earlier setup steps for review.
        compose.runOnUiThread {
            model.back()
            model.back()
        }
        capture("setup-model")
        compose.runOnUiThread {
            model.continueSetup()
            model.back()
        }
        capture("setup-library")
        compose.runOnUiThread { model.continueSetup() }
        capture("setup-ready")
        compose.runOnUiThread { model.finishSetup() }
        val beforeHome = model.conversationId.value
        compose.runOnUiThread { model.newConversation() }
        compose.waitUntil(10_000) { model.conversationId.value != beforeHome }
        capture("home")
        compose.onNodeWithContentDescription(text(R.string.open_navigation)).performClick()
        capture("navigation")
        compose.onNodeWithText(text(R.string.library)).performClick()
        capture("library")
        compose.onNodeWithText(text(R.string.discover_more)).performClick()
        capture("discover")
        for (id in listOf(
            "wikivoyage-en",
            "nps-outdoor-safety",
            "weather-safety",
            "earthquake-tsunami-safety",
            "emergency-food",
            "practical-living"
        )) {
            val asset = model.catalog.additionalLibraries.find { it.manifest.id == id } ?: continue
            val saved = model.packages.value.any { it.key == asset.manifest.key }
            compose.onNodeWithText(
                text(if (saved) R.string.your_collections else R.string.discover_more)
            ).performClick()
            val section = if (saved) "saved" else "discover"
            compose.onNodeWithText(asset.manifest.title).performScrollTo()
            capture("$section-$id", waitForMarker = false)
            compose.onNodeWithContentDescription(
                compose.activity.getString(R.string.library_details_for, asset.manifest.title)
            ).performScrollTo().performClick()
            capture("$section-$id-credits", waitForMarker = false)
            compose.onAllNodes(hasScrollAction())[0]
                .performSemanticsAction(SemanticsActions.ScrollBy) {
                    it(0f, 280 * compose.activity.resources.displayMetrics.density)
                }
            capture("$section-$id-credits-later", waitForMarker = false)
        }
        compose.onNodeWithText(text(R.string.your_collections)).performClick()
        compose.onAllNodesWithText(text(R.string.browse_collection))[0]
            .performScrollTo().performClick()
        compose.waitUntil(10_000) { !model.state.value.searching }
        capture("browse")
        val source = model.state.value.sources.first()
        compose.runOnUiThread { model.read(source) }
        capture("reader")
        val beforeChat = model.conversationId.value
        compose.runOnUiThread { model.newConversation() }
        compose.waitUntil(10_000) { model.conversationId.value != beforeChat }
        val thread = model.conversationId.value
        val selected = requireNotNull(model.selectedModel())
        val turn = TurnEntity(
            "review-$thread", thread, "How do sources stay with an answer?",
            "A saved passage stays with this answer [1]. This is a layout review fixture.",
            "COMPLETE", "${selected.title} · ${selected.version} · ${selected.sha256}",
            "*", Evidence.encode(listOf(source)), System.currentTimeMillis()
        )
        try {
            runBlocking { container.database.conversations().saveTurn(turn) }
            compose.waitUntil(10_000) { model.turns.value.any { it.id == turn.id } }
            capture("conversation-fixture")
            runBlocking {
                container.database.conversations().saveTurn(
                    turn.copy(answer = "", status = "RUNNING")
                )
            }
            compose.waitUntil(10_000) { model.turns.value.any { it.status == "RUNNING" } }
            capture("working-fixture")
            runBlocking { container.database.conversations().saveTurn(turn) }
            for (destination in listOf(
                Page.SOURCES, Page.HISTORY, Page.MODELS, Page.SETTINGS, Page.APPEARANCE,
                Page.STORAGE, Page.DATA, Page.PRIVACY, Page.HELP, Page.ABOUT
            )) {
                if (destination == Page.SOURCES) {
                    runBlocking { container.preferences.selectScope("*") }
                    compose.waitUntil(10_000) { model.selectedScope() == null }
                }
                page(destination)
                capture(destination.name.lowercase())
                if (destination == Page.SOURCES) {
                    val library = model.packages.value.first {
                        it.kind == "library" && it.enabled
                    }
                    compose.onNodeWithText(library.title).performScrollTo().performClick()
                    compose.waitUntil(10_000) {
                        model.selectedLibraryScope() == setOf(library.key)
                    }
                    capture("sources-selected", waitForMarker = false)
                    compose.onNodeWithText(text(R.string.all_libraries))
                        .performScrollTo().performClick()
                    compose.waitUntil(10_000) { model.selectedScope() == null }
                    capture("sources-all", waitForMarker = false)
                }
            }
            page(Page.LIBRARY)
            val fixture = File(compose.activity.cacheDir, "layout-review.blobs")
            instrumentation.context.assets.open("corpus-fixture.blobs").use { input ->
                fixture.outputStream().use { input.copyTo(it) }
            }
            val manifest = runBlocking { container.installer.inspect(Uri.fromFile(fixture)) }
            try {
                compose.runOnUiThread { model.inspect(Uri.fromFile(fixture)) }
                compose.waitUntil(10_000) { model.state.value.manifest != null }
                capture("import-review")
                compose.onNodeWithText(text(R.string.add)).performClick()
                compose.waitUntil(10_000) {
                    model.work.value.importComplete &&
                        !model.work.value.busy
                }
                capture("import-complete")
                compose.runOnUiThread { model.finishImport() }
            } finally {
                runBlocking {
                    container.database.packages().all().find { it.key == manifest.key }?.let {
                        container.installer.remove(it)
                    }
                }
                fixture.delete()
            }
        } finally {
            runBlocking {
                container.database.conversations().delete(thread)
                container.preferences.setThemeMode(ThemeMode.SYSTEM)
                container.preferences.setReducedMotion(false)
            }
        }
    }
}
