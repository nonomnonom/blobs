package dev.blobs.app.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductState
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.Scene
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.SetupProgress
import dev.blobs.app.ui.SetupProgressBlock
import dev.blobs.app.ui.fileSize

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
        if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleMedium)
        if (work.inAppDownload && state.importOrigin == Page.SETUP) {
            SetupProgressBlock(
                work.setupProgress(readyKinds),
                if (work.packageKind == "model") {
                    R.string.setup_model_step
                } else {
                    R.string.setup_library_step
                }
            )
        }
        if (running) {
            Body(
                when {
                    checking -> R.string.checking_file
                    work.downloading -> R.string.download_body
                    work.inAppDownload -> R.string.download_install_body
                    else -> R.string.import_body
                }
            )
            if (preparingDocuments) {
                CircularProgressIndicator()
                Body(R.string.preparing_documents)
            } else {
                if (state.importOrigin != Page.SETUP ||
                    !work.inAppDownload
                ) {
                    SetupProgress(work.phaseProgress)
                }
                Text(
                    stringResource(
                        if (work.downloading) {
                            R.string.download_progress
                        } else {
                            R.string.import_progress
                        },
                        fileSize(work.progress),
                        fileSize(work.total)
                    )
                )
            }
            if (!preparingDocuments) PrimaryAction(R.string.pause_import, onClick = model::stop)
            TextButton(onClick = {
                cancelling = true
            }) { Text(stringResource(R.string.import_cancel_action)) }
        } else if (work.hasPendingTransfer) {
            Body(if (failed) work.notice ?: R.string.import_failed else R.string.paused_import)
            PrimaryAction(
                if (failed) R.string.retry else R.string.resume_import,
                !work.busy,
                model::resumeImport
            )
            TextButton(onClick = model::finishImport) {
                Text(stringResource(R.string.continue_later))
            }
            TextButton(onClick = {
                cancelling = true
            }, enabled = !work.busy) { Text(stringResource(R.string.import_cancel_action)) }
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
