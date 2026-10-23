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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DeviceProfile
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageRole
import dev.blobs.app.ui.AppViewModel
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.ModelLogo
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.SetupStep
import dev.blobs.app.ui.WorldScene
import dev.blobs.app.ui.fileSize
import dev.blobs.app.ui.theme.BlobsComponentShapes
import dev.blobs.app.ui.theme.BlobsDimens
import dev.blobs.app.ui.worldSkyColor

@Composable
fun SetupScreen(packages: List<PackageEntity>, model: AppViewModel, readyScroll: ScrollState) {
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
    androidx.compose.runtime.LaunchedEffect(work.download?.fileName) {
        work.download?.takeIf { it.manifest.kind == "model" }?.let { selectedFile = it.fileName }
    }
    val fits = model.catalog.packageFitsStorage(selected, profile.freeStorageBytes)
    val fitsMemory = model.catalog.packageFitsMemory(selected, profile.totalMemoryBytes)
    val installed = packages.find { it.key == selected.manifest.key }
    val starter = catalog.packages.single { it.role == PackageRole.STARTER }
    val hasStarter = model.catalog.hasStarterLibrary(packages)
    val starterFits = model.catalog.packageFitsStorage(starter, profile.freeStorageBytes)
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
                .fillMaxWidth().padding(BlobsDimens.page),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.lg)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(BlobsDimens.xs)) {
                        repeat(3) { index ->
                            Surface(
                                modifier = Modifier.width(BlobsDimens.setupProgressSegmentWidth)
                                    .height(BlobsDimens.setupProgressSegmentHeight),
                                shape = MaterialTheme.shapes.extraSmall,
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
                    Text(
                        pluralStringResource(
                            R.plurals.model_device_context,
                            model.contextWindowTokens,
                            model.contextWindowTokens
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(BlobsDimens.lg),
                            verticalArrangement = Arrangement.spacedBy(BlobsDimens.md)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ModelLogo(selected.manifest.title, 44.dp)
                                Text(
                                    stringResource(
                                        if (selected ==
                                            profile.recommendedModel
                                        ) {
                                            if (profile.recommendedModelFitsStorage && fitsMemory) {
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
                            if (fits && fitsMemory) {
                                Text(
                                    stringResource(R.string.runs_on_phone),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (!fitsMemory) {
                                Text(
                                    stringResource(R.string.model_memory_warning),
                                    style = MaterialTheme.typography.bodyMedium
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
                        Modifier.fillMaxWidth().height(if (large) 128.dp else 200.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                        size = if (large) 116.dp else 180.dp
                    )
                    Heading(R.string.setup_library_title)
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
                                stringResource(
                                    R.string.library_download_summary,
                                    starter.language,
                                    fileSize(starter.downloadBytes),
                                    fileSize(starter.manifest.bytes)
                                ),
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
                    if (!hasStarter && !starterFits) {
                        Text(
                            stringResource(
                                R.string.model_space_to_free,
                                fileSize(
                                    (
                                        model.catalog.requiredStorageBytes(starter) -
                                            profile.freeStorageBytes
                                        )
                                        .coerceAtLeast(0)
                                )
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = {
                            profile = DeviceProfile.inspect(context, model.catalog)
                        }) { Text(stringResource(R.string.check_storage)) }
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
                    pending -> PrimaryAction(
                        if (!work.transferActive && work.inAppDownload) {
                            R.string.resume_download
                        } else {
                            R.string.download_progress_action
                        }
                    ) {
                        if (!work.transferActive && work.inAppDownload) model.resumeImport()
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
                        work.ready && !work.busy && (fits || installed != null)
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

                    else -> PrimaryAction(
                        R.string.download_starter,
                        work.ready && !work.busy && starterFits
                    ) {
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
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(BlobsDimens.md),
            verticalArrangement = Arrangement.spacedBy(BlobsDimens.xs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
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
                    BlobsComponentShapes.worldPanel
                ).padding(BlobsDimens.page),
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.setup_all_set),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.setup_ready_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                Text(
                    stringResource(R.string.setup_ready_intro),
                    style = MaterialTheme.typography.bodyLarge,
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
                Modifier.fillMaxWidth().padding(BlobsDimens.page),
                verticalArrangement = Arrangement.spacedBy(BlobsDimens.md)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .92f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(BlobsDimens.md),
                        verticalArrangement = Arrangement.spacedBy(BlobsDimens.sm)
                    ) {
                        listOf(
                            modelTitle,
                            stringResource(R.string.starter_saved)
                        ).forEach { label ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(BlobsDimens.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(R.drawable.ic_check),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
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
