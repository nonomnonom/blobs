package dev.blobs.app.ui

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.blobs.app.R
import kotlin.math.roundToInt

@Composable
fun GlyphButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp)) {
        Icon(painterResource(icon), stringResource(label), modifier = Modifier.size(24.dp))
    }
}

@Composable
fun PrimaryAction(@StringRes label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Text(stringResource(label), modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
fun SetupProgress(progress: Float) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth()
            .heightIn(min = 8.dp, max = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
    ) {
        Box(
            Modifier.fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun SetupProgressBlock(progress: Float, @StringRes label: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(
                R.string.progress_percent,
                (progress.coerceIn(0f, 1f) * 100).roundToInt()
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SetupProgress(progress)
}

@Composable
fun Heading(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics {
            heading()
        }
    )
}

@Composable
fun Body(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

@Composable
fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content
    )
}

@Composable
fun Art(@DrawableRes image: Int, height: Int = 220) {
    Image(
        painterResource(image),
        null,
        modifier = Modifier.fillMaxWidth().heightIn(max = height.dp)
    )
}

@Composable
fun Scene(@DrawableRes image: Int, height: Int = 220) {
    Image(
        painterResource(image),
        null,
        modifier = Modifier.fillMaxWidth().heightIn(min = height.dp, max = height.dp)
            .clip(RoundedCornerShape(28.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun MenuRow(
    @StringRes title: Int,
    detail: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                painterResource(R.drawable.ic_arrow_forward),
                null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun Notice(@StringRes text: Int, dismiss: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = dismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
fun SectionLabel(@StringRes title: Int) {
    Text(
        stringResource(title),
        Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun SearchField(value: String, @StringRes hint: Int, onValueChange: (String) -> Unit) {
    val label = stringResource(hint)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(22.dp)) },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = label
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun SourceRow(number: Int, title: String, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    number.toString(),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Icon(
                painterResource(R.drawable.ic_arrow_forward),
                null,
                Modifier.padding(start = 12.dp).size(20.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun fileSize(bytes: Long): String = Formatter.formatFileSize(LocalContext.current, bytes)
