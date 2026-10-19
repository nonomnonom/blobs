package dev.blobs.app.feature.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.ScrollPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PrivacyScreen() {
    val paragraphs = stringResource(R.string.privacy_body).split("\n\n")
    val titles = listOf(R.string.privacy_local, R.string.privacy_connects, R.string.privacy_copy)
    ScrollPage {
        Heading(R.string.privacy_heading)
        paragraphs.forEachIndexed { index, paragraph ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                titles.getOrNull(index)?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.titleMedium)
                }
                Text(paragraph, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun HelpScreen(reducedMotion: Boolean) {
    val sections = stringResource(R.string.help_body).split("\n\n")
    var expanded by rememberSaveable { mutableStateOf<Int?>(0) }
    ScrollPage {
        Heading(R.string.help_heading)
        Body(R.string.help_description)
        sections.forEachIndexed { index, section ->
            val open = index == expanded
            val status = stringResource(if (open) R.string.expanded else R.string.collapsed)
            val rotation by animateFloatAsState(
                if (open) 180f else 0f,
                tween(if (reducedMotion) 0 else 180),
                label = "help-section"
            )
            Column(Modifier.animateContentSize(tween(if (reducedMotion) 0 else 180))) {
                Surface(
                    onClick = {
                        expanded = if (open) null else index
                    },
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.semantics { stateDescription = status }
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            section.substringBefore('\n'),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            painterResource(R.drawable.ic_expand_more),
                            null,
                            Modifier.rotate(rotation)
                        )
                    }
                }
                if (open) {
                    Text(
                        section.substringAfter('\n', ""),
                        Modifier.padding(bottom = 20.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                HorizontalDivider()
            }
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
    ScrollPage {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painterResource(R.drawable.welcome_world),
                null,
                Modifier.size(160.dp, 130.dp).clip(RoundedCornerShape(30.dp)),
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
        Body(R.string.about_description)
        Text(stringResource(R.string.made_possible_by), style = MaterialTheme.typography.titleLarge)
        listOf(
            R.string.credit_interface,
            R.string.credit_inference,
            R.string.credit_storage,
            R.string.credit_type
        ).forEach { credit ->
            Text(
                stringResource(credit),
                Modifier.padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider()
        }
        Heading(R.string.licenses)
        files.forEach { file ->
            TextButton(onClick = { selected = file }, modifier = Modifier.fillMaxWidth()) {
                Text(file.removeSuffix(".txt").replace('-', ' '), Modifier.fillMaxWidth())
            }
            HorizontalDivider()
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
