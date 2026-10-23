package dev.blobs.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.ui.SourceRow
import dev.blobs.app.ui.theme.BlobsDimens

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
    val stateLabel = stringResource(
        if (expanded) R.string.expanded else R.string.collapsed
    )

    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.semantics {
            stateDescription = stateLabel
        }
    ) {
        Icon(
            painterResource(R.drawable.ic_auto_stories),
            null,
            Modifier.padding(end = 6.dp).size(18.dp)
        )
        Text(
            stringResource(
                if (citedGroups.isNotEmpty()) {
                    R.string.sources_count
                } else {
                    R.string.related_sources_count
                },
                visibleGroups.size
            )
        )
        Icon(
            painterResource(R.drawable.ic_expand_more),
            null,
            Modifier.padding(start = 6.dp).size(18.dp).rotate(if (expanded) 180f else 0f)
        )
    }
    if (expanded) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                Modifier.padding(horizontal = BlobsDimens.md),
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
}
