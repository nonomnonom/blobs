package dev.blobs.app.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductState
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.SearchField

@Composable
fun BrowseScreen(state: ProductState, model: ProductViewModel) {
    val selecting = state.page == Page.SOURCES
    val selected = model.selectedScope()
    val all = selected == null
    val packages by model.packages.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val collection = packages.find { it.key == state.browseCollection }
        if (!selecting && collection != null) {
            Text(collection.title, style = MaterialTheme.typography.titleLarge)
        }
        SearchField(state.query, R.string.search_sources, onValueChange = {
            model.search(it)
        })
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selecting) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Body(R.string.source_picker_help)
                        Surface(
                            selected = all,
                            onClick = { model.selectScope(null) },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                if (all) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            color = if (all) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.background
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(painterResource(R.drawable.ic_auto_stories), null)
                                Text(
                                    stringResource(R.string.all_sources),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (all) {
                                    Icon(
                                        painterResource(R.drawable.ic_check),
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        val summary = if (all) {
                            stringResource(R.string.individual_sources)
                        } else {
                            pluralStringResource(
                                R.plurals.sources_selected,
                                selected.size,
                                selected.size
                            )
                        }
                        if (LocalDensity.current.fontScale >= 1.5f) {
                            Text(summary, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { model.selectScope(emptySet()) }) {
                                Text(stringResource(R.string.clear_selection))
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    summary,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = { model.selectScope(emptySet()) }) {
                                    Text(stringResource(R.string.clear_selection))
                                }
                            }
                        }
                    }
                }
            }
            if (state.sources.isEmpty() && !state.searching) {
                item {
                    Body(R.string.no_results)
                    Body(R.string.no_results_body)
                    TextButton(onClick = {
                        model.search("")
                    }) { Text(stringResource(R.string.clear_search)) }
                }
            }
            items(state.sources, key = { it.id }) { source ->
                Surface(
                    onClick = {
                        if (selecting) model.toggleSource(source) else model.readFull(source)
                    },
                    shape = RoundedCornerShape(
                        20.dp
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selecting &&
                            selected?.contains(source.id) == true
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    color = if (selecting &&
                        selected?.contains(source.id) == true
                    ) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.background
                    }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (selecting) {
                            Checkbox(
                                checked =
                                    selected?.contains(source.id) == true,
                                onCheckedChange = null
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(source.title, style = MaterialTheme.typography.labelLarge)
                            Text(source.publisher, style = MaterialTheme.typography.bodyMedium)
                            if (source.section.isNotBlank()) {
                                Text(
                                    source.section,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item {
                if (state.offset >
                    0
                ) {
                    TextButton(onClick = {
                        model.search(state.query, (state.offset - 50).coerceAtLeast(0))
                    }) { Text(stringResource(R.string.previous_page)) }
                }
                if (state.hasMore) {
                    TextButton(onClick = {
                        model.search(
                            state.query,
                            state.offset + 50
                        )
                    }) { Text(stringResource(R.string.next_page)) }
                }
            }
        }
        if (selecting) {
            Column(Modifier.padding(bottom = 12.dp)) {
                PrimaryAction(
                    if (all) {
                        stringResource(R.string.done)
                    } else {
                        pluralStringResource(
                            R.plurals.done_sources,
                            selected.size,
                            selected.size
                        )
                    }
                ) { model.back() }
            }
        }
    }
}
