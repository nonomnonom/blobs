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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
internal fun LibraryDownloadCard(
    library: DownloadablePackage,
    enabled: Boolean,
    onDownload: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(library.manifest.title, style = MaterialTheme.typography.titleLarge)
            if (library.description.isNotBlank()) {
                Text(library.description, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(
                    R.string.library_download_summary,
                    library.language,
                    fileSize(library.downloadBytes),
                    fileSize(library.manifest.bytes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LibraryDetails(library, enabled, onDownload)
        }
    }
}

@Composable
internal fun LibraryDetails(
    library: DownloadablePackage,
    enabled: Boolean = true,
    download: (() -> Unit)? = null
) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(library.manifest.key) {
        mutableStateOf(false)
    }
    val detailsLabel = stringResource(R.string.library_details_for, library.manifest.title)
    val downloadLabel = stringResource(R.string.download_library_named, library.manifest.title)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics { contentDescription = detailsLabel }
        ) {
            Text(stringResource(R.string.library_details))
        }
        if (download != null) {
            Button(
                onClick = download,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = downloadLabel }
            ) {
                Text(stringResource(R.string.download_library))
            }
        }
    }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(library.publisher, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.library_edition, library.manifest.version),
                style = MaterialTheme.typography.bodySmall
            )
            Text(library.manifest.license, style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.library_source_credits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CatalogStatus(snapshot: CatalogSnapshot, busy: Boolean, refresh: () -> Unit) {
    val message = when {
        snapshot.refreshing -> stringResource(R.string.library_update_checking)

        snapshot.lastCheckedAt != null -> stringResource(
            R.string.library_update_checked,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(snapshot.lastCheckedAt))
        )

        else -> stringResource(R.string.library_update_offline_hint)
    }
    val status: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            message,
            modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val action: @Composable () -> Unit = {
        TextButton(onClick = refresh, enabled = !snapshot.refreshing && !busy) {
            Text(stringResource(R.string.library_update_action))
        }
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            status(Modifier.fillMaxWidth())
            action()
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            status(Modifier.weight(1f))
            action()
        }
    }
}
