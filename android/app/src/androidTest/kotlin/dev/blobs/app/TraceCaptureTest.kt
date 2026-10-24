package dev.blobs.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.ResearchStage
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.feature.chat.TurnCard
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.LocalLessMotion
import dev.blobs.app.ui.theme.BlobsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Live research trace artboards: fixture turn, progressive stages, no inference. */
class TraceCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @ManualCheck
    @Test
    fun captureLiveTrace() {
        val args = InstrumentationRegistry.getArguments()
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { model.state.value.loaded }
        val folder = requireNotNull(compose.activity.getExternalFilesDir("ui-review"))
        val prefix = args.getString("reviewName") ?: "native"
        val theme = if (args.getString("reviewTheme") == "dark") ThemeMode.DARK else ThemeMode.LIGHT
        val scale = args.getString("reviewScale")?.toFloat() ?: 1f
        val stage = mutableStateOf<ResearchStage?>(null)
        val answer = mutableStateOf("")
        val evidence = (1..3).map { index ->
            Evidence(
                id = "trace-$index",
                title = "Wikivoyage",
                publisher = "Wikivoyage",
                url = "https://en.wikivoyage.org/wiki/Stay_safe",
                license = "CC BY-SA 4.0",
                updated = "2026-06",
                saved = "2026-10-24",
                section = "Stay safe",
                text = "Layout fixture passage $index.",
                edition = "2026-06",
                passageId = index.toLong()
            )
        }
        val turn = TurnEntity(
            "trace-fixture", "trace-thread", "How do sources stay with an answer?",
            "", "RUNNING", "Layout fixture", "*", Evidence.encode(evidence),
            System.currentTimeMillis()
        )
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            try {
                File(folder, "$prefix-trace-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                bitmap.recycle()
            }
        }
        try {
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
                                Column(Modifier.fillMaxSize().padding(vertical = 24.dp)) {
                                    TurnCard(
                                        turn = turn,
                                        answer = answer.value,
                                        busy = true,
                                        model = model,
                                        researchStage = stage.value,
                                        latest = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            capture("waiting")
            for ((name, next) in listOf(
                "preparing" to ResearchStage.PREPARING_MODEL,
                "searching" to ResearchStage.SEARCHING_LIBRARY,
                "reviewing" to ResearchStage.REVIEWING_SOURCES,
                "writing" to ResearchStage.WRITING_ANSWER
            )) {
                compose.runOnUiThread { stage.value = next }
                capture(name)
            }
            compose.runOnUiThread {
                answer.value = "Sources stay attached because every passage is saved with the " +
                    "turn, so the reader can reopen it later [1]."
            }
            capture("streaming")
        } finally {
            compose.activityRule.scenario.recreate()
        }
    }
}
