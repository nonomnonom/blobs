package dev.blobs.app.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.SetupProgressBlock
import dev.blobs.app.ui.theme.BlobsTheme

@Composable
fun WelcomeScreen(preferencesUnavailable: Boolean = false, prepare: () -> Unit = {}) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer)) {
            Image(
                painter = painterResource(R.drawable.welcome_world),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = stringResource(R.string.app_wordmark),
                style = MaterialTheme.typography.displayLarge,
                color = androidx.compose.ui.graphics.Color(0xFF292A32),
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
            )
            Spacer(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
            )
        }
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SetupProgressBlock(0f, R.string.setup_welcome_step)
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            Text(
                text = stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            PrimaryAction(R.string.prepare, onClick = prepare)
            if (preferencesUnavailable) {
                Text(
                    text = stringResource(R.string.preferences_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    BlobsTheme { WelcomeScreen() }
}
