package dev.blobs.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
fun ReaderScreen(source: Evidence?, model: AppViewModel) {
    if (source == null) return
    var addressOpen by androidx.compose.runtime.saveable.rememberSaveable(source.id) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val packages by model.packages.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val installed = packages.any {
        it.kind == "library" && source.id.startsWith(it.key + "/")
    }
    val enabledForAnswers = packages.any {
        it.kind == "library" && it.enabled && source.id.startsWith(it.key + "/")
    }
    val large = LocalDensity.current.fontScale >= 1.5f
    val actions: @Composable (Modifier) -> Unit = { modifier ->
        if (installed) {
            Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                if (!state.readerIsFull) {
                    TextButton(onClick = {
                        model.readFull(source)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.read_full_source))
                    }
                }
                if (enabledForAnswers) {
                    PrimaryAction(R.string.use_library_in_chat) { model.askSource(source) }
                }
            }
        }
    }
    val paragraphs = remember(source.text) { splitReaderParagraphs(source.text) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = BlobsDimens.page),
            contentPadding = PaddingValues(vertical = BlobsDimens.lg),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.lg)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)) {
                    Text(
                        source.publisher,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(source.title, style = MaterialTheme.typography.headlineLarge)
                    Text(
                        source.section,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(paragraphs) { index, paragraph ->
                if (!state.readerIsFull && index == 0) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        SelectionContainer {
                            Text(
                                paragraph,
                                Modifier.padding(BlobsDimens.md),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(paragraph, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item { Heading(R.string.source_details) }
            item {
                Text(
                    stringResource(
                        R.string.source_date,
                        sourceDate(source.updated).ifBlank {
                            stringResource(R.string.unknown_date)
                        },
                        sourceDate(source.saved).ifBlank { stringResource(R.string.unknown_date) }
                    )
                )
                if (source.url.isNotBlank()) {
                    TextButton(onClick = { addressOpen = true }) {
                        Column {
                            Text(stringResource(R.string.source_address))
                            Text(
                                source.url.toUri().host ?: source.url,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.source_license),
                        style = MaterialTheme.typography.titleMedium
                    )
                    SelectionContainer {
                        Text(source.license, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Text(
                    source.edition.substringBeforeLast(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!installed) {
                item {
                    Text(
                        stringResource(R.string.source_unavailable),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (large) item { actions(Modifier) }
        }
        if (!large) actions(Modifier.padding(horizontal = BlobsDimens.page))
    }
    if (addressOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { addressOpen = false },
            title = { Text(stringResource(R.string.source_address)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(source.url, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    addressOpen = false
                }) { Text(stringResource(R.string.done)) }
            }
        )
    }
}

private fun sourceDate(value: String): String = runCatching {
    java.time.LocalDate.parse(value.take(10)).format(
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withLocale(java.util.Locale.ENGLISH)
    )
}.getOrDefault(value)

private fun splitReaderParagraphs(text: String): List<String> =
    text.split(Regex("\\n\\s*\\n")).flatMap { paragraph ->
        buildList {
            var remaining = paragraph
            while (remaining.length > MAX_READER_PARAGRAPH_CHARACTERS) {
                val preferredEnd = remaining.lastIndexOf(' ', MAX_READER_PARAGRAPH_CHARACTERS)
                    .takeIf { it > 0 } ?: MAX_READER_PARAGRAPH_CHARACTERS
                val end = preserveSurrogatePair(remaining, preferredEnd)
                add(remaining.substring(0, end))
                remaining = remaining.substring(end).trimStart()
            }
            if (remaining.isNotBlank()) add(remaining)
        }
    }

private fun preserveSurrogatePair(value: String, end: Int): Int =
    if (end < value.length && end > 0 && Character.isHighSurrogate(value[end - 1]) &&
        Character.isLowSurrogate(value[end])
    ) {
        end - 1
    } else {
        end
    }

private const val MAX_READER_PARAGRAPH_CHARACTERS = 2_400
