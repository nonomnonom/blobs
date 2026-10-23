package dev.blobs.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.blobs.app.R
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.ui.SourceRow
import dev.blobs.app.ui.chat.TraceCollapsible
import dev.blobs.app.ui.chat.TraceLine
import dev.blobs.app.ui.chat.TraceStatus
import dev.blobs.app.ui.theme.BlobsDimens

/**
 * The passages a turn actually returned, opened from one step line so an answer does not
 * have to carry its evidence list until the reader asks for it.
 */
@Composable
internal fun TurnSources(
    turnId: String,
    groups: List<TurnSourceGroup>,
    citedSourceIndices: Set<Int>,
    onRead: (Evidence) -> Unit
) {
    var expanded by rememberSaveable(turnId) { mutableStateOf(false) }
    val citedGroups = groups.filter { group ->
        group.sourceIndices.any(citedSourceIndices::contains)
    }
    val visibleGroups = if (citedGroups.isNotEmpty()) citedGroups else groups
    if (visibleGroups.isEmpty()) return
    TraceCollapsible(
        open = expanded,
        onOpenChange = { expanded = it },
        line = {
            TraceLine(
                status = if (citedGroups.isNotEmpty()) {
                    TraceStatus.DONE
                } else {
                    TraceStatus.PENDING
                },
                icon = R.drawable.ic_auto_stories,
                title = stringResource(
                    if (citedGroups.isNotEmpty()) {
                        R.string.sources_count
                    } else {
                        R.string.related_sources_count
                    },
                    visibleGroups.size
                )
            )
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.xxs)
        ) {
            visibleGroups.forEach { group ->
                val source = group.evidence
                val metadata = listOf(source.publisher, source.section)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" · ")
                SourceRow(
                    group.displayNumber,
                    source.title,
                    stringResource(
                        R.string.read_source,
                        group.displayNumber,
                        source.title
                    ),
                    metadata = metadata,
                    detail = source.text,
                    onClick = { onRead(source) }
                )
            }
        }
    }
}
