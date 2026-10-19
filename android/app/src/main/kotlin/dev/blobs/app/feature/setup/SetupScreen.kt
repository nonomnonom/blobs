package dev.blobs.app.feature.setup

import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DeviceProfile
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageRole
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.ModelLogo
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.SetupStep
import dev.blobs.app.ui.WorldScene
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.worldSkyColor

@Composable
fun SetupScreen(packages: List<PackageEntity>, model: ProductViewModel, readyScroll: ScrollState) {
    val state by model.state.collectAsStateWithLifecycle()
    val work by model.work.collectAsStateWithLifecycle()
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var profile by remember(context, catalog.packages) {
        mutableStateOf(DeviceProfile.inspect(context, model.catalog))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        profile =
            DeviceProfile.inspect(context, model.catalog)
    }
    val step = model.setupStep(state.setupStep)
    val available = catalog.packages.filter { it.manifest.kind == "model" }
    var selectedFile by rememberSaveable {
        mutableStateOf(
            available.find { it.manifest.key == state.options.model }?.fileName
                ?: available.find { it.manifest.key == model.selectedModel()?.key }?.fileName
                ?: profile.recommendedModel.fileName
        )
    }
    var comparing by rememberSaveable { mutableStateOf(false) }
    val selected = available.find { it.fileName == selectedFile } ?: profile.recommendedModel
    val fits = model.catalog.packageFitsStorage(selected, profile.freeStorageBytes)
    val installed = packages.find { it.key == selected.manifest.key }
    val starter = catalog.packages.single { it.role == PackageRole.STARTER }
    val hasStarter = model.catalog.hasStarterLibrary(packages)
    val pending = work.hasPendingTransfer || work.transferActive
    if (step == SetupStep.READY) {
        ReadyScreen(model.selectedModel()?.title.orEmpty(), readyScroll, model::finishSetup)
        return
    }
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    val scroll = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(step) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().then(if (large) Modifier.verticalScroll(scroll) else Modifier)) {
        Column(
            (if (large) Modifier else Modifier.weight(1f).verticalScroll(scroll))
                .fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (step != SetupStep.READY) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.setup_step, step.ordinal + 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { index ->
                            Surface(
                                modifier = Modifier.width(30.dp).height(4.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = if (index <=
                                    step.ordinal
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ) {}
                        }
                    }
                }
            }
            when (step) {
                SetupStep.MODEL -> {
                    Heading(R.string.setup_model_title)
                    Body(R.string.setup_model_intro)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ModelLogo(selected.manifest.title, 44.dp)
                                Text(
                                    stringResource(
                                        if (selected ==
                                            profile.recommendedModel
                                        ) {
                                            if (profile.recommendedModelFitsStorage) {
                                                R.string.recommended
                                            } else {
                                                R.string.model_smallest_option
                                            }
                                        } else {
                                            R.string.your_choice
                                        }
                                    ),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                selected.manifest.title,
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (selected.description.isNotBlank()) {
                                Text(
                                    selected.description,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.model_download_size,
                                    fileSize(selected.downloadBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (fits) {
                                Text(
                                    stringResource(R.string.runs_on_phone),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    TextButton(onClick = {
                        comparing = !comparing
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (comparing) R.string.hide_models else R.string.compare_models
                            )
                        )
                    }
                    if (comparing) {
                        available.forEach { option ->
                            ModelOption(option, option == selected, !work.busy) {
                                selectedFile =
                                    option.fileName
                            }
                        }
                    }
                    val device = if (Build.MODEL.contains("sdk_gphone", true) ||
                        Build.FINGERPRINT.contains("generic", true)
                    ) {
                        stringResource(R.string.this_phone)
                    } else {
                        "${Build.MANUFACTURER.replaceFirstChar(
                            Char::uppercase
                        )} ${Build.MODEL}".replace('_', ' ')
                    }
                    Text(
                        stringResource(
                            R.string.device_available,
                            device,
                            fileSize(profile.totalMemoryBytes),
                            fileSize(profile.freeStorageBytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!fits && installed == null) {
                        Text(
                            stringResource(
                                R.string.model_space_to_free,
                                fileSize(
                                    (
                                        model.catalog.requiredStorageBytes(selected) -
                                            profile.freeStorageBytes
                                        ).coerceAtLeast(0)
                                )
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = {
                            profile =
                                DeviceProfile.inspect(context, model.catalog)
                        }) { Text(stringResource(R.string.check_storage)) }
                    }
                }

                SetupStep.LIBRARY -> {
                    WorldScene(
                        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp)),
                        size = 180.dp
                    )
                    Heading(R.string.setup_library_title)
                    Body(R.string.setup_library_intro)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_auto_stories),
                            null
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                starter.manifest.title,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "${fileSize(starter.downloadBytes)} · ${starter.manifest.license}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (starter.description.isNotBlank()) {
                        Text(
                            starter.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                SetupStep.READY -> {}
            }
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    pending -> PrimaryAction(R.string.download_progress_action) {
                        model.navigate(Page.IMPORT)
                    }

                    step == SetupStep.READY -> PrimaryAction(R.string.setup_first_question) {
                        model.finishSetup()
                    }

                    step == SetupStep.MODEL -> PrimaryAction(
                        if (installed !=
                            null
                        ) {
                            R.string.continue_model
                        } else {
                            R.string.download_selected_model
                        },
                        !work.busy && (fits || installed != null)
                    ) {
                        if (installed !=
                            null
                        ) {
                            model.selectModel(installed)
                            model.continueSetup()
                        } else {
                            model.downloadPackage(selected)
                        }
                    }

                    hasStarter -> PrimaryAction(R.string.setup_continue) { model.continueSetup() }

                    else -> PrimaryAction(R.string.download_starter, !work.busy) {
                        model.downloadPackage(starter)
                    }
                }
                Text(
                    stringResource(
                        when (step) {
                            SetupStep.MODEL -> R.string.setup_model_once
                            SetupStep.LIBRARY -> R.string.setup_library_later
                            SetupStep.READY -> R.string.setup_ready_later
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelOption(
    option: DownloadablePackage,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick
        ),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelLogo(option.manifest.title)
                Text(
                    option.manifest.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                RadioButton(selected = selected, enabled = enabled, onClick = null)
            }
            Text(
                stringResource(R.string.model_download_size, fileSize(option.downloadBytes)),
                style = MaterialTheme.typography.bodySmall
            )
            Text(option.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReadyScreen(modelTitle: String, scroll: ScrollState, finish: () -> Unit) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = height),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.fillMaxWidth().background(
                    if (large) worldSkyColor() else Color.Transparent,
                    RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                ).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.setup_all_set),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.setup_ready_title),
                    style = MaterialTheme.typography.headlineLarge.let {
                        if (large) it.copy(fontSize = 27.sp, lineHeight = 32.sp) else it
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                Text(
                    stringResource(R.string.setup_ready_intro),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 23.sp
                    ),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            WorldScene(
                Modifier.fillMaxWidth().then(
                    if (large) {
                        Modifier.height(190.dp)
                    } else {
                        Modifier.weight(1f).heightIn(min = 240.dp)
                    }
                ).padding(bottom = 24.dp),
                pose = R.drawable.mascot_ready,
                celebrate = true,
                fullWorld = true,
                size = if (large) 170.dp else 240.dp
            )
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xEBFFFEFA),
                    contentColor = Color(0xFF292A32)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            modelTitle,
                            stringResource(R.string.starter_saved)
                        ).forEach { label ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(R.drawable.ic_check),
                                    null,
                                    tint = Color(0xFF145DE5)
                                )
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                PrimaryAction(R.string.setup_first_question, onClick = finish)
            }
        }
    }
}
