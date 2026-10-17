package dev.blobs.app.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductViewModel

@Composable
internal fun HomeContent(ready: Boolean, busy: Boolean, model: ProductViewModel) {
    val large = LocalDensity.current.fontScale >= 1.5f
    Column(
        Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
            Image(
                painterResource(R.drawable.discovery_world),
                null,
                Modifier.fillMaxWidth().height(if (large) 260.dp else 200.dp),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
            Text(
                stringResource(R.string.home_title),
                Modifier.padding(24.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF292A32)
            )
        }
        if (ready) {
            Text(
                stringResource(R.string.home_questions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listOf(
                R.string.question_printing to R.drawable.welcome_world,
                R.string.question_heat to R.drawable.discovery_world
            ).forEach { (questionId, artwork) ->
                val question = stringResource(questionId)
                Surface(
                    onClick = { model.send(question) },
                    enabled = !busy,
                    shape = RoundedCornerShape(20.dp),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!large) {
                            Image(
                                painterResource(artwork),
                                null,
                                Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(
                            question,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            painterResource(R.drawable.ic_arrow_forward),
                            null,
                            Modifier.size(20.dp)
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.offline_ready),
                Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                stringResource(R.string.setup_missing),
                style = MaterialTheme.typography.titleLarge
            )
            Body(R.string.setup_missing_body)
            PrimaryAction(R.string.setup_library) { model.navigate(Page.SETUP) }
        }
    }
}
