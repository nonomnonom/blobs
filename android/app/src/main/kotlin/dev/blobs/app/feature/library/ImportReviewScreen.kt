package dev.blobs.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.ui.AppUiState
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
fun ImportReviewScreen(state: AppUiState, packages: List<PackageEntity>, model: AppViewModel) {
    val work by model.work.collectAsStateWithLifecycle()
    if (state.importUri == null) return
    val manifest = state.manifest
    val duplicate = packages.any { it.key == manifest?.key }
    val otherEdition = packages.any { it.packageId == manifest?.id && it.key != manifest.key }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(BlobsDimens.page),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.lg)
        ) {
            Heading(if (duplicate) R.string.package_already_added else R.string.review_import)
            if (manifest == null) {
                CircularProgressIndicator()
                Body(R.string.checking_file)
            } else {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(BlobsDimens.lg),
                        verticalArrangement = Arrangement.spacedBy(BlobsDimens.xs)
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
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = BlobsDimens.page,
                vertical = BlobsDimens.sm
            )
        ) {
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
