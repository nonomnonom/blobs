package dev.blobs.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.blobs.app.R

@Composable
internal fun ModelLogo(identity: String, size: Dp = 40.dp) {
    val image = when {
        identity.contains("gemma", ignoreCase = true) -> R.drawable.model_gemma
        identity.contains("qwen", ignoreCase = true) -> R.drawable.model_qwen
        else -> null
    }
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = Color.White
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
                tint = Color(0xFF292A32)
            )
        }
    }
}
