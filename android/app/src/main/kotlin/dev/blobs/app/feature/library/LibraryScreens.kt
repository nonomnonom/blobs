package dev.blobs.app.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.CatalogSnapshot
import dev.blobs.app.data.packages.DeviceProfile
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageRole
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.MenuRow
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductState
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.Scene
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.SearchField
import dev.blobs.app.ui.SetupProgressBlock
import dev.blobs.app.ui.fileSize
import java.text.DateFormat
import java.util.Date

@Composable
fun SetupScreen(packages: List<PackageEntity>, model: ProductViewModel) {
    val work by model.work.collectAsStateWithLifecycle()
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val profile = remember(context, model.catalog, catalog.packages) {
        DeviceProfile.inspect(context, model.catalog)
    }
    val hasModel = packages.any { it.kind == "model" }
    val hasStarter = packages.any {
        it.packageId == model.catalog.starterLibrary.manifest.id &&
            it.version == model.catalog.starterLibrary.manifest.version
    }
    val installedIds = packages.map { it.packageId to it.version }.toSet()
    val remainingLibraries = catalog.packages.filter { it.role == PackageRole.OPTIONAL }.filter {
        (it.manifest.id to it.manifest.version) !in installedIds
    }
    val setupReadyKinds = buildSet {
        if (hasModel) add("model")
        if (hasStarter) add("library")
    }
    val needsModel = !hasModel
    val needsStarter = !hasStarter
    val ready = !needsModel && !needsStarter && remainingLibraries.isEmpty()
    var selectedModelFile by rememberSaveable {
        mutableStateOf(profile.recommendedModel.fileName)
    }
    val availableModels = catalog.packages.filter { it.manifest.kind == "model" }
    val starterLibrary = catalog.packages.single { it.role == PackageRole.STARTER }
    val selectedModel = availableModels.firstOrNull { it.fileName == selectedModelFile }
        ?: profile.recommendedModel
    val progressLabel = when {
        ready -> R.string.setup_all_ready
        needsModel -> R.string.setup_model_step
        needsStarter -> R.string.setup_library_step
        else -> R.string.setup_extras_step
    }
    ScrollPage {
        if (work.inAppDownload) {
            SetupProgressBlock(work.setupProgress(setupReadyKinds), progressLabel)
        }
        Scene(
            when {
                ready -> R.drawable.ready_world
                needsModel -> R.drawable.welcome_world
                else -> R.drawable.library_world
            },
            if (ready) 240 else 200
        )
        if (ready) {
            Body(R.string.ready_body)
            PrimaryAction(R.string.start_chat) { model.finishSetup() }
            return@ScrollPage
        }
        if (work.pendingImport != null || (work.busy && work.total > 0)) {
            Heading(R.string.download_in_progress)
            Body(R.string.download_in_progress_body)
            PrimaryAction(R.string.download_progress_action) { model.navigate(Page.IMPORT) }
        } else {
            if (needsModel) {
                Heading(R.string.setup_model_title)
                Body(R.string.setup_model_body)
                availableModels.forEach { option ->
                    ModelOption(
                        option,
                        option == profile.recommendedModel,
                        option.fileName == selectedModel.fileName,
                        onClick = { selectedModelFile = option.fileName }
                    )
                }
                PrimaryAction(
                    R.string.download_selected_model,
                    !work.busy
                ) { model.downloadPackage(selectedModel) }
                Text(
                    stringResource(
                        R.string.device_fit_note,
                        fileSize(profile.totalMemoryBytes),
                        fileSize(profile.freeStorageBytes)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (needsStarter) {
                Heading(R.string.setup_library_title)
                Body(R.string.setup_library_body)
                PrimaryAction(
                    R.string.download_starter,
                    !work.busy
                ) { model.downloadPackage(starterLibrary) }
                Text(
                    stringResource(
                        R.string.download_size_note,
                        fileSize(starterLibrary.downloadBytes)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (remainingLibraries.isNotEmpty()) {
                Heading(R.string.setup_extras_title)
                Body(R.string.setup_extras_body)
                remainingLibraries.forEach { library ->
                    OptionalLibraryOption(library, !work.busy) {
                        model.downloadPackage(library)
                    }
                }
                PrimaryAction(R.string.continue_without_extras, !work.busy) {
                    model.finishSetup()
                }
            } else {
                Heading(R.string.setup_ready_title)
                Body(R.string.ready_body)
                PrimaryAction(R.string.start_chat) { model.finishSetup() }
            }
        }
    }
}

@Composable
private fun ModelOption(
    option: DownloadablePackage,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(option.manifest.title, style = MaterialTheme.typography.titleMedium)
                if (option.description.isNotBlank()) {
                    Text(option.description, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(
                        if (recommended) {
                            R.string.recommended_model_size
                        } else {
                            R.string.model_download_size
                        },
                        fileSize(option.downloadBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (option.language.isNotBlank() || option.publisher.isNotBlank()) {
                    Text(
                        stringResource(
                            R.string.library_asset_details,
                            option.language.ifBlank { "Reference" },
                            option.publisher.ifBlank { "Blobs" }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.library_asset_license, option.manifest.license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OptionalLibraryOption(
    library: DownloadablePackage,
    enabled: Boolean,
    onDownload: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(library.manifest.title, style = MaterialTheme.typography.titleLarge)
            if (library.description.isNotBlank()) {
                Text(library.description, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(R.string.optional_library_size, fileSize(library.downloadBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (library.language.isNotBlank() || library.publisher.isNotBlank()) {
                Text(
                    stringResource(
                        R.string.library_asset_details,
                        library.language.ifBlank { "Reference" },
                        library.publisher.ifBlank { "Blobs" }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.library_asset_license, library.manifest.license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryAction(R.string.download_library, enabled, onDownload)
        }
    }
}

@Composable
fun LibraryScreen(packages: List<PackageEntity>, model: ProductViewModel, pickFile: () -> Unit) {
    val work by model.work.collectAsStateWithLifecycle()
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    val large = LocalDensity.current.fontScale >= 1.5f
    ScrollPage {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Heading(R.string.library_title)
            if (!large) {
                Text(
                    stringResource(R.string.library_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CatalogStatus(catalog, work.busy, model::refreshCatalog)
        }
        val libraries = packages.filter { it.kind == "library" }
        if (libraries.isEmpty()) {
            Scene(R.drawable.library_world, 200)
            Text(
                stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.titleLarge
            )
            Body(R.string.library_empty_body)
            PrimaryAction(R.string.download_library, !work.busy) {
                model.downloadPackage(catalog.packages.single { it.role == PackageRole.STARTER })
            }
            TextButton(
                onClick = pickFile,
                enabled = !work.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.have_local_packages))
            }
        }
        libraries.forEachIndexed { index, pack ->
            PackageCard(
                pack,
                work.busy,
                model,
                artwork =
                index == 0
            )
        }
        Heading(R.string.explore_title)
        Body(R.string.explore_body)
        MenuRow(R.string.explore_models, model.selectedModel()?.title.orEmpty()) {
            model.navigate(Page.MODELS)
        }
        val installedLibraries = libraries.map { it.packageId to it.version }.toSet()
        catalog.packages.filter { it.role == PackageRole.OPTIONAL }.filter {
            (it.manifest.id to it.manifest.version) !in installedLibraries
        }.forEach { library ->
            OptionalLibraryOption(library, !work.busy) { model.downloadPackage(library) }
        }
        Column {
            MenuRow(R.string.browse, stringResource(R.string.browse_summary)) {
                model.navigate(Page.BROWSE)
            }
            MenuRow(
                R.string.models,
                model.selectedModel()?.title ?: stringResource(R.string.model_missing)
            ) {
                model.navigate(Page.MODELS)
            }
            MenuRow(
                R.string.add_package,
                stringResource(R.string.import_summary),
                !work.busy,
                pickFile
            )
            MenuRow(R.string.settings, stringResource(R.string.settings_body)) {
                model.navigate(Page.SETTINGS)
            }
        }
    }
}

@Composable
fun ModelsScreen(
    packages: List<PackageEntity>,
    work: WorkState,
    model: ProductViewModel,
    pickFile: () -> Unit
) {
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    ScrollPage {
        Heading(R.string.models)
        Body(R.string.models_help)
        CatalogStatus(catalog, work.busy, model::refreshCatalog)
        if (work.busy) Body(R.string.busy_help)
        val models = packages.filter { it.kind == "model" }
        models.forEach { PackageCard(it, work.busy, model) }
        val installed = models.map { it.packageId to it.version }.toSet()
        catalog.packages.filter { it.manifest.kind == "model" }
            .filter { (it.manifest.id to it.manifest.version) !in installed }
            .forEach { option ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(option.manifest.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.model_download_size,
                                fileSize(option.downloadBytes)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        PrimaryAction(R.string.download_model, !work.busy) {
                            model.downloadPackage(option)
                        }
                    }
                }
            }
        TextButton(onClick = pickFile, enabled = !work.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_package))
        }
    }
}

@Composable
private fun CatalogStatus(snapshot: CatalogSnapshot, busy: Boolean, refresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val message = when {
            snapshot.refreshing -> stringResource(R.string.library_update_checking)
            snapshot.lastCheckedAt != null -> stringResource(
                R.string.library_update_checked,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(snapshot.lastCheckedAt))
            )
            else -> stringResource(R.string.library_update_offline_hint)
        }
        Text(
            message,
            modifier = Modifier.weight(1f).padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = refresh, enabled = !snapshot.refreshing && !busy) {
            Text(stringResource(R.string.library_update_action))
        }
    }
}

@Composable
private fun PackageCard(
    pack: PackageEntity,
    busy: Boolean,
    model: ProductViewModel,
    artwork: Boolean = false
) {
    var removing by remember { mutableStateOf(false) }
    val packages by model.packages.collectAsStateWithLifecycle()
    val lastModel = pack.kind == "model" && packages.count { it.kind == "model" } <= 1
    val fallback = packages.firstOrNull { it.kind == "model" && it.key != pack.key }
    val large = LocalDensity.current.fontScale >= 1.5f
    Surface(
        color = if (artwork) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(24.dp)
    ) {
        Column {
            if (artwork && !large) {
                Image(
                    painterResource(R.drawable.library_world),
                    null,
                    Modifier.fillMaxWidth().height(136.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(pack.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${pack.version} · ${fileSize(pack.bytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(pack.license, style = MaterialTheme.typography.bodyMedium)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (pack.kind == "library") {
                        TextButton(onClick = { model.navigate(Page.BROWSE) }) {
                            Text(stringResource(R.string.take_look))
                        }
                    }
                    if (pack.kind == "model") {
                        val selected = model.selectedModel()?.key == pack.key
                        Button(onClick = {
                            model.selectModel(pack)
                        }, enabled = !busy && !selected) {
                            Text(
                                stringResource(
                                    if (selected) R.string.model_selected else R.string.use_model
                                )
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            removing = true
                        },
                        enabled = !busy && !lastModel,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text(stringResource(R.string.remove)) }
                }
                if (lastModel) {
                    Text(
                        stringResource(R.string.keep_model),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = {
                removing = false
            },
            title = { Text(stringResource(R.string.remove_package, pack.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.remove_package_body, fileSize(pack.bytes)))
                    if (model.selectedModel()?.key == pack.key && fallback != null) {
                        Text(stringResource(R.string.fallback_model, fallback.title))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    model.remove(pack)
                    removing = false
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

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

@Composable
fun StorageScreen(
    packages: List<PackageEntity>,
    work: WorkState,
    model: ProductViewModel,
    pickFile: () -> Unit
) {
    ScrollPage {
        Heading(R.string.storage)
        Body(R.string.storage_body)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(
                        R.string.installed_bytes,
                        fileSize(
                            packages.sumOf {
                                it.bytes
                            }
                        )
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(R.string.library_installed),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (work.busy && work.total > 0) {
            Text(
                stringResource(
                    R.string.import_progress,
                    fileSize(work.progress),
                    fileSize(work.total)
                )
            )
            LinearProgressIndicator(progress = {
                (work.progress.toFloat() / work.total).coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth())
            if (work.progress == work.total) Body(R.string.checking_file)
            TextButton(onClick = model::stop) { Text(stringResource(R.string.pause_import)) }
        }
        if (work.pendingImport != null) {
            Body(R.string.paused_import)
            Text(work.pendingImport.manifest.title)
            PrimaryAction(R.string.view_import) { model.navigate(Page.IMPORT) }
        }
        packages.forEach { PackageCard(it, work.busy, model) }
        PrimaryAction(R.string.add_package, !work.busy, pickFile)
    }
}

@Composable
fun ImportReviewScreen(
    state: ProductState,
    packages: List<PackageEntity>,
    model: ProductViewModel
) {
    val work by model.work.collectAsStateWithLifecycle()
    if (state.importUri == null) return
    val manifest = state.manifest
    val duplicate = packages.any { it.key == manifest?.key }
    val otherEdition = packages.any { it.packageId == manifest?.id && it.key != manifest.key }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Heading(if (duplicate) R.string.package_already_added else R.string.review_import)
            if (manifest == null) {
                CircularProgressIndicator()
                Body(R.string.checking_file)
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(manifest.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.package_edition, manifest.version),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.installed_size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(fileSize(manifest.bytes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.install_space,
                            fileSize(
                                manifest.bytes + 64L * 1024 * 1024
                            )
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.package_license),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(manifest.license, style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    stringResource(R.string.import_trust),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!duplicate && otherEdition) {
                    Text(
                        stringResource(R.string.older_editions_kept),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            if (duplicate) {
                Text(
                    stringResource(R.string.already_added),
                    modifier = Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            PrimaryAction(
                R.string.add,
                manifest != null && !duplicate && !work.busy,
                model::install
            )
            TextButton(onClick = model::dismissImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
