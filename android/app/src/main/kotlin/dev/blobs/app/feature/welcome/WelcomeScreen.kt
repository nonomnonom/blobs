package dev.blobs.app.feature.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.blobs.app.R
import dev.blobs.app.data.packages.DeviceProfile
import dev.blobs.app.data.packages.PackageCatalog
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.fileSize

@Composable
fun WelcomeScreen(
    catalog: PackageCatalog,
    preferencesUnavailable: Boolean = false,
    prepare: () -> Unit = {}
) {
    val context = LocalContext.current
    var profile by androidx.compose.runtime.remember(context, catalog) {
        mutableStateOf(DeviceProfile.inspect(context, catalog))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        profile = DeviceProfile.inspect(context, catalog)
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer)) {
            Image(
                painter = painterResource(R.drawable.welcome_world),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = stringResource(R.string.app_wordmark),
                style = MaterialTheme.typography.displayLarge,
                color = androidx.compose.ui.graphics.Color(0xFF292A32),
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
            )
            Spacer(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    )
            )
        }
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.diagnosis_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            Text(
                text = stringResource(R.string.diagnosis_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(
                    R.string.device_details,
                    deviceLabel(),
                    fileSize(profile.totalMemoryBytes),
                    fileSize(profile.freeStorageBytes)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(
                            if (profile.recommendedModelFitsStorage) {
                                R.string.model_recommendation
                            } else {
                                R.string.model_smallest_option
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        profile.recommendedModel.manifest.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (profile.recommendedModelFitsStorage) {
                            stringResource(
                                R.string.model_recommendation_reason,
                                fileSize(profile.recommendedModel.downloadBytes)
                            )
                        } else {
                            stringResource(
                                R.string.model_space_requirement,
                                fileSize(catalog.requiredStorageBytes(profile.recommendedModel)),
                                fileSize(profile.freeStorageBytes)
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            PrimaryAction(R.string.choose_model, onClick = prepare)
            Body(R.string.device_check_private)
            if (preferencesUnavailable) {
                Text(
                    text = stringResource(R.string.preferences_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

private fun deviceLabel(): String {
    val model = android.os.Build.MODEL
    return if (
        model.contains("sdk_gphone", ignoreCase = true) ||
        android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true)
    ) {
        "This phone"
    } else {
        "${android.os.Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} $model"
            .replace('_', ' ')
    }
}
