package dev.blobs.app.ui.chat

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blobs.app.ui.DetailDisclosure
import dev.blobs.app.ui.theme.BlobsDimens

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
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(BlobsDimens.xs)
    ) {
        Box(
            Modifier.size(BlobsDimens.traceGutter).padding(top = 3.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (status == TraceStatus.RUNNING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = when (status) {
                    TraceStatus.ERROR -> MaterialTheme.colorScheme.error
                    TraceStatus.RUNNING -> MaterialTheme.colorScheme.onSurface
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
}

@Composable
internal fun TraceCollapsible(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    line: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DetailDisclosure(
        open = open,
        onOpenChange = onOpenChange,
        summary = { line() },
        modifier = modifier.testTag(TAG_TRACE_COLLAPSIBLE),
        bodyIndent = BlobsDimens.traceGutter,
        content = content
    )
}

private const val TAG_TRACE = "trace"
private const val TAG_TRACE_LINE = "trace-line"
private const val TAG_TRACE_COLLAPSIBLE = "trace-collapsible"
private const val DIMMED_ALPHA = 0.6f
