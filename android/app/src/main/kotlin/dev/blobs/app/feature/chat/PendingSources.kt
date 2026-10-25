package dev.blobs.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.ui.theme.BlobsDimens

/**
 * Retrieval finishes a minute before the first character of the answer, so the passages the turn
 * will be built from are real, readable objects while the model is still working. They carry the
 * same numbers the answer cites, and tapping one opens the reader on the saved passage.
 */
@Composable
internal fun PendingSources(
    groups: List<TurnSourceGroup>,
    onRead: (Evidence) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BlobsDimens.xs),
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.xxs)
    ) {
        groups.forEach { group ->
            val source = group.evidence
            val label = stringResource(R.string.read_source, group.displayNumber, source.title)
            OutlinedButton(
                onClick = { onRead(source) },
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.semantics { contentDescription = label }
            ) {
                Text(
                    group.displayNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    source.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 184.dp)
                )
            }
        }
    }
}
