package dev.blobs.app.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.CatalogSnapshot
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.fileSize
import java.text.DateFormat
import java.util.Date

@Composable
internal fun OptionalLibraryOption(
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
internal fun CatalogStatus(snapshot: CatalogSnapshot, busy: Boolean, refresh: () -> Unit) {
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
internal fun PackageCard(
    pack: PackageEntity,
    busy: Boolean,
    model: ProductViewModel,
    artwork: Boolean = false,
    onExport: () -> Unit = {}
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
                if (pack.kind == "library") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.collection_toggle))
                            Text(
                                stringResource(R.string.collection_toggle_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = pack.enabled,
                            onCheckedChange = { model.setLibraryEnabled(pack, it) },
                            enabled = !busy
                        )
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (pack.kind == "library") {
                        TextButton(onClick = { model.navigate(Page.BROWSE) }) {
                            Text(stringResource(R.string.take_look))
                        }
                        TextButton(onClick = onExport, enabled = !busy) {
                            Text(stringResource(R.string.export_collection))
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
