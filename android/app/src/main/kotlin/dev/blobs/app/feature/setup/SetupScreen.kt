package dev.blobs.app.feature.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blobs.app.R
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DeviceProfile
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageRole
import dev.blobs.app.feature.library.OptionalLibraryOption
import dev.blobs.app.ui.Body
import dev.blobs.app.ui.Heading
import dev.blobs.app.ui.Page
import dev.blobs.app.ui.PrimaryAction
import dev.blobs.app.ui.ProductViewModel
import dev.blobs.app.ui.Scene
import dev.blobs.app.ui.SetupProgressBlock
import dev.blobs.app.ui.fileSize

@Composable
fun SetupScreen(packages: List<PackageEntity>, model: ProductViewModel) {
    val work by model.work.collectAsStateWithLifecycle()
    val catalog by model.catalogState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var profile by remember(context, model.catalog, catalog.packages) {
        mutableStateOf(DeviceProfile.inspect(context, model.catalog))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        profile = DeviceProfile.inspect(context, model.catalog)
    }
    val hasModel = packages.any { it.kind == "model" }
    val hasStarter = model.catalog.hasStarterLibrary(packages)
    val installedIds = packages.map { it.packageId to it.version }.toSet()
    val remainingLibraries = catalog.packages.filter { it.role == PackageRole.OPTIONAL }.filter {
        (it.manifest.id to it.manifest.version) !in installedIds
    }
    val setupReadyKinds = buildSet {
        if (hasModel) add("model")
        if (hasStarter) add("library")
    }
    val needsModel = !hasModel
    val needsStarter = !hasStarter
    val ready = !needsModel && !needsStarter && remainingLibraries.isEmpty()
    var selectedModelFile by rememberSaveable {
        mutableStateOf(profile.recommendedModel.fileName)
    }
    val availableModels = catalog.packages.filter { it.manifest.kind == "model" }
        .sortedWith(
            compareByDescending<DownloadablePackage> {
                it == profile.recommendedModel
            }.thenBy { it.downloadBytes }
        )
    val starterLibrary = catalog.packages.single { it.role == PackageRole.STARTER }
    val selectedModel = availableModels.firstOrNull { it.fileName == selectedModelFile }
        ?: profile.recommendedModel
    val selectedModelFitsStorage = model.catalog.packageFitsStorage(
        selectedModel,
        profile.freeStorageBytes
    )
    val progressLabel = when {
        ready -> R.string.setup_all_ready
        needsModel -> R.string.setup_model_step
        needsStarter -> R.string.setup_library_step
        else -> R.string.setup_extras_step
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (work.inAppDownload) {
                SetupProgressBlock(work.setupProgress(setupReadyKinds), progressLabel)
            }
            Scene(
                when {
                    ready -> R.drawable.ready_world
                    needsModel -> R.drawable.welcome_world
                    else -> R.drawable.library_world
                },
                if (needsModel) 124 else 200
            )
            when {
                ready -> {
                    Heading(R.string.setup_ready_title)
                    Body(R.string.ready_body)
                }
                work.pendingImport != null || (work.busy && work.total > 0) -> {
                    Heading(R.string.download_in_progress)
                    Body(R.string.download_in_progress_body)
                }
                needsModel -> {
                    Heading(R.string.setup_model_title)
                    Body(
                        if (profile.recommendedModelFitsStorage) {
                            R.string.setup_model_body
                        } else {
                            R.string.setup_model_body_no_space
                        }
                    )
                    availableModels.forEach { option ->
                        ModelOption(
                            option,
                            option == profile.recommendedModel,
                            profile.recommendedModelFitsStorage,
                            option.fileName == selectedModel.fileName,
                            onClick = { selectedModelFile = option.fileName }
                        )
                    }
                }
                needsStarter -> {
                    Heading(R.string.setup_library_title)
                    Body(R.string.setup_library_body)
                    Text(
                        stringResource(
                            R.string.download_size_note,
                            fileSize(starterLibrary.downloadBytes)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                remainingLibraries.isNotEmpty() -> {
                    Heading(R.string.setup_extras_title)
                    Body(R.string.setup_extras_body)
                    remainingLibraries.forEach { library ->
                        OptionalLibraryOption(library, !work.busy) {
                            model.downloadPackage(library)
                        }
                    }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    ready -> PrimaryAction(R.string.start_chat) { model.finishSetup() }
                    work.pendingImport != null || (work.busy && work.total > 0) ->
                        PrimaryAction(R.string.download_progress_action) {
                            model.navigate(Page.IMPORT)
                        }
                    needsModel -> {
                        Text(
                            if (selectedModelFitsStorage) {
                                stringResource(
                                    R.string.model_download_size,
                                    fileSize(selectedModel.downloadBytes)
                                )
                            } else {
                                stringResource(
                                    R.string.model_space_to_free,
                                    fileSize(
                                        (
                                            model.catalog.requiredStorageBytes(selectedModel) -
                                                profile.freeStorageBytes
                                            ).coerceAtLeast(0)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PrimaryAction(
                            R.string.download_selected_model,
                            !work.busy && selectedModelFitsStorage
                        ) { model.downloadPackage(selectedModel) }
                    }
                    needsStarter -> PrimaryAction(
                        R.string.download_starter,
                        !work.busy
                    ) { model.downloadPackage(starterLibrary) }
                    else -> PrimaryAction(R.string.continue_without_extras, !work.busy) {
                        model.finishSetup()
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelOption(
    option: DownloadablePackage,
    recommended: Boolean,
    recommendationFitsStorage: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        option.manifest.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (recommended) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                stringResource(
                                    if (recommendationFitsStorage) {
                                        R.string.recommended
                                    } else {
                                        R.string.model_smallest_option
                                    }
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    stringResource(
                        R.string.model_download_size,
                        fileSize(option.downloadBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
