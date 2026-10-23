package dev.blobs.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.ui.AppUiState
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.MenuRow
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.SectionLabel
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsDimens

@Composable
fun SettingsScreen(model: AppViewModel, themeMode: ThemeMode) {
    val packages by model.packages.collectAsStateWithLifecycle()
    val conversations by model.conversations.collectAsStateWithLifecycle()
    val count = conversations.count { it.title.isNotBlank() || it.draft.isNotBlank() }
    val theme = stringResource(
        when (themeMode) {
            ThemeMode.LIGHT -> R.string.theme_light
            ThemeMode.DARK -> R.string.theme_dark
            else -> R.string.theme_system
        }
    )
    ScrollPage {
        Column {
            SectionLabel(R.string.settings_experience)
            MenuRow(
                R.string.appearance,
                theme
            ) { model.navigate(Page.APPEARANCE) }
            SectionLabel(R.string.settings_phone)
            MenuRow(
                R.string.models,
                model.selectedModel()?.let { stringResource(R.string.models_summary, it.title) }
                    ?: stringResource(R.string.model_missing)
            ) { model.navigate(Page.MODELS) }
            MenuRow(
                R.string.storage,
                stringResource(
                    R.string.storage_summary,
                    fileSize(
                        packages.sumOf {
                            it.bytes
                        }
                    )
                )
            ) { model.navigate(Page.STORAGE) }
            MenuRow(
                R.string.conversation_data,
                pluralStringResource(R.plurals.conversations_summary, count, count)
            ) {
                model.navigate(Page.DATA)
            }
            SectionLabel(R.string.settings_know)
            MenuRow(R.string.privacy, stringResource(R.string.privacy_summary)) {
                model.navigate(Page.PRIVACY)
            }
            MenuRow(R.string.help, stringResource(R.string.help_summary)) {
                model.navigate(Page.HELP)
            }
            MenuRow(R.string.about, stringResource(R.string.about_summary)) {
                model.navigate(Page.ABOUT)
            }
        }
    }
}

@Composable
fun AppearanceScreen(state: AppUiState, theme: ThemeMode, model: AppViewModel) {
    ScrollPage {
        Body(R.string.appearance_body)
        val themes = listOf(
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
            ThemeMode.SYSTEM to R.string.theme_system
        )
        SectionLabel(R.string.appearance)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                if (LocalDensity.current.fontScale >= 1.5f) 6.dp else 10.dp
            )
        ) {
            themes.forEach { (mode, title) ->
                ThemeOption(mode, stringResource(title), theme == mode, Modifier.weight(1f)) {
                    model.theme(mode)
                }
            }
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                Modifier.fillMaxWidth().padding(BlobsDimens.lg),
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
            ) {
                Icon(
                    painterResource(R.drawable.ic_auto_stories),
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.reading_preview),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
                value = state.options.reducedMotion,
                role = Role.Switch,
                onValueChange = model::reducedMotion
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.less_motion), Modifier.weight(1f))
            Switch(checked = state.options.reducedMotion, onCheckedChange = null)
        }
        Body(R.string.less_motion_body)
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    title: String,
    selected: Boolean,
    modifier: Modifier,
    choose: () -> Unit
) {
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = choose
        ),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(
                    horizontal = if (LocalDensity.current.fontScale >= 1.5f) 6.dp else 8.dp,
                    vertical = 10.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val paper = Color(0xFFFFFEFA)
                val night = Color(0xFF20232B)
                val backdrop = when (mode) {
                    ThemeMode.LIGHT -> Brush.horizontalGradient(listOf(paper, paper))

                    ThemeMode.DARK -> Brush.horizontalGradient(listOf(night, night))

                    ThemeMode.SYSTEM -> Brush.horizontalGradient(
                        0f to paper,
                        .5f to paper,
                        .5f to night,
                        1f to night
                    )
                }
                Column(
                    Modifier.fillMaxWidth().height(
                        80.dp
                    ).clip(RoundedCornerShape(8.dp)).background(backdrop)
                        .padding(horizontal = 8.dp, vertical = 13.dp).clearAndSetSemantics { },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(3) { line ->
                        Box(
                            Modifier.fillMaxWidth(if (line == 0) .5f else .8f).height(9.dp)
                                .clip(RoundedCornerShape(3.dp)).background(
                                    if (line == 0) {
                                        if (mode ==
                                            ThemeMode.DARK
                                        ) {
                                            Color(0xFF90B9FF)
                                        } else {
                                            Color(0xFF145DE5)
                                        }
                                    } else {
                                        if (mode ==
                                            ThemeMode.DARK
                                        ) {
                                            Color(0xFF444B5A)
                                        } else {
                                            Color(0xFFE6EFFF)
                                        }
                                    }
                                )
                        )
                    }
                }
                Text(
                    title,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                )
            }
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    null,
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(9.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DataScreen(busy: Boolean, model: AppViewModel, export: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    ScrollPage {
        Heading(R.string.data_heading)
        Body(R.string.export_body)
        val conversations by model.conversations.collectAsStateWithLifecycle()
        val count = conversations.count { it.title.isNotBlank() || it.draft.isNotBlank() }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                Modifier.fillMaxWidth().padding(BlobsDimens.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_chat_bubble),
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(count.toString(), style = MaterialTheme.typography.displayLarge)
                Text(
                    pluralStringResource(R.plurals.saved_conversations_label, count),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (busy) Body(R.string.busy_help)
        PrimaryAction(R.string.export, count > 0, onClick = export)
        HorizontalDivider(Modifier.padding(top = 20.dp))
        Text(
            stringResource(R.string.clear_conversations),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.data_keeps_files),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { deleting = true },
            enabled = !busy && count > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) { Text(stringResource(R.string.delete_all), Modifier.padding(vertical = 6.dp)) }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = {
                deleting = false
            },
            title = { Text(stringResource(R.string.delete_all)) },
            text = { Text(stringResource(R.string.delete_all_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.delete(null)
                        deleting = false
                    },
                    enabled = !busy,
                    colors = TextButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
