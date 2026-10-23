package dev.blobs.app.feature.chat

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.blobs.app.R
import dev.blobs.app.data.TurnResponse
import dev.blobs.app.data.local.TurnGrounding
import dev.blobs.app.inference.ModelSupport
import dev.blobs.app.ui.chat.TraceCollapsible
import dev.blobs.app.ui.chat.TraceLine
import dev.blobs.app.ui.chat.TraceStatus

/**
 * The proof behind one answer, shown in the shape of a chain of thought.
 *
 * Blobs runs one local completion, so there is no model reasoning to display and none is
 * invented here. Each line is something the runtime recorded for this turn: the scope it
 * searched, how many passages came back, how long the whole turn took, how many of those
 * passages carry a marker in the prose, and the support claim the model itself made. The
 * grounding verdict closes the chain, because it is the only claim here the app can prove.
 */
@Composable
internal fun GroundingChain(response: TurnResponse, modifier: Modifier = Modifier) {
    var open by rememberSaveable(response.turnId) { mutableStateOf(false) }
    val grounded = response.grounding == TurnGrounding.GROUNDED
    val verdictLabel = when (response.grounding) {
        TurnGrounding.GROUNDED -> R.string.status_grounded
        TurnGrounding.UNVERIFIED -> R.string.status_unverified
        else -> null
    }
    val steps = chainSteps(response, ((response.usage.elapsedMs + 500) / 1000).toInt())
    TraceCollapsible(
        open = open,
        onOpenChange = { open = it },
        modifier = modifier,
        line = {
            TraceLine(
                status = if (grounded) TraceStatus.DONE else TraceStatus.PENDING,
                icon = R.drawable.ic_blobs_monochrome,
                title = stringResource(R.string.chain_title)
            )
        }
    ) {
        steps.forEach { step ->
            TraceLine(status = step.status, icon = step.icon, title = step.title)
        }
        if (verdictLabel != null) {
            TraceLine(
                status = if (grounded) TraceStatus.DONE else TraceStatus.PENDING,
                icon = if (grounded) R.drawable.ic_check else R.drawable.ic_close,
                title = stringResource(verdictLabel)
            )
        }
    }
}

private class ChainStep(val status: TraceStatus, @DrawableRes val icon: Int, val title: String)

@Composable
private fun chainSteps(response: TurnResponse, seconds: Int): List<ChainStep> {
    val usage = response.usage
    val searched = searchedLabel(response.scope)
    val retrieved = if (usage.sourceCount > 0) {
        pluralStringResource(R.plurals.chain_retrieved, usage.sourceCount, usage.sourceCount)
    } else {
        null
    }
    val ready = if (seconds > 0) stringResource(R.string.chain_ready, seconds) else null
    val cited = pluralStringResource(
        R.plurals.chain_cited,
        usage.citedSourceCount,
        usage.citedSourceCount
    )
    return listOfNotNull(
        searched?.let { ChainStep(TraceStatus.DONE, R.drawable.ic_search, it) },
        retrieved?.let { ChainStep(TraceStatus.DONE, R.drawable.ic_folder_open, it) },
        ready?.let { ChainStep(TraceStatus.DONE, R.drawable.ic_chat_bubble, it) },
        ChainStep(
            if (usage.citedSourceCount > 0) TraceStatus.DONE else TraceStatus.PENDING,
            R.drawable.ic_auto_stories,
            cited
        ),
        ChainStep(
            if (response.modelSupport == ModelSupport.SOURCED) {
                TraceStatus.DONE
            } else {
                TraceStatus.PENDING
            },
            R.drawable.ic_edit_square,
            stringResource(claimLabel(response.modelSupport))
        )
    )
}

/** A legacy turn recorded no scope, so this chain says nothing about one. */
@Composable
private fun searchedLabel(scope: String): String? = when {
    scope.isBlank() -> null

    scope == "*" -> stringResource(R.string.chain_searched_all)

    else -> {
        val selected = scope.lineSequence().filter(String::isNotBlank).count()
        pluralStringResource(R.plurals.chain_searched_some, selected, selected)
    }
}

@StringRes
private fun claimLabel(claimed: ModelSupport?): Int = when (claimed) {
    ModelSupport.SOURCED -> R.string.chain_claim_sourced
    ModelSupport.PARTIAL -> R.string.chain_claim_partial
    ModelSupport.UNSOURCED -> R.string.chain_claim_unsourced
    null -> R.string.chain_claim_none
}
