package dev.blobs.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.ResearchStage

@Composable
internal fun ResearchActivity(stage: ResearchStage?, evidenceCount: Int) {
    val label = when (stage) {
        ResearchStage.PREPARING_MODEL -> stringResource(R.string.research_preparing)

        ResearchStage.SEARCHING_LIBRARY -> stringResource(R.string.research_searching)

        ResearchStage.REVIEWING_SOURCES -> pluralStringResource(
            R.plurals.research_reviewing,
            evidenceCount,
            evidenceCount
        )

        ResearchStage.WRITING_ANSWER -> stringResource(R.string.research_writing)

        null -> stringResource(R.string.working)
    }
    val icon = when (stage) {
        ResearchStage.SEARCHING_LIBRARY -> R.drawable.ic_search
        ResearchStage.REVIEWING_SOURCES -> R.drawable.ic_auto_stories
        ResearchStage.WRITING_ANSWER -> R.drawable.ic_chat_bubble
        ResearchStage.PREPARING_MODEL, null -> R.drawable.ic_blobs_monochrome
    }
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painterResource(icon),
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
