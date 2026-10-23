package dev.blobs.app.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.ui.AppUiState
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.SearchField
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
fun BrowseScreen(state: AppUiState, model: AppViewModel) {
    val packages by model.packages.collectAsStateWithLifecycle()
    if (state.page == Page.SOURCES) {
        LibraryScopePicker(packages, model)
        return
    }
    val collection = packages.find { it.key == state.browseCollection }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = BlobsDimens.page),
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
    ) {
        collection?.let { Text(it.title, style = MaterialTheme.typography.titleLarge) }
        SearchField(state.query, R.string.search_sources, onValueChange = model::search)
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
        ) {
            if (state.sources.isEmpty() && !state.searching) {
                item {
                    Body(R.string.no_results)
                    Body(R.string.no_results_body)
                    TextButton(onClick = { model.search("") }) {
                        Text(stringResource(R.string.clear_search))
                    }
                }
            }
            items(state.sources, key = { it.id }) { source ->
                Surface(
                    onClick = { model.readFull(source) },
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(BlobsDimens.md),
                        verticalArrangement = Arrangement.spacedBy(BlobsDimens.xxs)
                    ) {
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
            item {
                if (state.offset > 0) {
                    TextButton(onClick = {
                        model.search(state.query, (state.offset - 50).coerceAtLeast(0))
                    }) { Text(stringResource(R.string.previous_page)) }
                }
                if (state.hasMore) {
                    TextButton(onClick = { model.search(state.query, state.offset + 50) }) {
                        Text(stringResource(R.string.next_page))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScopePicker(packages: List<PackageEntity>, model: AppViewModel) {
    var selected by remember { mutableStateOf(model.selectedLibraryScope()) }
    val all = selected == null
    val none = selected?.isEmpty() == true
    val libraries = packages.filter { it.kind == "library" && it.enabled }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = BlobsDimens.page),
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
    ) {
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
        ) {
            item { Body(R.string.library_scope_help) }
            item {
                ScopeOption(
                    R.string.all_libraries,
                    R.string.all_libraries_description,
                    all,
                    R.drawable.ic_auto_stories
                ) {
                    selected = null
                    model.selectLibraryScope(null)
                }
            }
            item {
                ScopeOption(
                    R.string.no_library,
                    R.string.no_library_description,
                    none,
                    R.drawable.ic_chat_bubble
                ) {
                    selected = emptySet()
                    model.selectLibraryScope(emptySet())
                }
            }
            if (libraries.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Body(R.string.no_installed_libraries)
                        TextButton(onClick = { model.navigate(Page.LIBRARY) }) {
                            Text(stringResource(R.string.add_library))
                        }
                    }
                }
            } else {
                items(libraries, key = { it.key }) { pack ->
                    val checked = all || pack.key in selected.orEmpty()
                    Surface(
                        selected = checked,
                        onClick = {
                            val current = selected ?: libraries.mapTo(mutableSetOf()) { it.key }
                            val next =
                                if (pack.key in current) {
                                    current - pack.key
                                } else {
                                    current + pack.key
                                }
                            selected = next
                            model.selectLibraryScope(next)
                        },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            1.dp,
                            if (checked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        ),
                        color = if (checked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(BlobsDimens.md),
                            horizontalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(pack.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.library_available_offline,
                                        fileSize(pack.bytes)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        PrimaryAction(R.string.done) { model.back() }
    }
}

@Composable
private fun ScopeOption(
    title: Int,
    description: Int,
    selected: Boolean,
    icon: Int,
    onClick: () -> Unit
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(BlobsDimens.md),
            horizontalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), null)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.xxs)
            ) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
