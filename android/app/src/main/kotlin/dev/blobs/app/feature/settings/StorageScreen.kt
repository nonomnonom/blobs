package dev.blobs.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.feature.library.PackageCard
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.fileSize

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
