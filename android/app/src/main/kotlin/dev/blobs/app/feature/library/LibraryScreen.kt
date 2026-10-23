package dev.blobs.app.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DeviceProfile
import dev.blobs.app.data.packages.LibraryProfile
import dev.blobs.app.data.packages.PackageRole
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.MenuRow
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.Scene
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
fun LibraryScreen(
    packages: List<PackageEntity>,
    model: AppViewModel,
    pickFile: () -> Unit,
    pickDocuments: () -> Unit,
    exportCollection: (PackageEntity) -> Unit
) {
    val work by model.work.collectAsStateWithLifecycle()
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var discover by rememberSaveable { mutableStateOf(false) }
    var freeStorageBytes by remember { mutableStateOf<Long?>(null) }
    val collectionScroll = rememberScrollState()
    val discoverScroll = rememberScrollState()
    val libraries = packages.filter { it.kind == "library" }
    LaunchedEffect(catalog.packages, work.importComplete) {
        freeStorageBytes = runCatching {
            DeviceProfile.inspect(context, model.catalog).freeStorageBytes
        }.getOrNull()
    }
    LaunchedEffect(work.importComplete, work.inAppDownload, work.packageKind) {
        if (work.importComplete && work.inAppDownload && work.packageKind == "library") {
            discover = false
        }
    }
    val available = catalog.packages.filter { asset ->
        asset.manifest.kind == "library" && libraries.none { it.key == asset.manifest.key }
    }.sortedWith(
        compareBy<dev.blobs.app.data.packages.DownloadablePackage> {
            if (it.role == PackageRole.STARTER) 0 else 1
        }.thenBy { it.downloadBytes }
    )
    val starter = model.catalog.starterLibrary
    val starterRequiredStorageBytes = model.catalog.requiredStorageBytes(starter)
    val starterFitsStorage = freeStorageBytes?.let {
        model.catalog.packageFitsStorage(starter, it)
    } ?: true
    Column {
        PrimaryTabRow(selectedTabIndex = if (discover) 1 else 0) {
            Tab(selected = !discover, onClick = {
                discover = false
            }, text = { Text(stringResource(R.string.your_collections)) })
            Tab(selected = discover, onClick = {
                discover = true
            }, text = { Text(stringResource(R.string.discover_more)) })
        }
        Column(
            Modifier.weight(
                1f
            ).verticalScroll(if (discover) discoverScroll else collectionScroll).padding(BlobsDimens.page),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.lg)
        ) {
            if (!discover) {
                if (libraries.isEmpty()) {
                    Scene(R.drawable.library_world, 160)
                    Heading(R.string.library_empty_heading)
                    Body(R.string.library_empty_description)
                    Text(
                        stringResource(
                            R.string.library_storage_requirement,
                            fileSize(starterRequiredStorageBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    freeStorageBytes?.takeIf { !starterFitsStorage }?.let { availableBytes ->
                        Text(
                            stringResource(
                                R.string.library_space_to_free,
                                fileSize(
                                    (starterRequiredStorageBytes - availableBytes)
                                        .coerceAtLeast(0)
                                )
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    PrimaryAction(R.string.download_starter, !work.busy && starterFitsStorage) {
                        model.downloadPackage(model.catalog.starterLibrary)
                    }
                }
                libraries.forEachIndexed { index, pack ->
                    CollectionCard(pack, index, work.busy, model) { exportCollection(pack) }
                }
                Column {
                    MenuRow(
                        R.string.add_files_title,
                        stringResource(R.string.add_files_description),
                        !work.busy,
                        icon = R.drawable.ic_add_file,
                        onClick = pickDocuments
                    )
                    MenuRow(
                        R.string.import_library_title,
                        stringResource(R.string.import_library_description),
                        !work.busy,
                        icon = R.drawable.ic_folder_open,
                        onClick = pickFile
                    )
                }
            } else {
                Scene(R.drawable.discovery_world, 140)
                Heading(R.string.discover_heading)
                Body(R.string.discover_description)
                CatalogStatus(catalog, work.busy, model::refreshCatalog)
                if (available.isEmpty()) {
                    Body(R.string.all_caught_up)
                } else {
                    Body(R.string.library_small_downloads)
                }
                available.forEach { asset ->
                    val requiredStorageBytes = model.catalog.requiredStorageBytes(asset)
                    val fitsStorage = freeStorageBytes?.let {
                        model.catalog.packageFitsStorage(asset, it)
                    } ?: true
                    LibraryDownloadCard(
                        asset,
                        !work.busy && fitsStorage,
                        requiredStorageBytes,
                        freeStorageBytes
                    ) {
                        model.downloadPackage(asset)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    pack: PackageEntity,
    index: Int,
    busy: Boolean,
    model: AppViewModel,
    export: () -> Unit
) {
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    val metadata = catalog.packages.find { it.manifest.key == pack.key }
    val storedProfile = remember(pack.key, pack.libraryProfile) {
        pack.libraryProfile.takeIf(String::isNotBlank)?.let { value ->
            runCatching { LibraryProfile.fromStorageJson(value) }.getOrNull()
        }
    }
    var options by rememberSaveable { mutableStateOf(false) }
    val large = LocalDensity.current.fontScale >= 1.5f
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column {
            Box {
                Image(
                    painterResource(
                        if (index ==
                            0
                        ) {
                            R.drawable.library_world
                        } else {
                            R.drawable.discovery_world
                        }
                    ),
                    null,
                    Modifier.fillMaxWidth().height(if (large) 80.dp else 112.dp),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(BlobsDimens.sm),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Text(
                        stringResource(R.string.saved_offline),
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(
                Modifier.padding(BlobsDimens.lg),
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pack.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Box {
                        IconButton(onClick = { options = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more),
                                stringResource(R.string.collection_options, pack.title)
                            )
                        }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            if (pack.origin == "local:user-documents") {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.export_collection))
                                    },
                                    enabled = !busy,
                                    onClick = {
                                        options = false
                                        export()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.storage)) },
                                onClick = {
                                    options = false
                                    model.navigate(Page.STORAGE)
                                }
                            )
                        }
                    }
                }
                Text(
                    metadata?.let {
                        stringResource(
                            R.string.library_saved_summary,
                            it.language,
                            fileSize(pack.bytes)
                        )
                    } ?: fileSize(pack.bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (metadata != null) {
                    Text(metadata.description, style = MaterialTheme.typography.bodyMedium)
                    LibraryDetails(metadata)
                } else if (storedProfile != null) {
                    InstalledLibraryDetails(pack, storedProfile)
                }
                OutlinedButton(onClick = {
                    model.browseCollection(pack)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.browse_collection))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.use_for_answers),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(
                                if (pack.enabled) {
                                    R.string.collection_included
                                } else {
                                    R.string.collection_excluded
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val toggleLabel =
                        stringResource(R.string.use_collection_for_answers, pack.title)
                    Switch(
                        checked = pack.enabled,
                        onCheckedChange = {
                            model.setLibraryEnabled(pack, it)
                        },
                        enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = toggleLabel }
                    )
                }
            }
        }
    }
}
