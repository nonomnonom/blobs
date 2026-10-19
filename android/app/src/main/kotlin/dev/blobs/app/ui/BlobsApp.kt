package dev.blobs.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.blobs.app.AppContainer
import dev.blobs.app.R
import dev.blobs.app.feature.chat.ChatScreen
import dev.blobs.app.feature.history.HistoryScreen
import dev.blobs.app.feature.library.BrowseScreen
import dev.blobs.app.feature.library.ImportReviewScreen
import dev.blobs.app.feature.library.ImportScreen
import dev.blobs.app.feature.library.LibraryScreen
import dev.blobs.app.feature.library.ModelsScreen
import dev.blobs.app.feature.library.ReaderScreen
import dev.blobs.app.feature.settings.AboutScreen
import dev.blobs.app.feature.settings.AppearanceScreen
import dev.blobs.app.feature.settings.DataScreen
import dev.blobs.app.feature.settings.HelpScreen
import dev.blobs.app.feature.settings.PrivacyScreen
import dev.blobs.app.feature.settings.SettingsScreen
import dev.blobs.app.feature.settings.StorageScreen
import dev.blobs.app.feature.setup.SetupScreen
import dev.blobs.app.feature.welcome.WelcomeScreen
import dev.blobs.app.ui.theme.BlobsTheme

