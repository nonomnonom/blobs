package dev.blobs.app.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DeviceProfile
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.ModelLogo
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
fun ModelsScreen(
    packages: List<PackageEntity>,
    work: WorkState,
    model: AppViewModel,
    pickFile: () -> Unit
) {
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val profile =
        remember(context, catalog.packages) { DeviceProfile.inspect(context, model.catalog) }
    val selected = model.selectedModel()?.key
    val installed = packages.filter { it.kind == "model" }
    ScrollPage {
        Body(R.string.models_help)
        Text(
            pluralStringResource(
                R.plurals.model_device_context,
                model.contextWindowTokens,
                model.contextWindowTokens
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        catalog.packages.filter { it.manifest.kind == "model" }.forEach { asset ->
            val pack = installed.find { it.key == asset.manifest.key }
            ModelCard(
                asset.manifest.title,
                asset.description,
                "${fileSize(asset.downloadBytes)} · ${asset.manifest.license}",
                pack?.key == selected
            ) {
                if (!model.catalog.packageFitsMemory(asset, profile.totalMemoryBytes)) {
                    Text(
                        stringResource(R.string.model_memory_warning),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (pack != null) {
                    if (pack.key != selected) {
                        OutlinedButton(
                            onClick = { model.selectModel(pack) },
                            enabled = !work.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.use_model))
                        }
                    }
                } else {
                    PrimaryAction(R.string.download_model, !work.busy) {
                        model.downloadPackage(asset)
                    }
                }
            }
        }
        installed.filter { pack ->
            catalog.packages.none { it.manifest.key == pack.key }
        }.forEach { pack ->
            ModelCard(pack.title, pack.version, fileSize(pack.bytes), pack.key == selected) {
                if (pack.key != selected) {
                    OutlinedButton(
                        onClick = { model.selectModel(pack) },
                        enabled = !work.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.use_model))
                    }
                }
            }
        }
        CatalogStatus(catalog, work.busy, model::refreshCatalog)
        TextButton(onClick = pickFile, enabled = !work.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_model_file))
        }
    }
}

@Composable
private fun ModelCard(
    title: String,
    description: String,
    details: String,
    selected: Boolean,
    action: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(BlobsDimens.lg),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.md)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelLogo(title, 44.dp)
                if (selected) {
                    Text(
                        stringResource(R.string.model_selected),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            action()
        }
    }
}
