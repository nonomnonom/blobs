package dev.blobs.app.feature.settings

import android.app.usage.StorageStatsManager
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class StorageUsage(val used: Long?, val available: Long, val capacity: Long)

@Composable
fun StorageScreen(
    packages: List<PackageEntity>,
    work: WorkState,
    model: AppViewModel,
    pickFile: () -> Unit
) {
    val context = LocalContext.current
    var revision by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val usage by produceState<StorageUsage?>(null, packages, work.busy, revision) {
        value = withContext(Dispatchers.IO) {
            val disk = StatFs(context.filesDir.path)
            val stats = runCatching {
                context.getSystemService(StorageStatsManager::class.java)
                    .queryStatsForUid(
                        context.applicationInfo.storageUuid,
                        context.applicationInfo.uid
                    )
            }.getOrNull()
            StorageUsage(
                stats?.let {
                    it.appBytes + it.dataBytes
                },
                disk.availableBytes,
                disk.totalBytes
            )
        }
    }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val target = packages.find { it.key == removing }
    val lastModel = target?.kind == "model" && packages.count { it.kind == "model" } <= 1
    ScrollPage {
        Heading(R.string.storage_heading)
        Body(R.string.storage_body)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                Modifier.fillMaxWidth().padding(BlobsDimens.lg),
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
            ) {
                Text(
                    stringResource(
                        if (usage?.used !=
                            null
                        ) {
                            R.string.used_by_blobs
                        } else {
                            R.string.installed_files
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    fileSize(
                        usage?.used ?: packages.sumOf {
                            it.bytes
                        }
                    ),
                    style = MaterialTheme.typography.headlineLarge
                )
                usage?.let { current ->
                    LinearProgressIndicator(progress = {
                        (
                            (
                                current.used
                                    ?: packages.sumOf { it.bytes }
                                ).toDouble() /
                                current.capacity.coerceAtLeast(1)
                            ).toFloat().coerceIn(0f, 1f)
                    }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.storage_free, fileSize(current.available)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (work.busy) Body(R.string.busy_help)
        if (work.pendingImport != null) {
            TextButton(onClick = {
                model.navigate(Page.IMPORT)
            }) { Text(stringResource(R.string.view_import)) }
        }
        Text(stringResource(R.string.saved_offline), style = MaterialTheme.typography.titleMedium)
        packages.forEach { pack ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = BlobsDimens.xs),
                horizontalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pack.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        fileSize(pack.bytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { removing = pack.key }, enabled = !work.busy) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        stringResource(R.string.remove_package_name, pack.title)
                    )
                }
            }
            HorizontalDivider()
        }
        OutlinedButton(
            onClick = pickFile,
            enabled = !work.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            Text(stringResource(R.string.import_library_title), Modifier.padding(vertical = 6.dp))
        }
        Body(R.string.storage_keeps_answers)
    }
    if (target != null) {
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_package_name, target.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(target.version, style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(
                            if (lastModel) R.string.keep_model else R.string.storage_keeps_answers
                        )
                    )
                    val fallback = packages.firstOrNull {
                        it.kind == "model" && it.key != target.key
                    }
                    if (target.key == model.selectedModel()?.key && fallback != null) {
                        Text(stringResource(R.string.fallback_model, fallback.title))
                    }
                    if (!lastModel) {
                        Text(
                            stringResource(R.string.storage_frees, fileSize(target.bytes))
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.remove(target)
                        removing = null
                    },
                    enabled = !work.busy && !lastModel,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