@Composable
fun BlobsApp(container: AppContainer) {
    val themeModel: BlobsViewModel =
        viewModel(
            factory = viewModelFactory {
                initializer { BlobsViewModel(container.preferences) }
            }
        )
    val model: ProductViewModel =
        viewModel(factory = viewModelFactory { initializer { ProductViewModel(container) } })
    val theme by themeModel.uiState.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val work by model.work.collectAsStateWithLifecycle()
    val packages by model.packages.collectAsStateWithLifecycle()
    val turns by model.turns.collectAsStateWithLifecycle()
    val thread by model.conversationId.collectAsStateWithLifecycle()
    val conversations by model.conversations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exportingCollection by remember { mutableStateOf<String?>(null) }
    val documentContract = remember {
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent =
                super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }
    val picker = rememberLauncherForActivityResult(documentContract) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some local providers grant access only for the current app session.
            }
            model.inspect(it)
        }
    }
    val exporter =
        rememberLauncherForActivityResult(
            LocalCreateDocument("text/markdown")
        ) { uri ->
            uri?.let { model.export(context, it) }
        }
    val recoveryExporter =
        rememberLauncherForActivityResult(
            LocalCreateDocument("application/zip")
        ) { uri ->
            uri?.let { model.exportRecovery(context, it) }
        }
    val collectionExporter = rememberLauncherForActivityResult(
        LocalCreateDocument("application/json")
    ) { uri ->
        val key = exportingCollection
        if (uri != null && key != null) {
            packages.firstOrNull { it.key == key }?.let { model.exportCollection(context, it, uri) }
        }
        exportingCollection = null
    }
    val localDocumentsContract = remember {
        object : ActivityResultContracts.OpenMultipleDocuments() {
            override fun createIntent(context: Context, input: Array<String>): Intent =
                super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }
    val documentsPicker = rememberLauncherForActivityResult(localDocumentsContract) { uris ->
        if (uris.isNotEmpty()) model.importDocuments(uris, context)
    }
    val pickFile = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    val pickDocuments = { documentsPicker.launch(arrayOf("*/*")) }
    val exportCollection: (dev.blobs.app.data.local.PackageEntity) -> Unit = { pack ->
        exportingCollection = pack.key
        val fileName = pack.title.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80)
        collectionExporter.launch("$fileName.blobs.json")
    }
    val density = LocalDensity.current
    val reviewingImport = state.importUri != null
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.page, reviewingImport) {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }
    BackHandler(
        enabled = reviewingImport || (state.page != Page.CHAT && state.page != Page.WELCOME)
    ) {
        if (reviewingImport) model.dismissImport() else model.back()
    }
    val lessMotion = state.options.reducedMotion || systemLessMotion()
    val welcomeScroll = rememberScrollState()
    val readyScroll = rememberScrollState()
    BlobsTheme(mode = theme.themeMode) {
        CompositionLocalProvider(
            LocalLessMotion provides lessMotion,
            LocalDensity provides
                Density(density.density, density.fontScale * state.options.textScale)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ExperienceCanvas(
                    world = !state.options.welcomed || state.page == Page.WELCOME ||
                        (
                            state.page == Page.SETUP &&
                                model.setupStep(state.setupStep) == SetupStep.READY
                            ),
                    chat = state.page == Page.CHAT,
                    home = turns.isEmpty(),
                    worldScroll = if (state.page == Page.SETUP) readyScroll else welcomeScroll
                ) {
                    AppNavigation(state, conversations, thread, model) { openNavigation ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            if (!state.loaded) {
                                Column(
                                    Modifier.padding(32.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    if (state.notice == null && work.notice == null) {
                                        CircularProgressIndicator()
                                        Text(stringResource(R.string.loading))
                                    }
                                    (state.notice ?: work.notice)?.let {
                                        Notice(it, model::dismissNotice)
                                    }
                                    if (state.notice != null || work.notice != null) {
                                        Body(R.string.recovery_copy_body)
                                        PrimaryAction(R.string.recovery_copy) {
                                            recoveryExporter.launch("blobs-recovery.zip")
                                        }
                                    }
                                }
                            } else if (!state.options.welcomed || state.page == Page.WELCOME) {
                                WelcomeScreen(
                                    theme.preferencesUnavailable,
                                    scroll = welcomeScroll,
                                    prepare = model::welcome
                                )
                            } else {
                                Column(
                                    Modifier.widthIn(
                                        max = 720.dp
                                    ).fillMaxSize().safeDrawingPadding().imePadding()
                                ) {
                                    AppHeader(state, turns.isEmpty(), model, openNavigation)
                                    if (!reviewingImport && state.page != Page.IMPORT) {
                                        (state.notice ?: work.notice)?.let {
                                            Notice(it, model::dismissNotice)
                                        }
                                        if (work.transferActive || work.hasPendingTransfer) {
                                            androidx.compose.material3.TextButton(onClick = {
                                                model.navigate(Page.IMPORT)
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    if (work.transferActive) {
                                                        stringResource(
                                                            R.string.downloading_package,
                                                            work.importTitle,
                                                            (work.phaseProgress * 100).toInt()
                                                        )
                                                    } else {
                                                        stringResource(R.string.paused_import)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    val holder = rememberSaveableStateHolder()
                                    Crossfade(
                                        state.page to reviewingImport,
                                        animationSpec = tween(
                                            if (lessMotion) 0 else 220
                                        ),
                                        label = "page",
                                        modifier = Modifier.weight(1f)
                                    ) { (page, reviewing) ->
                                        holder.SaveableStateProvider(
                                            if (reviewing) {
                                                "import-review"
                                            } else if (page ==
                                                Page.CHAT
                                            ) {
                                                "chat-$thread"
                                            } else if (page ==
                                                Page.READER
                                            ) {
                                                "reader-${state.reader?.id}-" +
                                                    state.reader?.text.hashCode()
                                            } else {
                                                page.name
                                            }
                                        ) {
                                            if (reviewing) {
                                                ImportReviewScreen(state, packages, model)
                                            } else {
                                                when (page) {
                                                    Page.CHAT -> ChatScreen(
                                                        state,
                                                        turns,
                                                        work,
                                                        model
                                                    )

                                                    Page.WELCOME -> Unit

                                                    Page.SETUP -> SetupScreen(
                                                        packages,
                                                        model,
                                                        readyScroll
                                                    )

                                                    Page.IMPORT -> ImportScreen(state, work, model)

                                                    Page.HISTORY -> HistoryScreen(
                                                        conversations,
                                                        work.busy,
                                                        model
                                                    )

                                                    Page.LIBRARY -> LibraryScreen(
                                                        packages,
                                                        model,
                                                        pickFile,
                                                        pickDocuments,
                                                        exportCollection
                                                    )

                                                    Page.MODELS -> ModelsScreen(
                                                        packages,
                                                        work,
                                                        model,
                                                        pickFile
                                                    )

                                                    Page.BROWSE, Page.SOURCES -> BrowseScreen(
                                                        state,
                                                        model
                                                    )

                                                    Page.READER -> ReaderScreen(state.reader, model)

                                                    Page.STORAGE -> StorageScreen(
                                                        packages,
                                                        work,
                                                        model,
                                                        pickFile
                                                    )

                                                    Page.SETTINGS -> SettingsScreen(
                                                        model,
                                                        theme.themeMode
                                                    )

                                                    Page.APPEARANCE -> AppearanceScreen(
                                                        state,
                                                        theme.themeMode,
                                                        model
                                                    )

                                                    Page.DATA -> DataScreen(work.busy, model) {
                                                        exporter.launch("blobs-conversations.md")
                                                    }

                                                    Page.PRIVACY -> PrivacyScreen()

                                                    Page.HELP -> HelpScreen(lessMotion)

                                                    Page.ABOUT -> AboutScreen()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun pageTitle(page: Page): Int = when (page) {
    Page.CHAT -> R.string.chat_title
    Page.WELCOME -> R.string.app_wordmark
    Page.SETUP -> R.string.setup_progress_title
    Page.IMPORT -> R.string.import_header
    Page.HISTORY -> R.string.history
    Page.DATA -> R.string.conversation_data
    Page.LIBRARY -> R.string.library
    Page.BROWSE -> R.string.browse
    Page.SOURCES -> R.string.sources
    Page.READER -> R.string.source_details
    Page.MODELS -> R.string.models
    Page.SETTINGS -> R.string.settings
    Page.APPEARANCE -> R.string.appearance
    Page.STORAGE -> R.string.storage
    Page.PRIVACY -> R.string.privacy
    Page.HELP -> R.string.help
    Page.ABOUT -> R.string.about
}

private class LocalCreateDocument(mimeType: String) :
    ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
