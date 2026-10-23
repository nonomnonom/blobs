package dev.blobs.app.ui.chat

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.ui.LocalLessMotion
import dev.blobs.app.ui.theme.BlobsDimens
import dev.blobs.app.ui.theme.BlobsMotion

/** The state one step of a turn is in, shared by every step shape in the transcript. */
internal enum class TraceStatus {
    RUNNING,
    DONE,
    ERROR,
    PENDING
}

/** A column of the steps one turn took, each step the same shape. */
@Composable
internal fun Trace(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_TRACE),
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.xxs),
        content = content
    )
}

/**
 * One step line. [status] drives the marker and the colour, never the copy, so a step
 * that has nothing measured yet stays quiet instead of guessing.
 */
@Composable
internal fun TraceLine(
    status: TraceStatus?,
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val label = listOfNotNull(title, description).joinToString(" · ")
    val tint = when (status) {
        TraceStatus.ERROR -> MaterialTheme.colorScheme.error
        TraceStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DIMMED_ALPHA)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.fillMaxWidth().testTag(TAG_TRACE_LINE)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BlobsDimens.xs)
    ) {
        Box(Modifier.size(BlobsDimens.traceGutter), contentAlignment = Alignment.Center) {
            if (status == TraceStatus.RUNNING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    painterResource(icon),
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = tint
                )
            }
        }
        Text(
            title,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            color = when (status) {
                TraceStatus.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DIMMED_ALPHA)
            )
        }
    }
}

/**
 * A step line that opens into what it produced. [open] and [onOpenChange] travel together,
 * so a collapsed step can never be shipped without a way back.
 */
@Composable
internal fun TraceCollapsible(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    line: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val lessMotion = LocalLessMotion.current
    val stateLabel = stringResource(if (open) R.string.expanded else R.string.collapsed)
    val rotation by animateFloatAsState(
        targetValue = if (open) CHEVRON_OPEN else 0f,
        animationSpec = if (lessMotion) snap() else tween(BlobsMotion.QUICK),
        label = "trace-chevron"
    )
    Column(modifier.fillMaxWidth().testTag(TAG_TRACE_COLLAPSIBLE)) {
        Surface(
            onClick = { onOpenChange(!open) },
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = BlobsDimens.touchTarget)
                    .semantics { stateDescription = stateLabel },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) { line() }
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    null,
                    modifier = Modifier.padding(start = BlobsDimens.xs).size(18.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (lessMotion) {
            if (open) TraceCollapsibleBody(content = content)
        } else {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(tween(BlobsMotion.QUICK)),
                exit = shrinkVertically(tween(BlobsMotion.QUICK))
            ) {
                TraceCollapsibleBody(content = content)
            }
        }
    }
}

@Composable
private fun TraceCollapsibleBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(top = BlobsDimens.xxs, start = BlobsDimens.traceGutter),
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.xxs),
        content = content
    )
}

private const val TAG_TRACE = "trace"
private const val TAG_TRACE_LINE = "trace-line"
private const val TAG_TRACE_COLLAPSIBLE = "trace-collapsible"
private const val DIMMED_ALPHA = 0.6f
private const val CHEVRON_OPEN = 90f
