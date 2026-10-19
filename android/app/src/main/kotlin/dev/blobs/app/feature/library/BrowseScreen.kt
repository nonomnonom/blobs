package dev.blobs.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Heading(if (selecting) R.string.sources else R.string.browse)
        SearchField(state.query, R.string.search_sources, onValueChange = {
            model.search(it)
        })
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selecting) {
                item {
                    Body(
                        if (state.options.scope == "*") {
                            R.string.narrow_help
                        } else {
                            R.string.source_picker_help
                        }
                    )
                    TextButton(onClick = { model.selectScope(null) }) {
                        Text(stringResource(R.string.all_sources))
                    }
                    TextButton(onClick = { model.selectScope(emptySet()) }) {
                        Text(stringResource(R.string.clear_selection))
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
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (selecting) {
                            Checkbox(
                                checked =
                                    model.selectedScope()?.contains(source.id) == true,
                                onCheckedChange = null
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(source.title, style = MaterialTheme.typography.labelLarge)
                            Text(source.publisher, style = MaterialTheme.typography.bodyMedium)
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
                PrimaryAction(R.string.done) { model.back() }
            }
        }
    }
}
