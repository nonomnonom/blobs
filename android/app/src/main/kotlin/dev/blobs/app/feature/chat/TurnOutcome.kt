package dev.blobs.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.TurnResponse
import dev.blobs.app.data.local.TurnFinishReason
import dev.blobs.app.data.local.TurnStatus
import dev.blobs.app.ui.chat.Trace
import dev.blobs.app.ui.chat.TraceLine
import dev.blobs.app.ui.chat.TraceStatus

/**
 * Where one finished turn landed, and the chain that proves it.
 *
 * The lifecycle line stays separate from grounding on purpose: a stopped, limited, or
 * ungrounded answer is still an answer the reader can use.
 */
@Composable
internal fun TurnOutcome(
    response: TurnResponse,
    latest: Boolean,
    busy: Boolean,
    onChooseLibraries: () -> Unit,
    onSearchAll: () -> Unit,
    onRetry: () -> Unit
) {
    val status = response.status
    if (status == TurnStatus.RUNNING) return
    val answer = response.body
    val limited = status == TurnStatus.COMPLETE &&
        response.finishReason == TurnFinishReason.TOKEN_LIMIT
    val label = when {
        status == TurnStatus.ERROR && response.error == "MODEL_TEMPLATE" ->
            R.string.model_incompatible

        status == TurnStatus.ERROR && response.finishReason == TurnFinishReason.CONTEXT_FULL ->
            R.string.context_full

        limited || status == TurnStatus.LIMITED -> R.string.status_limited

        status == TurnStatus.STOPPED -> if (answer.isBlank()) {
            R.string.status_stopped_empty
        } else {
            R.string.status_stopped
        }

        status == TurnStatus.INTERRUPTED -> if (answer.isBlank()) {
            R.string.status_interrupted_empty
        } else {
            R.string.status_interrupted
        }

        status == TurnStatus.COMPLETE -> R.string.status_complete

        status == TurnStatus.CONTEXT_FULL -> R.string.context_full

        status == TurnStatus.NO_SOURCES -> R.string.status_no_sources

        status == TurnStatus.UNVERIFIED -> R.string.status_unverified

        else -> R.string.status_error
    }
    val seconds = ((response.usage.elapsedMs + 500) / 1000).toInt()
    val measure = when {
        status != TurnStatus.COMPLETE || limited -> null

        response.usage.sourceCount > 0 -> pluralStringResource(
            R.plurals.turn_passages,
            response.usage.sourceCount,
            response.usage.sourceCount,
            response.usage.citedSourceCount,
            seconds
        )

        else -> stringResource(R.string.turn_elapsed, seconds)
    }
    Trace(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        TraceLine(
            status = outcomeStatus(status, limited),
            icon = outcomeIcon(status, limited),
            title = stringResource(label),
            description = measure
        )
    }
    if (response.evidence != null && answer.isNotBlank()) {
        GroundingChain(response)
    }
    if (latest && response.hasNoSources) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onChooseLibraries) {
                Text(stringResource(R.string.choose_libraries))
            }
            if (response.scope != "*") {
                TextButton(onClick = onSearchAll, enabled = !busy) {
                    Text(stringResource(R.string.search_all))
                }
            }
        }
    } else if (latest && (
            limited || status !in setOf(
                TurnStatus.COMPLETE,
                TurnStatus.RUNNING
            )
            )
    ) {
        Button(onClick = onRetry, enabled = !busy) {
            Text(stringResource(R.string.retry))
        }
    }
}

/**
 * A degraded answer stays dimmed rather than red: the reader still has a usable answer,
 * and only a turn that could not produce one borrows the error role.
 */
private fun outcomeStatus(status: TurnStatus?, limited: Boolean): TraceStatus = when {
    limited -> TraceStatus.PENDING
    status == TurnStatus.COMPLETE -> TraceStatus.DONE
    status != null && status in partialOutcomes -> TraceStatus.PENDING
    else -> TraceStatus.ERROR
}

private fun outcomeIcon(status: TurnStatus?, limited: Boolean): Int = when {
    limited || status == TurnStatus.STOPPED -> R.drawable.ic_stop
    status == TurnStatus.COMPLETE -> R.drawable.ic_check
    status != null && status in partialOutcomes -> R.drawable.ic_auto_stories
    else -> R.drawable.ic_close
}

private val partialOutcomes = setOf(
    TurnStatus.STOPPED,
    TurnStatus.NO_SOURCES,
    TurnStatus.UNVERIFIED
)
