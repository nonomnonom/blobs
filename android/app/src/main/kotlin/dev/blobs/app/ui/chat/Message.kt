package dev.blobs.app.ui.chat

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blobs.app.ui.theme.BlobsComponentShapes
import dev.blobs.app.ui.theme.BlobsDimens

/** Which side of the transcript a turn belongs to. */
internal enum class MessageAlign {
    Start,
    End
}

/** One turn: the person's question and the answer that follows it. */
@Composable
internal fun Message(
    modifier: Modifier = Modifier,
    align: MessageAlign = MessageAlign.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_MESSAGE),
        horizontalAlignment = if (align == MessageAlign.End) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
        content = content
    )
}

/** Who answered, kept as a line of its own so a turn can be identified while scanning. */
@Composable
internal fun MessageHeader(@DrawableRes avatar: Int, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(TAG_MESSAGE_HEADER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BlobsDimens.xs)
    ) {
        Image(
            painterResource(avatar),
            null,
            modifier = Modifier.size(28.dp)
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** The reading column of a turn: answer, steps, evidence, and outcomes stack in here. */
@Composable
internal fun MessageContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_MESSAGE_CONTENT),
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
        content = content
    )
}

/** The actions and state that belong to a finished turn. */
@Composable
internal fun MessageFooter(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(TAG_MESSAGE_FOOTER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BlobsDimens.xs),
        content = content
    )
}

/** The person's own words, separated from a model answer by shape and side. */
@Composable
internal fun Bubble(
    modifier: Modifier = Modifier,
    align: MessageAlign = MessageAlign.Start,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(TAG_BUBBLE),
        horizontalArrangement = if (align == MessageAlign.End) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Surface(
            modifier = Modifier.widthIn(max = BlobsDimens.userMessageWidth),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = BlobsComponentShapes.userMessage
        ) {
            content()
        }
    }
}

/** Selectable question text inside a [Bubble]. */
@Composable
internal fun BubbleContent(text: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Text(
            text,
            modifier = modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private const val TAG_MESSAGE = "message"
private const val TAG_MESSAGE_HEADER = "message-header"
private const val TAG_MESSAGE_CONTENT = "message-content"
private const val TAG_MESSAGE_FOOTER = "message-footer"
private const val TAG_BUBBLE = "bubble"
