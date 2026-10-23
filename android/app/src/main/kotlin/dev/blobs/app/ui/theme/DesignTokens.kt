package dev.blobs.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal object BlobsDimens {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val page = 24.dp
    val touchTarget = 48.dp
    val primaryAction = 52.dp
    val homeReadingWidth = 720.dp
    val answerReadingWidth = 900.dp
    val twoPaneConversationWidth = 900.dp
    val appContentWidth = 1200.dp
    val drawerWidth = 360.dp
    val historyPaneMinWidth = 264.dp
    val historyPaneMaxWidth = 304.dp
    val userMessageWidth = 640.dp
    val inlineCitationTarget = 48.dp
    val inlineCitationVisual = 28.dp
    val setupProgressSegmentWidth = 32.dp
    val setupProgressSegmentHeight = 4.dp
}

internal object BlobsMotion {
    const val quick = 160
    const val standard = 220
}

internal object BlobsComponentShapes {
    val drawer = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    val userMessage = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomEnd = 4.dp,
        bottomStart = 24.dp
    )
    val worldPanel = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
}
