package dev.blobs.app.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.ModelLogo
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductState
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.Scene
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.SetupProgress
import dev.blobs.app.ui.fileSize
import kotlin.math.roundToInt

@Composable
fun ImportScreen(state: ProductState, work: WorkState, model: ProductViewModel) {
    var cancelling by rememberSaveable { mutableStateOf(false) }
    val packages by model.packages.collectAsStateWithLifecycle()
    val readyKinds = buildSet {
        if (packages.any { it.kind == "model" }) add("model")
        if (model.catalog.hasStarterLibrary(packages)) add("library")
    }
    val preparingDocuments = work.transferActive &&
        work.packageKind == "library" &&
        !work.inAppDownload &&
        work.total == 0L
    val running = work.transferActive
    val checking = running && work.total > 0 && work.progress == work.total
    BackHandler {
        if (running || work.hasPendingTransfer) cancelling = true else model.finishImport()
    }
    val failed = work.notice in setOf(
        R.string.import_failed,
        R.string.storage_failed,
        R.string.download_failed,
        R.string.download_source_failed
    )
    ScrollPage {
        Scene(
            when {
                work.importComplete -> R.drawable.ready_world
                failed || (!running && work.hasPendingTransfer) -> R.drawable.pause_world
                else -> R.drawable.library_world
            },
            168
        )
        Heading(
            when {
                work.importComplete -> R.string.import_ready_title
                failed -> R.string.import_error_title
                !running && work.hasPendingTransfer -> R.string.import_paused_title
                checking -> R.string.import_checking_title
                work.downloading -> R.string.download_title
                work.inAppDownload && running -> R.string.download_install_title
                running -> R.string.import_title
                work.pendingImport != null -> R.string.import_paused_title
                else -> R.string.import_cancelled
            }
        )
        val title = work.importTitle.ifBlank { work.pendingImport?.manifest?.title.orEmpty() }
        Body(
            when {
                work.importComplete -> R.string.download_saved_body
                failed -> R.string.download_error_keeps_files
                !running && work.hasPendingTransfer -> R.string.paused_import
                checking -> R.string.checking_file
                work.downloading -> R.string.download_body
                work.inAppDownload && running -> R.string.download_install_body
                running -> R.string.import_body
                else -> R.string.storage_keeps_answers
            }
        )
        if (work.inAppDownload && state.importOrigin == Page.SETUP) {
            Text(
                stringResource(
                    if (work.packageKind ==
                        "model"
                    ) {
                        R.string.setup_model_step
                    } else {
                        R.string.setup_library_step
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (title.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (work.packageKind == "model") {
                            ModelLogo(title)
                        } else {
                            Icon(painterResource(R.drawable.ic_auto_stories), null)
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            val bytes =
                                work.download?.downloadBytes ?: work.downloadTotal.takeIf { it > 0 }
                                    ?: work.total
                            if (bytes >
                                0
                            ) {
                                Text(
                                    fileSize(bytes),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    if (work.importComplete) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.verified_saved),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else if (preparingDocuments) {
                        CircularProgressIndicator()
                        Body(R.string.preparing_documents)
                    } else {
                        val progress = if (work.inAppDownload &&
                            state.importOrigin == Page.SETUP
                        ) {
                            work.setupProgress(readyKinds)
                        } else {
                            work.phaseProgress
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when {
                                    failed -> stringResource(R.string.download_interrupted)

                                    !running -> stringResource(R.string.transfer_paused_label)

                                    checking -> stringResource(R.string.checking_file)

                                    work.downloading -> stringResource(
                                        R.string.download_progress,
                                        fileSize(work.progress),
                                        fileSize(work.total)
                                    )

                                    else -> stringResource(R.string.download_install_title)
                                },
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                stringResource(
                                    R.string.progress_percent,
                                    (progress * 100).roundToInt()
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        SetupProgress(progress)
                    }
                }
            }
        }
        if (running) {
            if (!preparingDocuments && (work.downloading || !work.inAppDownload)) {
                OutlinedButton(
                    onClick = model::stop,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.pause_import), Modifier.padding(vertical = 6.dp))
                }
            }
        } else if (work.hasPendingTransfer) {
            if (failed) Body(work.notice ?: R.string.import_failed)
            PrimaryAction(
                if (failed) R.string.retry else R.string.resume_import,
                !work.busy,
                model::resumeImport
            )
            TextButton(onClick = model::finishImport) {
                Text(stringResource(R.string.continue_later))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (failed) Body(work.notice ?: R.string.import_failed)
                PrimaryAction(
                    if (state.importOrigin ==
                        Page.SETUP
                    ) {
                        R.string.return_setup
                    } else {
                        R.string.done
                    },
                    onClick = model::finishImport
                )
            }
        }
        if (running || work.hasPendingTransfer) {
            OutlinedButton(
                onClick = {
                    cancelling = true
                },
                enabled = !work.busy || running,
                modifier = Modifier.fillMaxWidth().heightIn(
                    min = 52.dp
                )
            ) {
                Text(
                    stringResource(R.string.import_cancel_action),
                    Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
    if (cancelling) {
        AlertDialog(
            onDismissRequest = { cancelling = false },
            title = { Text(stringResource(R.string.import_cancel_title)) },
            text = { Text(stringResource(R.string.import_cancel_body)) },
            confirmButton = {
                TextButton(onClick = {
                    cancelling = false
                    model.discardImport()
                }) { Text(stringResource(R.string.import_cancel_action)) }
            },
            dismissButton = {
                TextButton(onClick = { cancelling = false }) { Text(stringResource(R.string.back)) }
            }
        )
    }
}
