package dev.blobs.app.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.GlyphButton
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.Mascot
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.SearchField
import dev.blobs.app.ui.theme.BlobsDimens
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(conversations: List<ConversationEntity>, busy: Boolean, model: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ConversationEntity?>(null) }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    val visible = conversations.filter {
        (it.title.isNotBlank() || it.draft.isNotBlank()) &&
            (it.title.contains(query, true) || it.draft.contains(query, true))
    }
    Column(
        Modifier.padding(
            start = BlobsDimens.page,
            end = BlobsDimens.page,
            top = BlobsDimens.sm
        ),
        verticalArrangement = Arrangement.spacedBy(BlobsDimens.md)
    ) {
        SearchField(query, R.string.search_history, {
            query = it
        })
        LazyColumn(
            contentPadding = PaddingValues(bottom = BlobsDimens.lg),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.xs)
        ) {
            if (visible.isEmpty()) {
                item {
                    Column(
                        Modifier.padding(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Mascot(R.drawable.mascot_reading, Modifier.size(110.dp))
                        Heading(
                            if (query.isBlank()) {
                                R.string.empty_history
                            } else {
                                R.string.history_no_match
                            }
                        )
                        Body(
                            if (query.isBlank()) {
                                R.string.empty_history_body
                            } else {
                                R.string.history_no_match_body
                            }
                        )
                        if (query.isNotBlank()) {
                            TextButton(onClick = {
                                query = ""
                            }) { Text(stringResource(R.string.clear_search)) }
                        } else {
                            PrimaryAction(R.string.ask_question) { model.newConversation() }
                        }
                    }
                }
            }
            items(visible, key = { it.id }) { item ->
                Surface(
                    onClick = {
                        model.openConversation(item)
                    },
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(BlobsDimens.md),
                        verticalArrangement = Arrangement.spacedBy(BlobsDimens.xs)
                    ) {
                        Text(
                            item.title.ifBlank {
                                item.draft.take(80)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(BlobsDimens.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (item.draft.isNotBlank()) {
                                    stringResource(
                                        R.string.history_draft,
                                        item.draft.take(100)
                                    )
                                } else {
                                    DateFormat.getDateInstance(
                                        DateFormat.MEDIUM
                                    ).format(Date(item.updatedAt))
                                },
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            GlyphButton(R.drawable.ic_edit_square, R.string.rename) {
                                renaming =
                                    item
                                title = item.title
                            }
                            GlyphButton(R.drawable.ic_delete, R.string.delete, !busy) {
                                deleting =
                                    item
                            }
                        }
                    }
                }
            }
        }
    }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = {
                deleting = null
            },
            title = { Text(stringResource(R.string.delete_conversation)) },
            text = { Text(stringResource(R.string.delete_conversation_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.delete(deleting)
                        deleting = null
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(title, {
                    title = it.take(120)
                }, label = { Text(stringResource(R.string.conversation_title)) })
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming?.let { model.rename(it, title) }
                    renaming =
                        null
                }, enabled = title.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
