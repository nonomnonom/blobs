package dev.blobs.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.PackageRole
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.MenuRow
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.Scene
import dev.blobs.app.ui.ScrollPage

@Composable
fun LibraryScreen(
    packages: List<PackageEntity>,
    model: ProductViewModel,
    pickFile: () -> Unit,
    pickDocuments: () -> Unit,
    exportCollection: (PackageEntity) -> Unit
) {
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
                onClick = pickDocuments,
                enabled = !work.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_documents))
            }
        }
        libraries.forEachIndexed { index, pack ->
            PackageCard(pack, work.busy, model, artwork = index == 0) {
                exportCollection(pack)
            }
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
            MenuRow(
                R.string.add_documents,
                stringResource(R.string.add_documents_summary),
                !work.busy,
                pickDocuments
            )
            MenuRow(R.string.settings, stringResource(R.string.settings_body)) {
                model.navigate(Page.SETTINGS)
            }
        }
    }
}
