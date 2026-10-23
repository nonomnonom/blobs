package dev.blobs.app.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.blobs.app.R
import dev.blobs.app.data.ResearchStage
import dev.blobs.app.ui.chat.Trace
import dev.blobs.app.ui.chat.TraceLine
import dev.blobs.app.ui.chat.TraceStatus

/**
 * The one local research stage the runtime can prove it is in.
 *
 * Stages already passed are not reconstructed here: the runtime reports the current stage
 * only, so a turn in flight shows a single live line instead of a step history the app
 * never measured.
 */
@Composable
internal fun ResearchStep(
    stage: ResearchStage?,
    evidenceCount: Int,
    modifier: Modifier = Modifier
) {
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
    Trace(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        TraceLine(status = TraceStatus.RUNNING, icon = icon, title = label)
    }
}
