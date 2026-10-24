package dev.blobs.app.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.blobs.app.ui.AppUiState
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.DetailDisclosure
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.ModelLogo
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.Scene
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.SetupProgress
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsDimens
import kotlin.math.roundToInt

@Composable
fun ImportScreen(state: AppUiState, work: WorkState, model: AppViewModel) {
    var cancelling by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val packages by model.packages.collectAsStateWithLifecycle()
    val readyKinds = buildSet {
        if (packages.any { it.kind == "model" }) add("model")
        if (model.catalog.hasStarterLibrary(packages)) add("library")
    }
    val preparingDocuments = work.transferActive &&
        work.packageKind == "library" &&
        !work.inAppDownload &&
        work.total == 0L
    val discardLabel = if (work.inAppDownload) {
        R.string.discard_download
    } else {
        R.string.import_cancel_action
    }
    val discardTitle = if (work.inAppDownload) {
        R.string.discard_download_title
    } else {
        R.string.import_cancel_title
    }
    val pauseLabel = if (work.inAppDownload) R.string.pause_download else R.string.pause_import
    val running = work.transferActive
    val checking = running && work.total > 0 && work.progress == work.total
    LaunchedEffect(state.page, work.importComplete, work.inAppDownload) {
        if (state.page == Page.IMPORT && work.importComplete && work.inAppDownload) {
            model.finishImport(preserveNotice = true)
        }
    }
    BackHandler(enabled = state.page == Page.IMPORT) { model.finishImport() }
    val modelIncompatible = work.notice in setOf(
        R.string.model_context_incompatible,
        R.string.model_incompatible_detail
    )
    val failed = work.notice in setOf(
        R.string.import_failed,
        R.string.storage_failed,
        R.string.download_failed,
        R.string.download_source_failed,
        R.string.model_context_incompatible,
        R.string.model_incompatible_detail
    )
    val progress = when {
        work.downloading && work.total > 0 ->
            (work.progress.toDouble() / work.total).toFloat().coerceIn(0f, 1f)

        work.inAppDownload && state.importOrigin == Page.SETUP -> work.setupProgress(readyKinds)

        else -> work.phaseProgress
    }
    val stateLine = when {
        work.importComplete -> stringResource(R.string.import_state_saved)

        failed -> stringResource(R.string.import_state_interrupted)

        preparingDocuments -> stringResource(R.string.import_state_preparing)

        checking -> stringResource(R.string.import_state_checking)

        running && work.total > 0 -> stringResource(
            R.string.import_state_progress,
            stringResource(R.string.progress_percent, (progress * 100).roundToInt()),
            fileSize(work.progress),
            fileSize(work.total)
        )

        running -> stringResource(R.string.import_state_working)

        work.hasPendingTransfer -> stringResource(R.string.import_state_paused)

        else -> stringResource(R.string.import_state_idle)
    }
    ScrollPage {
        Scene(
            when {
                work.importComplete -> R.drawable.ready_world
                failed || (!running && work.hasPendingTransfer) -> R.drawable.pause_world
                else -> R.drawable.library_world
            },
            168
        )
        if (!work.importComplete) {
            Heading(
                when {
                    failed -> R.string.import_error_title

                    !running && work.hasPendingTransfer -> if (work.inAppDownload) {
                        R.string.download_title
                    } else {
                        R.string.import_title
                    }

                    checking -> R.string.import_checking_title

                    work.downloading -> R.string.download_title

                    work.inAppDownload && running -> R.string.download_install_title

                    running -> R.string.import_title

                    work.pendingImport != null -> R.string.import_title

                    else -> R.string.import_cancelled
                }
            )
        }
        Text(
            stateLine,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val title = work.importTitle.ifBlank { work.pendingImport?.manifest?.title.orEmpty() }
        val body = when {
            work.importComplete -> R.string.download_saved_detail
            failed -> R.string.download_error_detail
            !running && work.hasPendingTransfer -> null
            checking -> R.string.checking_file
            work.downloading -> R.string.download_detail
            work.inAppDownload && running -> R.string.download_install_detail
            running -> R.string.import_detail
            else -> R.string.storage_keeps_answers
        }
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
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(BlobsDimens.lg),
                    verticalArrangement = Arrangement.spacedBy(BlobsDimens.md)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
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
                    when {
                        work.importComplete -> Unit
                        preparingDocuments -> CircularProgressIndicator()
                        running || failed -> SetupProgress(progress)
                    }
                }
            }
        }
        val details = listOfNotNull(body, work.notice.takeIf { failed }).distinct()
        if (details.isNotEmpty()) {
            DetailDisclosure(
                open = detailsOpen,
                onOpenChange = { detailsOpen = it },
                summary = {
                    Text(
                        stringResource(R.string.import_details),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            ) {
                details.forEach { detail ->
                    Text(
                        stringResource(detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (running) {
            if (!preparingDocuments && (work.downloading || !work.inAppDownload)) {
                OutlinedButton(
                    onClick = model::stop,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(
                        stringResource(pauseLabel),
                        Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        } else if (work.hasPendingTransfer) {
            PrimaryAction(
                if (modelIncompatible) {
                    R.string.models
                } else if (failed) {
                    R.string.retry
                } else if (work.inAppDownload) {
                    R.string.resume_download
                } else {
                    R.string.resume_import
                },
                !work.busy,
                if (modelIncompatible) {
                    {
                        model.finishImport()
                        model.navigate(Page.MODELS)
                    }
                } else {
                    model::resumeImport
                }
            )
            TextButton(onClick = model::finishImport) {
                Text(stringResource(R.string.continue_later))
            }
        } else {
            PrimaryAction(
                if (state.importOrigin == Page.SETUP && work.importComplete &&
                    work.packageKind == "model" && !model.catalog.hasStarterLibrary(packages)
                ) {
                    R.string.setup_next_library
                } else if (state.importOrigin == Page.SETUP) {
                    R.string.return_setup
                } else {
                    R.string.done
                },
                onClick = model::finishImport
            )
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
                    stringResource(
                        discardLabel
                    ),
                    Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
    if (cancelling) {
        AlertDialog(
            onDismissRequest = { cancelling = false },
            title = {
                Text(
                    stringResource(
                        discardTitle
                    )
                )
            },
            text = { Text(stringResource(R.string.import_cancel_body)) },
            confirmButton = {
                TextButton(onClick = {
                    cancelling = false
                    model.discardImport()
                }) {
                    Text(
                        stringResource(
                            discardLabel
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelling = false }) { Text(stringResource(R.string.back)) }
            }
        )
    }
}
