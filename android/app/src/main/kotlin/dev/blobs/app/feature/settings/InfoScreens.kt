package dev.blobs.app.feature.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.ui.DetailDisclosure
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.ScrollPage
import dev.blobs.app.ui.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InfoSection(@StringRes val title: Int, val details: List<Int>)

private val privacySections = listOf(
    InfoSection(R.string.privacy_local, listOf(R.string.privacy_local_detail)),
    InfoSection(
        R.string.privacy_connects,
        listOf(R.string.privacy_connects_detail, R.string.privacy_offline_detail)
    ),
    InfoSection(R.string.privacy_copy, listOf(R.string.privacy_copy_detail))
)

private val helpSections = listOf(
    InfoSection(R.string.help_start, listOf(R.string.help_start_detail)),
    InfoSection(R.string.help_ask, listOf(R.string.help_ask_detail)),
    InfoSection(R.string.help_sources, listOf(R.string.help_sources_detail)),
    InfoSection(R.string.help_library, listOf(R.string.help_library_detail)),
    InfoSection(R.string.help_stops, listOf(R.string.help_stops_detail)),
    InfoSection(R.string.help_model, listOf(R.string.help_model_detail)),
    InfoSection(R.string.help_keep, listOf(R.string.help_keep_detail))
)

@Composable
fun PrivacyScreen() {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    ScrollPage {
        Heading(R.string.privacy_heading)
        Text(
            stringResource(R.string.privacy_state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        privacySections.forEachIndexed { index, section ->
            HorizontalDivider()
            InfoDisclosure(section, open == index) { open = if (open == index) null else index }
        }
    }
}

@Composable
fun HelpScreen() {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    ScrollPage {
        Heading(R.string.help_heading)
        Text(
            stringResource(R.string.help_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        helpSections.forEachIndexed { index, section ->
            HorizontalDivider()
            InfoDisclosure(section, open == index) { open = if (open == index) null else index }
        }
    }
}

@Composable
private fun InfoDisclosure(section: InfoSection, open: Boolean, onToggle: () -> Unit) {
    DetailDisclosure(
        open = open,
        onOpenChange = { onToggle() },
        summary = {
            Text(
                stringResource(section.title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
        }
    ) {
        section.details.forEach { detail ->
            Text(stringResource(detail), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val files by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) { context.assets.list("licenses").orEmpty().sorted() }
    }
    val version by produceState("") {
        value = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >=
                33
            ) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName.orEmpty()
        }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    ScrollPage {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painterResource(R.drawable.welcome_world),
                null,
                Modifier.size(160.dp, 130.dp).clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Crop
            )
            Text(
                stringResource(R.string.app_wordmark),
                style = MaterialTheme.typography.displayLarge
            )
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DetailDisclosure(
            open = open == "how",
            onOpenChange = { open = if (open == "how") null else "how" },
            summary = {
                Text(
                    stringResource(R.string.about_how),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        ) {
            Text(
                stringResource(R.string.about_description_detail),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        DetailDisclosure(
            open = open == "credits",
            onOpenChange = { open = if (open == "credits") null else "credits" },
            summary = {
                Text(
                    stringResource(R.string.made_possible_by),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        ) {
            listOf(
                R.string.credit_interface_detail,
                R.string.credit_inference_detail,
                R.string.credit_storage_detail,
                R.string.credit_type_detail
            ).forEach { credit ->
                Text(stringResource(credit), style = MaterialTheme.typography.bodyMedium)
            }
        }
        SectionLabel(R.string.licenses)
        Column {
            files.forEach { file ->
                TextButton(
                    onClick = { selected = file },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        file.removeSuffix(".txt").replace('-', ' '),
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                HorizontalDivider()
            }
        }
    }
    selected?.let { file ->
        androidx.compose.runtime.key(file) { LicenseDialog(file) { selected = null } }
    }
}

@Composable
private fun LicenseDialog(file: String, close: () -> Unit) {
    val context = LocalContext.current
    val notice by produceState<Result<String>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licenses/$file").bufferedReader().use { it.readText() }
            }
        }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(file.removeSuffix(".txt").replace('-', ' ')) },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val result = notice
                when {
                    result == null -> item { Text(stringResource(R.string.loading)) }

                    result.isFailure -> item { Text(stringResource(R.string.notice_unavailable)) }

                    else -> items(result.getOrThrow().chunked(2400)) { chunk ->
                        SelectionContainer {
                            Text(chunk, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.done)) } }
    )
}
