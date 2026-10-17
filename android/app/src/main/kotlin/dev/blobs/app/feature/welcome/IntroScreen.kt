package dev.blobs.app.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.ui.PrimaryAction

@Composable
fun IntroScreen(prepare: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Image(
                painterResource(R.drawable.offline_world),
                null,
                Modifier.fillMaxWidth().height(
                    210.dp
                ).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.intro_title),
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                listOf(
                    Triple(
                        R.drawable.ic_edit_square,
                        R.string.intro_ask_title,
                        R.string.intro_ask_body
                    ),
                    Triple(
                        R.drawable.ic_auto_stories,
                        R.string.intro_sources_title,
                        R.string.intro_sources_body
                    ),
                    Triple(
                        R.drawable.ic_check,
                        R.string.intro_offline_title,
                        R.string.intro_offline_body
                    )
                ).forEach { (icon, title, body) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                painterResource(icon),
                                null,
                                Modifier.padding(12.dp).size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            PrimaryAction(R.string.get_ready, onClick = prepare)
        }
    }
}
