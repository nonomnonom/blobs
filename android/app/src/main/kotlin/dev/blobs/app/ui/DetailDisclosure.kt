package dev.blobs.app.ui

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.ui.theme.BlobsDimens
import dev.blobs.app.ui.theme.BlobsMotion

/**
 * The app's only disclosure shape: a summary line the reader can scan, and the
 * explanation behind it. [open] and [onOpenChange] travel together so a collapsed
 * detail can never ship without a way back.
 */
@Composable
internal fun DetailDisclosure(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    summary: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    bodyIndent: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val lessMotion = LocalLessMotion.current
    val stateLabel = stringResource(if (open) R.string.expanded else R.string.collapsed)
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = if (lessMotion) snap() else tween(BlobsMotion.QUICK),
        label = "disclosure-chevron"
    )
    Column(modifier.fillMaxWidth().testTag(TAG_DISCLOSURE)) {
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
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    content = summary
                )
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
            if (open) DisclosureBody(bodyIndent, content)
        } else {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(tween(BlobsMotion.QUICK)),
                exit = shrinkVertically(tween(BlobsMotion.QUICK))
            ) {
                DisclosureBody(bodyIndent, content)
            }
        }
    }
}

@Composable
private fun DisclosureBody(indent: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(top = BlobsDimens.xxs, start = indent),
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.xxs),
        content = content
    )
}

private const val TAG_DISCLOSURE = "detail-disclosure"
