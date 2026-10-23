package dev.blobs.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.blobs.app.R

@Composable
internal fun ModelLogo(identity: String, size: Dp = 40.dp) {
    val image = when {
        identity.contains("minicpm", ignoreCase = true) -> R.drawable.model_minicpm
        identity.contains("phi-4", ignoreCase = true) -> R.drawable.model_phi
        identity.contains("qwen", ignoreCase = true) -> R.drawable.model_qwen
        else -> null
    }
    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        if (image != null) {
            Image(
                painterResource(image),
                null,
                Modifier.padding(size * 0.15f),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_auto_stories),
                null,
                Modifier.padding(size * 0.2f),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
