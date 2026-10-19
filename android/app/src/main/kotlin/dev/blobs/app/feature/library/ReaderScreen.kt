package dev.blobs.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductViewModel

@Composable
fun ReaderScreen(source: Evidence?, model: ProductViewModel) {
    if (source == null) return
    val packages by model.packages.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val available = packages.any { source.id.startsWith(it.key + "/") }
    val paragraphs = remember(source.text) {
        source.text.split(Regex("\\n\\s*\\n")).flatMap { paragraph ->
            buildList {
                var remaining = paragraph
                while (remaining.length > 2400) {
                    val end = remaining.lastIndexOf(' ', 2400).takeIf { it > 0 } ?: 2400
                    add(remaining.substring(0, end))
                    remaining = remaining.substring(end).trimStart()
                }
                if (remaining.isNotBlank()) add(remaining)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(source.title, style = MaterialTheme.typography.headlineLarge)
                Text(source.publisher, style = MaterialTheme.typography.labelLarge)
                Text(source.section, style = MaterialTheme.typography.bodyMedium)
            }
            items(paragraphs) { paragraph ->
                SelectionContainer { Text(paragraph, style = MaterialTheme.typography.bodyLarge) }
            }
            item { Heading(R.string.source_details) }
            item {
                Text(
                    stringResource(
                        R.string.source_date,
                        source.updated.ifBlank {
                            stringResource(R.string.unknown_date)
                        },
                        source.saved.ifBlank { stringResource(R.string.unknown_date) }
                    )
                )
                SelectionContainer { Text(source.url, style = MaterialTheme.typography.bodyMedium) }
            }
            item { Text(source.license, style = MaterialTheme.typography.bodyMedium) }
            item {
                Text(
                    source.edition.substringBeforeLast(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (available) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                if (!state.readerIsFull) {
                    TextButton(onClick = {
                        model.readFull(source)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.read_full_source))
                    }
                }
                PrimaryAction(R.string.ask_source) { model.askSource(source) }
            }
        }
    }
}
