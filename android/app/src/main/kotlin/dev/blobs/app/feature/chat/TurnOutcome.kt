package dev.blobs.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.local.TurnFinishReason
import dev.blobs.app.data.local.TurnGrounding
import dev.blobs.app.data.local.TurnStatus

@Composable
internal fun TurnOutcome(
    turn: TurnEntity,
    status: TurnStatus?,
    finishReason: TurnFinishReason?,
    grounding: TurnGrounding,
    answer: String,
    hasNoSources: Boolean,
    latest: Boolean,
    busy: Boolean,
    onChooseLibraries: () -> Unit,
    onSearchAll: () -> Unit,
    onRetry: () -> Unit
) {
    if (status == TurnStatus.RUNNING) return
    val limited = status == TurnStatus.COMPLETE &&
        finishReason == TurnFinishReason.TOKEN_LIMIT
    val label = when {
        status == TurnStatus.ERROR && turn.error == "MODEL_TEMPLATE" ->
            R.string.model_incompatible

        status == TurnStatus.ERROR && finishReason == TurnFinishReason.CONTEXT_FULL ->
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
    val trustLabel = when {
        status != TurnStatus.COMPLETE || answer.isBlank() -> null
        hasNoSources -> R.string.status_no_sources
        grounding == TurnGrounding.GROUNDED -> R.string.status_grounded
        else -> R.string.status_unverified
    }
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painterResource(
                if (hasNoSources) R.drawable.ic_auto_stories else R.drawable.ic_blobs_monochrome
            ),
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (trustLabel != null) {
        Text(
            stringResource(trustLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (latest && hasNoSources) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onChooseLibraries) {
                Text(stringResource(R.string.choose_libraries))
            }
            if (turn.scope != "*") {
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
