package dev.blobs.app.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.MenuRow
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductState
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.SectionLabel
import dev.blobs.app.ui.fileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(model: ProductViewModel, themeMode: ThemeMode) {
    val state by model.state.collectAsStateWithLifecycle()
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Heading(R.string.settings_title)
                Text(
                    stringResource(R.string.settings_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Image(painterResource(R.drawable.blob_wave), null, Modifier.size(76.dp))
        }
        Column {
            SectionLabel(R.string.settings_experience)
            MenuRow(
                R.string.appearance,
                stringResource(
                    R.string.appearance_summary,
                    theme,
                    (
                        state.options.textScale *
                            100
                        ).toInt()
                )
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
                R.string.history,
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
fun AppearanceScreen(state: ProductState, theme: ThemeMode, model: ProductViewModel) {
    ScrollPage {
        Heading(R.string.appearance_title)
        if (LocalDensity.current.fontScale < 1.5f) Body(R.string.appearance_body)
        val themes = listOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark
        )
        SectionLabel(R.string.appearance)
        if (LocalDensity.current.fontScale < 1.5f) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    themes.forEach { (mode, title) ->
                        Surface(
                            modifier = Modifier.weight(1f).selectable(
                                selected = theme == mode,
                                role = Role.RadioButton,
                                onClick = { model.theme(mode) }
                            ),
                            shape = RoundedCornerShape(24.dp),
                            color = if (theme ==
                                mode
                            ) {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Text(
                                stringResource(title),
                                Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        } else {
            themes.forEach { (mode, title) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = theme == mode,
                        role = Role.RadioButton,
                        onClick = { model.theme(mode) }
                    ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(title), Modifier.weight(1f))
                    RadioButton(selected = theme == mode, onClick = null)
                }
            }
        }
        SectionLabel(R.string.text_size)
        Text(stringResource(R.string.text_size_value, (state.options.textScale * 100).toInt()))
        var scale by remember(state.options.textScale) {
            mutableFloatStateOf(state.options.textScale)
        }
        val sizeLabel = stringResource(R.string.text_size)
        val sizeValue = stringResource(R.string.text_size_value, (scale * 100).toInt())
        Slider(
            modifier = Modifier.semantics {
                contentDescription = sizeLabel
                stateDescription = sizeValue
            },
            value = scale,
            onValueChange = {
                scale = it
            },
            onValueChangeFinished = { model.textScale(scale) },
            valueRange =
                1f..2f,
            steps = 3
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                stringResource(R.string.reading_preview),
                Modifier.fillMaxWidth().padding(20.dp),
                style = MaterialTheme.typography.bodyLarge
            )
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
fun AboutScreen() {
    val context = LocalContext.current
    val notices by produceState("") {
        value = withContext(Dispatchers.IO) {
            context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { file ->
                "$file\n\n" +
                    context.assets.open("licenses/$file").bufferedReader().use { it.readText() }
            }
        }
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)
    ) {
        item { Heading(R.string.about) }
        item { Body(R.string.about_body) }
        item { Heading(R.string.licenses) }
        val chunks = notices.chunked(2400)
        items(chunks.size) { index -> Text(chunks[index]) }
    }
}

@Composable
fun DataScreen(busy: Boolean, model: ProductViewModel, export: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    ScrollPage {
        Heading(R.string.history)
        Body(R.string.export_body)
        PrimaryAction(R.string.export, onClick = export)
        TextButton(onClick = {
            deleting = true
        }, enabled = !busy) { Text(stringResource(R.string.delete_all)) }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = {
                deleting = false
            },
            title = { Text(stringResource(R.string.delete_all)) },
            text = { Text(stringResource(R.string.delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    model.delete(null)
                    deleting = false
                }, enabled = !busy) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
