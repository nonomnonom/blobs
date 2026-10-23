package dev.blobs.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.feature.library.ImportScreen
import dev.blobs.app.ui.AppHeader
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.LocalLessMotion
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.theme.BlobsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Download status artboards use explicit layout fixtures; no transfer or inference is started. */
class TransferLayoutCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @ManualCheck
    @Test
    fun captureTransferLayouts() {
        val args = InstrumentationRegistry.getArguments()
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        val asset = model.catalog.models.first()
        val total = asset.downloadBytes
        val base = WorkState(
            ready = true, busy = true, transferActive = true, downloading = true,
            inAppDownload = true, packageKind = "model", importTitle = asset.manifest.title,
            progress = total * 36 / 100, total = total, downloadTotal = total,
            installTotal = asset.manifest.bytes, download = asset
        )
        val variants = listOf(
            "downloading" to base,
            "paused" to base.copy(busy = false, transferActive = false),
            "verifying" to base.copy(progress = total),
            "installing" to
                base.copy(
                    downloading = false,
                    progress = asset.manifest.bytes * 36 / 100,
                    total = asset.manifest.bytes
                ),
            "error" to
                base.copy(busy = false, transferActive = false, notice = R.string.download_failed),
            "done" to
                base.copy(
                    busy = false,
                    transferActive = false,
                    downloading = false,
                    importComplete = true,
                    progress = total
                )
        )
        val folder = requireNotNull(compose.activity.getExternalFilesDir("ui-review"))
        val prefix = args.getString("reviewName") ?: "native"
        val state = model.state.value.copy(page = Page.IMPORT, importOrigin = Page.LIBRARY)
        val theme = if (args.getString("reviewTheme") == "dark") ThemeMode.DARK else ThemeMode.LIGHT
        val scale = args.getString("reviewScale")?.toFloat() ?: 1f
        fun capture(name: String) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            try {
                File(folder, "$prefix-download-$name-fixture.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                bitmap.recycle()
            }
        }
        try {
            for ((name, work) in variants) {
                compose.runOnUiThread {
                    compose.activity.setContent {
                        val density = LocalDensity.current.density
                        CompositionLocalProvider(
                            LocalDensity provides Density(density, scale),
                            LocalLessMotion provides true
                        ) {
                            BlobsTheme(theme) {
                                Surface(
                                    Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                                        AppHeader(state, false, model) { }
                                        ImportScreen(state, work, model)
                                    }
                                }
                            }
                        }
                    }
                }
                compose.waitForIdle()
                capture(name)
                if (compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()) {
                    compose.onAllNodes(hasScrollAction())[0]
                        .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
                    compose.waitForIdle()
                    capture("$name-later")
                }
            }
        } finally {
            compose.activityRule.scenario.recreate()
        }
    }
}
