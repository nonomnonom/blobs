package dev.blobs.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.fileSize

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
