package dev.blobs.app.feature.welcome

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.WorldScene
import dev.blobs.app.ui.worldSkyColor
import dev.blobs.app.ui.theme.BlobsComponentShapes
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
fun WelcomeScreen(
    preferencesUnavailable: Boolean = false,
    scroll: ScrollState,
    prepare: () -> Unit = {}
) {
    val large = LocalDensity.current.fontScale >= 1.5f
    val floor = MaterialTheme.colorScheme.background.copy(alpha = .94f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).heightIn(min = height)
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.fillMaxWidth().background(
                    if (large) worldSkyColor() else Color.Transparent,
                    BlobsComponentShapes.worldPanel
                ).padding(horizontal = BlobsDimens.page, vertical = BlobsDimens.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.lg)
            ) {
                Text(
                    stringResource(R.string.app_wordmark),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    stringResource(R.string.welcome_headline),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.welcome_description),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            WorldScene(
                Modifier.fillMaxWidth().then(
                    if (large) {
                        Modifier.height(190.dp)
                    } else {
                        Modifier.weight(1f).heightIn(min = 240.dp)
                    }
                ).padding(bottom = BlobsDimens.lg),
                fullWorld = true,
                size = if (large) 170.dp else 240.dp
            )
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(floor.copy(alpha = 0f), floor))
                )
                    .padding(horizontal = BlobsDimens.page, vertical = BlobsDimens.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
            ) {
                if (preferencesUnavailable) {
                    Text(
                        stringResource(R.string.preferences_unavailable),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                PrimaryAction(R.string.welcome_start, onClick = prepare)
                Text(
                    stringResource(R.string.welcome_footer),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
