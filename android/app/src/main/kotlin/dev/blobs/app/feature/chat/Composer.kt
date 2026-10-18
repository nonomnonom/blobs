package dev.blobs.app.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.ProductState
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.fileSize

@Composable
internal fun Composer(
    state: ProductState,
    work: WorkState,
    followUp: Boolean,
    model: ProductViewModel,
    onCompose: () -> Unit
) {
    var choosingModel by remember { mutableStateOf(false) }
    val packages by model.packages.collectAsStateWithLifecycle()
    val selected = model.selectedModel()
    val modelTitle = selected?.title ?: stringResource(R.string.model_missing)
    val modelLabel = stringResource(R.string.model_choice, modelTitle)
    val sourceCount = model.selectedScope().orEmpty().size
    val scopeLabel = if (state.options.scope == "*") {
        stringResource(R.string.all_sources)
    } else {
        pluralStringResource(R.plurals.selected_sources, sourceCount, sourceCount)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(8.dp)) {
            val inputLabel =
                stringResource(if (followUp) R.string.follow_up_hint else R.string.question_hint)
            TextField(
                value = state.draft,
                onValueChange = {
                    onCompose()
                    model.draft(it)
                },
                placeholder = { Text(inputLabel, style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).semantics {
                    contentDescription =
                        inputLabel
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                enabled = state.loaded,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { choosingModel = true },
                    enabled = !work.busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                        contentDescription =
                            modelLabel
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Text(
                        modelTitle,
                        Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        null,
                        Modifier.padding(start = 6.dp).size(16.dp)
                    )
                }
                TextButton(
                    onClick = { model.navigate(Page.SOURCES) },
                    enabled = !work.busy,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription =
                            scopeLabel
                    }
                ) {
                    Text(
                        if (state.options.scope ==
                            "*"
                        ) {
                            stringResource(R.string.sources)
                        } else {
                            stringResource(R.string.sources_count, sourceCount)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        null,
                        Modifier.padding(start = 6.dp).size(16.dp)
                    )
                }
                val answering = work.busy && work.turnId.isNotEmpty()
                FilledIconButton(
                    onClick = { if (answering) model.stop() else model.send() },
                    enabled = answering || (state.draft.isNotBlank() && !work.busy && work.ready),
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        painterResource(
                            if (answering) R.drawable.ic_stop else R.drawable.ic_arrow_upward
                        ),
                        stringResource(if (answering) R.string.stop_answer else R.string.send),
                        Modifier.size(24.dp)
                    )
                }
            }
        }
    }
    if (choosingModel) {
        AlertDialog(
            onDismissRequest = { choosingModel = false },
            title = { Text(stringResource(R.string.models)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.models_help))
                    LazyColumn {
                        items(packages.filter { it.kind == "model" }, key = { it.key }) { pack ->
                            Row(
                                Modifier.fillMaxWidth().selectable(
                                    selected = selected?.key == pack.key,
                                    enabled = !work.busy,
                                    role = Role.RadioButton,
                                    onClick = {
                                        model.selectModel(pack)
                                        choosingModel = false
                                    }
                                ).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(pack.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        fileSize(pack.bytes),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                RadioButton(selected = selected?.key == pack.key, onClick = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    choosingModel = false
                    model.navigate(Page.MODELS)
                }) {
                    Text(stringResource(R.string.manage_models))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    choosingModel = false
                }) { Text(stringResource(R.string.done)) }
            }
        )
    }
}
