package dev.blobs.app.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import kotlinx.coroutines.delay

@Composable
internal fun TurnActions(
    turnId: String,
    answer: String,
    modelName: String
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    var copied by rememberSaveable(turnId) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    if (answer.isNotBlank()) {
        TextButton(
            onClick = {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(appName, answer))
                copied = true
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Icon(
                painterResource(R.drawable.ic_content_copy),
                null,
                Modifier.padding(end = 8.dp).size(20.dp)
            )
            Text(stringResource(if (copied) R.string.copied else R.string.copy_answer))
        }
    }
    Text(
        modelName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
