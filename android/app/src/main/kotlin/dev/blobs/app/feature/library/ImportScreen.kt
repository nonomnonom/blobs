package dev.blobs.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import dev.blobs.app.ui.fileSize

@Composable
fun ImportScreen(state: ProductState, work: WorkState, model: ProductViewModel) {
    var cancelling by rememberSaveable { mutableStateOf(false) }
    val running = work.busy && work.total > 0
    val checking = running && work.progress == work.total
    val failed = work.notice == R.string.import_failed || work.notice == R.string.storage_failed
    ScrollPage {
        Text(stringResource(R.string.import_boundary), style = MaterialTheme.typography.bodySmall)
        Scene(
            when {
                work.importComplete -> R.drawable.ready_world
                running -> R.drawable.library_world
                else -> R.drawable.pause_world
            },
            220
        )
        Heading(
            when {
                work.importComplete -> R.string.import_ready_title
                failed -> R.string.import_error_title
                checking -> R.string.import_checking_title
                running -> R.string.import_title
                work.pendingImport != null -> R.string.import_paused_title
                else -> R.string.import_cancelled
            }
        )
        val title = work.importTitle.ifBlank { work.pendingImport?.manifest?.title.orEmpty() }
        if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleMedium)
        if (running) {
            Body(R.string.import_body)
            LinearProgressIndicator(progress = {
                (work.progress.toFloat() / work.total).coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(
                    R.string.import_progress,
                    fileSize(work.progress),
                    fileSize(work.total)
                )
            )
            if (checking) Body(R.string.checking_file)
            PrimaryAction(R.string.pause_import, onClick = model::stop)
        } else if (work.pendingImport != null) {
            Body(if (failed) work.notice else R.string.paused_import)
            PrimaryAction(R.string.resume_import, !work.busy, model::resumeImport)
            TextButton(onClick = {
                cancelling = true
            }, enabled = !work.busy) { Text(stringResource(R.string.import_cancel_action)) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (failed) Body(work.notice)
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
