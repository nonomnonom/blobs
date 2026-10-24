package dev.blobs.app.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.ui.AppUiState
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.ModelLogo
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsDimens
import dev.blobs.app.ui.theme.BlobsMotion

@Composable
internal fun AnswerDisclaimer(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.answer_check),
        modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun Composer(
    state: AppUiState,
    work: WorkState,
    followUp: Boolean,
    model: AppViewModel,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {}
) {
    val selected = model.selectedModel()
    val modelTitle = selected?.title ?: stringResource(R.string.model_missing)
    val modelLabel = stringResource(R.string.model_choice, modelTitle)
    val selectedLibraries = model.selectedLibraryScope()
    val libraryCount = selectedLibraries?.size ?: 0
    val scopeLabel = when {
        selectedLibraries == null -> stringResource(R.string.all_libraries)
        selectedLibraries.isEmpty() -> stringResource(R.string.offline_only)
        else -> stringResource(R.string.libraries_count, libraryCount)
    }
    val large = LocalDensity.current.fontScale >= 1.5f
    var focused by remember { mutableStateOf(false) }
    val outline by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(BlobsMotion.QUICK),
        label = "composer-outline"
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (focused) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, outline),
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier.padding(BlobsDimens.xs),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.xxs)
        ) {
            val inputLabel =
                stringResource(if (followUp) R.string.follow_up_hint else R.string.question_hint)
            TextField(
                value = state.draft,
                onValueChange = model::draft,
                placeholder = { Text(inputLabel, style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.fillMaxWidth().heightIn(
                    min = 64.dp,
                    max = if (large) 240.dp else 140.dp
                )
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocusChange(it.hasFocus)
                    }
                    .semantics { contentDescription = inputLabel },
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
            if (large) {
                ModelPicker(
                    modelTitle,
                    modelLabel,
                    selected,
                    model,
                    !work.busy,
                    Modifier.fillMaxWidth(),
                    true
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BlobsDimens.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!large) {
                    ModelPicker(
                        modelTitle,
                        modelLabel,
                        selected,
                        model,
                        !work.busy,
                        Modifier.weight(1f),
                        false
                    )
                }
                TextButton(
                    onClick = { model.navigate(Page.SOURCES) },
                    enabled = !work.busy,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = (if (large) Modifier.weight(1f) else Modifier).heightIn(min = 48.dp)
                        .semantics { contentDescription = scopeLabel }
                ) {
                    Icon(
                        painterResource(R.drawable.ic_auto_stories),
                        null,
                        Modifier.padding(end = 6.dp).size(16.dp)
                    )
                    Text(
                        scopeLabel,
                        style = MaterialTheme.typography.bodySmall
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
}

@Composable
private fun ModelPicker(
    title: String,
    label: String,
    selected: PackageEntity?,
    model: AppViewModel,
    enabled: Boolean,
    modifier: Modifier,
    large: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val packages by model.packages.collectAsStateWithLifecycle()
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    val installedModels = packages.filter { it.kind == "model" }
    val installedKeys = installedModels.mapTo(mutableSetOf()) { it.key }
    val downloadableModels = catalog.packages.filter {
        it.manifest.kind == "model" && it.manifest.key !in installedKeys
    }

    Box(modifier) {
        ModelPickerButton(title, label, enabled, Modifier.fillMaxWidth(), large) {
            expanded = true
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            installedModels.forEach { pack ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(pack.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                fileSize(pack.bytes),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    leadingIcon = { ModelLogo(pack.title, 24.dp) },
                    trailingIcon = if (pack.key == selected?.key) {
                        {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        model.selectModel(pack)
                    },
                    enabled = enabled
                )
            }
            if (downloadableModels.isNotEmpty()) {
                if (installedModels.isNotEmpty()) HorizontalDivider()
                downloadableModels.forEach { asset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    asset.manifest.title,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    stringResource(R.string.download_model),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        leadingIcon = { ModelLogo(asset.manifest.title, 24.dp) },
                        onClick = {
                            expanded = false
                            model.navigate(Page.MODELS)
                        },
                        enabled = enabled
                    )
                }
            }
            if (installedModels.isNotEmpty() || downloadableModels.isNotEmpty()) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_models)) },
                onClick = {
                    expanded = false
                    model.navigate(Page.MODELS)
                }
            )
        }
    }
}

@Composable
private fun ModelPickerButton(
    title: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    large: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = label },
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        ModelLogo(title, 24.dp)
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = 6.dp),
            maxLines = if (large) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
        Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(16.dp))
    }
}
