package dev.blobs.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.blobs.app.R
import dev.blobs.app.data.ResearchStage
import dev.blobs.app.ui.LocalLessMotion
import dev.blobs.app.ui.chat.Trace
import dev.blobs.app.ui.chat.TraceLine
import dev.blobs.app.ui.chat.TraceStatus
import dev.blobs.app.ui.theme.BlobsMotion

/**
 * The stages this turn has actually reported, in the order they arrived.
 *
 * Only observed stages are listed. The runtime never replays a stage the UI missed, so a
 * restored or fast turn may show fewer lines rather than a reconstructed history.
 */
@Composable
internal fun ResearchTrace(
    stages: List<ResearchStage>,
    current: ResearchStage?,
    evidenceCount: Int,
    modifier: Modifier = Modifier
) {
    val shown = if (current != null && current !in stages) stages + current else stages
    val lessMotion = LocalLessMotion.current
    Trace(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (shown.isEmpty()) {
            TraceLine(
                status = TraceStatus.RUNNING,
                icon = R.drawable.ic_blobs_monochrome,
                title = stringResource(R.string.working)
            )
        }
        shown.forEach { stage ->
            StageLine(
                stage = stage,
                running = stage == current,
                evidenceCount = evidenceCount,
                lessMotion = lessMotion
            )
        }
    }
}

@Composable
private fun StageLine(
    stage: ResearchStage,
    running: Boolean,
    evidenceCount: Int,
    lessMotion: Boolean
) {
    val line: @Composable () -> Unit = {
        TraceLine(
            status = if (running) TraceStatus.RUNNING else TraceStatus.DONE,
            icon = if (running) stageIcon(stage) else R.drawable.ic_check,
            title = stageLabel(stage, evidenceCount)
        )
    }
    if (lessMotion) {
        line()
    } else {
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(BlobsMotion.QUICK)) +
                slideInVertically(tween(BlobsMotion.QUICK)) { it / 3 }
        ) {
            line()
        }
    }
}

@Composable
private fun stageLabel(stage: ResearchStage, evidenceCount: Int): String = when (stage) {
    ResearchStage.PREPARING_MODEL -> stringResource(R.string.research_preparing)

    ResearchStage.SEARCHING_LIBRARY -> stringResource(R.string.research_searching)

    ResearchStage.REVIEWING_SOURCES -> if (evidenceCount > 0) {
        pluralStringResource(
            R.plurals.research_reviewing,
            evidenceCount,
            evidenceCount
        )
    } else {
        stringResource(R.string.research_checking)
    }

    ResearchStage.WRITING_ANSWER -> stringResource(R.string.research_writing)
}

private fun stageIcon(stage: ResearchStage): Int = when (stage) {
    ResearchStage.SEARCHING_LIBRARY -> R.drawable.ic_search
    ResearchStage.REVIEWING_SOURCES -> R.drawable.ic_auto_stories
    ResearchStage.WRITING_ANSWER -> R.drawable.ic_chat_bubble
    ResearchStage.PREPARING_MODEL -> R.drawable.ic_blobs_monochrome
}
