package dev.blobs.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blobs.app.AppContainer
import dev.blobs.app.R
import dev.blobs.app.data.ConversationExport
import dev.blobs.app.data.RecoveryExport
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.data.local.ConversationStore
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.local.PackageStore
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.preferences.ThemeMode
import dev.blobs.app.inference.ResearchPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(AppUiState())
    val state = mutable.asStateFlow()
    val contextWindowTokens = container.contextWindowTokens
    val work = container.work.state
    val catalog = container.catalog
    val catalogState = container.catalog.state
    private val conversation = MutableStateFlow("")
    val conversationId = conversation.asStateFlow()
    private val conversationStore: ConversationStore = container.conversationStore
    private val packageStore: PackageStore = container.packageStore
    private val draftLock = Mutex()
    private var draftRevision = 0L
    private val backStack = ArrayDeque<Page>()
    private var searchJob: Job? = null
    private var inspectionJob: Job? = null
    val conversations = conversationStore.observeConversations().catch { fail(R.string.data_unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val packages = packageStore.observe().onEach { installed ->
        reconcileLibraryScope(mutable.value.options.scope, installed)
    }.catch { fail(R.string.data_unavailable) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val turns = conversation.flatMapLatest {
        conversationStore.observeTurns(it)
    }.catch { fail(R.string.data_unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        action {
            container.preferences.options.collect { options ->
                if (!mutable.value.loaded) {
                    val saved = conversationStore.find(options.conversation)
                    if (saved != null) {
                        conversation.value = saved.id
                        mutable.value = mutable.value.copy(draft = saved.draft)
                    } else {
                        createConversation()
                    }
                    val installed = packageStore.all()
                    val restored = work.first { it.ready || it.notice != null }
                    val needsSetup = installed.none { it.kind == "model" }
                    val pendingKind = restored.download?.manifest?.kind
                        ?: restored.pendingImport?.manifest?.kind
                    mutable.value = mutable.value.copy(
                        page = if (options.welcomed && needsSetup) Page.SETUP else Page.CHAT,
                        importOrigin = when {
                            needsSetup -> Page.SETUP
                            pendingKind == "model" -> Page.MODELS
                            else -> Page.LIBRARY
                        }
                    )
                }
                mutable.value = mutable.value.copy(options = options, loaded = true)
                reconcileLibraryScope(options.scope, packages.value)
            }
        }
    }

    fun navigate(page: Page) {
        if (page == mutable.value.page) return
        if (page == Page.SOURCES) mutable.value = mutable.value.copy(browseCollection = null)
        backStack.addLast(mutable.value.page)
        mutable.value = mutable.value.copy(page = page)
        if (page == Page.BROWSE) search("")
    }

    fun back(): Boolean {
        if (mutable.value.page == Page.SETUP) {
            when (setupStep()) {
                SetupStep.MODEL -> mutable.value = mutable.value.copy(page = Page.WELCOME)
                SetupStep.LIBRARY -> mutable.value = mutable.value.copy(setupStep = SetupStep.MODEL)
                SetupStep.READY -> mutable.value = mutable.value.copy(setupStep = SetupStep.LIBRARY)
            }
            return true
        }
        if (backStack.isEmpty()) return false
        mutable.value = mutable.value.copy(page = backStack.removeLast())
        return true
    }

    fun welcome(): Job {
        backStack.clear()
        mutable.value = mutable.value.copy(
            page = Page.WELCOME,
            setupStep = null,
            options = mutable.value.options.copy(welcomed = true)
        )
        navigate(Page.SETUP)
        return action { container.preferences.welcome() }
    }

    fun finishSetup() {
        if (selectedModel() == null) {
            navigate(Page.SETUP)
            return
        }
        goHome()
    }

    fun setupStep(requested: SetupStep? = mutable.value.setupStep): SetupStep = when {
        requested == SetupStep.MODEL -> SetupStep.MODEL
        packages.value.none { it.kind == "model" } -> SetupStep.MODEL
        requested == SetupStep.LIBRARY -> SetupStep.LIBRARY
        else -> SetupStep.READY
    }

    fun continueSetup() {
        if (selectedModel() == null) return
        mutable.value = mutable.value.copy(
            setupStep = when (setupStep()) {
                SetupStep.MODEL -> SetupStep.READY
                SetupStep.LIBRARY -> SetupStep.READY
                SetupStep.READY -> SetupStep.READY
            }
        )
    }

    fun finishImport(preserveNotice: Boolean = false) {
        if (work.value.importComplete && mutable.value.importOrigin == Page.SETUP) {
            mutable.value = mutable.value.copy(setupStep = null)
        }
        if (!preserveNotice) container.work.clearNotice()
        val origin = mutable.value.importOrigin
        if (mutable.value.page == Page.IMPORT) {
            if (backStack.lastOrNull() == origin) {
                back()
            } else {
                mutable.value = mutable.value.copy(page = origin)
            }
        } else {
            navigate(origin)
        }
    }

    fun draft(value: String) {
        val id = conversation.value
        if (id.isEmpty()) return
        draftRevision++
        mutable.value = mutable.value.copy(
            draft = truncateUtf16(value, ResearchPrompt.MAX_QUESTION_CHARACTERS)
        )
        val captured = mutable.value.draft
        action { draftLock.withLock { conversationStore.saveDraft(id, captured) } }
    }

    fun newConversation() = action {
        createConversation()
        goHome()
    }

    private suspend fun createConversation() {
        val created = conversationStore.create()
        container.preferences.selectConversation(created.id)
        conversation.value = created.id
        mutable.value = mutable.value.copy(draft = "")
    }

    fun openConversation(item: ConversationEntity) = action {
        draftLock.withLock {
            val saved = conversationStore.find(item.id) ?: return@withLock
            container.preferences.selectConversation(saved.id)
            conversation.value = saved.id
            mutable.value = mutable.value.copy(draft = saved.draft)
            goHome()
        }
    }

    private fun goHome() {
        backStack.clear()
        mutable.value = mutable.value.copy(page = Page.CHAT)
    }

    fun send(question: String = mutable.value.draft) = submit(question)

    fun retry(turn: TurnEntity, widen: Boolean = false) = submit(turn.question, turn, widen)

    private fun submit(question: String, retry: TurnEntity? = null, widen: Boolean = false) =
        action {
            if (work.value.busy || question.isBlank()) return@action
            val model = (
                if (retry == null) {
                    selectedModel()
                } else {
                    packages.value.find {
                        it.kind == "model" && it.sha256 == retry.model.substringAfterLast(" · ")
                    }
                }
                ) ?: run {
                navigate(Page.MODELS)
                return@action
            }
            val chosen = when {
                widen -> null

                retry != null -> retry.scope.takeUnless {
                    it == "*"
                }?.split('\n')?.filter(String::isNotBlank)?.toSet()

                else -> selectedScope()
            }
            draftLock.withLock {
                val submittingThread = conversation.value
                val submittingRevision = draftRevision
                if (widen) container.preferences.selectScope("*")
                if (container.work.answer(
                        submittingThread,
                        question.trim(),
                        model,
                        packages.value.toList(),
                        chosen,
                        retry?.id
                    )
                ) {
                    if (retry == null && conversation.value == submittingThread) {
                        if (draftRevision == submittingRevision &&
                            mutable.value.draft.trim() == question.trim()
                        ) {
                            mutable.value = mutable.value.copy(draft = "")
                        } else {
                            conversationStore.saveDraft(submittingThread, mutable.value.draft)
                        }
                    }
                }
            }
        }

    fun selectedModel(): PackageEntity? =
        packages.value.find { it.kind == "model" && it.key == mutable.value.options.model }
            ?: packages.value.firstOrNull { it.kind == "model" }

    fun selectedScope(): Set<String>? = mutable.value.options.scope.let {
        if (it ==
            "*"
        ) {
            null
        } else {
            it.split('\n').filter(String::isNotBlank).toSet()
        }
    }

    fun selectedLibraryScope(): Set<String>? {
        val selected = selectedScope() ?: return null
        val libraries = packages.value.filter { it.kind == "library" && it.enabled }
        return selected.mapNotNull { key ->
            libraries.firstOrNull { it.key == key }?.key
                ?: libraries.firstOrNull { key.startsWith("${it.key}/") }?.key
        }.toSet()
    }

    fun selectLibraryScope(value: Set<String>?) = selectScope(value)

    fun selectModel(pack: PackageEntity) = action {
        if (!work.value.busy && selectedModel()?.key != pack.key) {
            container.work.releaseInferenceModel()
            container.preferences.selectModel(pack.key)
        }
    }

    fun selectScope(value: Set<String>?) = action {
        container.preferences.selectScope(
            value?.sorted()?.joinToString("\n") ?: "*"
        )
    }

    private suspend fun reconcileLibraryScope(scope: String, installed: List<PackageEntity>) {
        if (scope == "*") return
        val selected = scope.lineSequence().filter(String::isNotBlank).toList()
        if (selected.isEmpty()) return
        val libraries = installed.filter { it.kind == "library" }
        var changed = false
        val reconciled = selected.mapNotNull { selection ->
            val previous = libraries.firstOrNull {
                selection == it.key || selection.startsWith("${it.key}/")
            }
            if (previous == null || previous.enabled) {
                selection
            } else {
                val replacement = libraries.firstOrNull {
                    it.enabled && it.packageId == previous.packageId
                }
                when {
                    replacement == null -> selection
                    selection == previous.key -> {
                        changed = true
                        replacement.key
                    }

                    else -> {
                        changed = true
                        null
                    }
                }
            }
        }
        if (changed) {
            container.preferences.selectScope(reconciled.distinct().sorted().joinToString("\n"))
        }
    }

    fun toggleSource(source: Evidence) {
        val selected = selectedScope().orEmpty()
        selectScope(if (source.id in selected) selected - source.id else selected + source.id)
    }

    fun read(source: Evidence) {
        mutable.value = mutable.value.copy(reader = source, readerIsFull = false)
        navigate(Page.READER)
    }

    fun readFull(source: Evidence) = action {
        val document =
            withContext(Dispatchers.IO) {
                container.library.readDocument(packages.value, source.id)
            }
        if (document != null) {
            mutable.value = mutable.value.copy(reader = document, readerIsFull = true)
            navigate(Page.READER)
        }
    }

    fun askSource(source: Evidence) = action {
        val libraryKey = packages.value.firstOrNull {
            it.kind == "library" && it.enabled && source.id.startsWith("${it.key}/")
        }?.key ?: return@action
        container.preferences.selectScope(libraryKey)
        goHome()
    }

    fun search(query: String, offset: Int = 0) {
        searchJob?.cancel()
        mutable.value = mutable.value.copy(query = query, searching = true, offset = offset)
        val browseKey = mutable.value.browseCollection.takeIf { mutable.value.page == Page.BROWSE }
        val searchPackages = if (browseKey != null) {
            packages.value.filter { it.key == browseKey }.map { it.copy(enabled = true) }
        } else {
            packages.value
        }
        searchJob = action {
            // SQLite work already inside a driver is not guaranteed to stop when
            // the coroutine is cancelled. Debounce only typed search terms so a
            // fast edit schedules one query; initial browse and paging stay
            // immediate.
            if (query.isNotBlank() && offset == 0) delay(250)
            val result =
                withContext(Dispatchers.IO) {
                    container.library.browse(searchPackages, query, offset)
                }
            if (mutable.value.query != query || mutable.value.offset != offset) return@action
            mutable.value =
                mutable.value.copy(
                    sources = result.take(50),
                    hasMore = result.size > 50,
                    searching = false
                )
        }
    }

    fun browseCollection(pack: PackageEntity) {
        mutable.value = mutable.value.copy(browseCollection = pack.key)
        navigate(Page.BROWSE)
    }

    fun inspect(uri: Uri) {
        if (work.value.busy) return
        inspectionJob?.cancel()
        inspectionJob = action {
            if (!discardPendingTransfer()) return@action
            mutable.value = mutable.value.copy(importUri = uri, manifest = null)
            try {
                val manifest = container.installer.inspect(uri)
                if (mutable.value.importUri == uri) {
                    mutable.value = mutable.value.copy(manifest = manifest)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (mutable.value.importUri == uri) {
                    mutable.value = mutable.value.copy(importUri = null)
                    fail(R.string.import_failed)
                }
            }
        }
    }

    fun importDocuments(uris: List<Uri>, context: Context) {
        action {
            if (!discardPendingTransfer()) return@action
            if (container.work.importDocuments(
                    uris,
                    context.getString(R.string.import_documents_title)
                )
            ) {
                mutable.value = mutable.value.copy(importOrigin = mutable.value.page)
                navigate(Page.IMPORT)
            }
        }
    }

    fun setLibraryEnabled(pack: PackageEntity, enabled: Boolean) = action {
        if (pack.kind != "library" || work.value.busy) return@action
        container.work.mutate {
            packageStore.setLibraryEnabled(pack.key, enabled)
        }
    }

    fun exportCollection(context: Context, pack: PackageEntity, uri: Uri) = action {
        withContext(Dispatchers.IO) {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri))
            container.installer.exportCollection(pack, output)
        }
        fail(R.string.collection_export_saved)
    }

    fun dismissImport() {
        inspectionJob?.cancel()
        mutable.value = mutable.value.copy(manifest = null, importUri = null)
    }

    fun install() {
        val uri = mutable.value.importUri ?: return
        val manifest = mutable.value.manifest ?: return
        action {
            if (!discardPendingTransfer()) return@action
            if (container.work.install(uri, manifest)) {
                mutable.value = mutable.value.copy(importOrigin = mutable.value.page)
                dismissImport()
                navigate(Page.IMPORT)
            }
        }
    }

    fun downloadPackage(asset: DownloadablePackage) {
        action {
            val current = work.value
            val sameTransfer = current.hasPendingTransfer &&
                (current.download == asset || current.pendingImport?.manifest == asset.manifest)
            if (sameTransfer) {
                if (!current.transferActive) container.work.resumeImport()
                mutable.value = mutable.value.copy(importOrigin = mutable.value.page)
                navigate(Page.IMPORT)
                return@action
            }
            if (!discardPendingTransfer()) return@action
            startPackageDownload(asset, mutable.value.page)
        }
    }

    private suspend fun discardPendingTransfer(): Boolean {
        if (!work.value.hasPendingTransfer) return true
        container.work.discardImport()
        val settled = work.first { !it.busy }
        if (settled.hasPendingTransfer) fail(R.string.save_failed)
        return !settled.hasPendingTransfer
    }

    private suspend fun startPackageDownload(asset: DownloadablePackage, origin: Page) {
        if (!container.work.downloadAndInstall(asset)) return
        if (asset.manifest.kind == "model") {
            container.work.releaseInferenceModel()
            container.preferences.selectModel(asset.manifest.key)
        }
        mutable.value = mutable.value.copy(importOrigin = origin)
        navigate(Page.IMPORT)
    }

    fun refreshCatalog() = action { container.catalog.refreshNow() }

    fun remove(pack: PackageEntity) = action {
        if (work.value.busy) return@action
        if (pack.kind == "model" &&
            packages.value.count { it.kind == "model" } <= 1
        ) {
            fail(R.string.keep_model)
            return@action
        }
        val wasSelected = selectedModel()?.key == pack.key
        val removed = container.work.mutate {
            if (wasSelected) container.work.releaseInferenceModel()
            container.installer.remove(pack)
        }
        if (!removed) return@action
        if (wasSelected) {
            container.preferences.selectModel(
                packages.value.firstOrNull {
                    it.kind == "model" &&
                        it.key != pack.key
                }?.key.orEmpty()
            )
        }
        if (pack.kind == "library") {
            selectedScope()?.let { selected ->
                val remaining = selected.filterNot {
                    it == pack.key || it.startsWith("${pack.key}/")
                }
                if (remaining.size != selected.size) {
                    container.preferences.selectScope(remaining.sorted().joinToString("\n"))
                }
            }
        }
    }

    fun rename(item: ConversationEntity, title: String) = action {
        if (title.isNotBlank()) {
            conversationStore.rename(
                item.id,
                truncateUtf16(title.trim(), 120),
                System.currentTimeMillis()
            )
        }
    }

    fun delete(item: ConversationEntity?) = action {
        if (work.value.busy) return@action
        draftLock.withLock {
            container.work.mutate {
                if (item == null) conversationStore.deleteAll() else conversationStore.delete(item.id)
                if (item == null || item.id == conversation.value) {
                    createConversation()
                    goHome()
                }
            }
        }
    }

    fun theme(mode: ThemeMode) = action { container.preferences.setThemeMode(mode) }
    fun reducedMotion(value: Boolean) = action { container.preferences.setReducedMotion(value) }
    fun stop() = container.work.stop()
    fun resumeImport() = container.work.resumeImport()
    fun discardImport() = container.work.discardImport()
    fun dismissNotice() {
        mutable.value = mutable.value.copy(notice = null)
        container.work.clearNotice()
    }

    fun export(context: Context, uri: Uri) = action {
        ConversationExport.write(context, uri, conversationStore) { work.value }
        fail(R.string.export_saved)
    }

    fun exportRecovery(context: Context, uri: Uri) = action {
        RecoveryExport.write(context, uri)
        fail(R.string.export_saved)
    }

    private fun fail(message: Int) {
        mutable.value =
            mutable.value.copy(notice = message, searching = false)
    }

    private fun truncateUtf16(value: String, maximumLength: Int): String {
        var end = minOf(value.length, maximumLength)
        if (
            end < value.length && end > 0 &&
            Character.isHighSurrogate(value[end - 1]) &&
            Character.isLowSurrogate(value[end])
        ) {
            end--
        }
        return value.substring(0, end)
    }

    private fun action(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (
            error: kotlinx.coroutines.CancellationException
        ) {
            throw error
        } catch (_: Exception) {
            fail(R.string.save_failed)
        }
    }
}
