package dev.blobs.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blobs.app.AppContainer
import dev.blobs.app.R
import dev.blobs.app.data.RecoveryExport
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageManifest
import dev.blobs.app.data.preferences.PreferencesStore
import dev.blobs.app.data.preferences.ThemeMode
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Page {
    CHAT,
    WELCOME,
    SETUP,
    IMPORT,
    HISTORY,
    LIBRARY,
    BROWSE,
    SOURCES,
    READER,
    MODELS,
    SETTINGS,
    APPEARANCE,
    STORAGE,
    DATA,
    PRIVACY,
    HELP,
    ABOUT
}

data class ProductState(
    val page: Page = Page.CHAT,
    val draft: String = "",
    val options: PreferencesStore.Options = PreferencesStore.Options(),
    val loaded: Boolean = false,
    val notice: Int? = null,
    val sources: List<Evidence> = emptyList(),
    val query: String = "",
    val offset: Int = 0,
    val hasMore: Boolean = false,
    val searching: Boolean = false,
    val reader: Evidence? = null,
    val readerIsFull: Boolean = false,
    val importOrigin: Page = Page.SETUP,
    val importUri: Uri? = null,
    val manifest: PackageManifest? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProductViewModel(private val container: AppContainer) : ViewModel() {
    private val mutable = MutableStateFlow(ProductState())
    val state = mutable.asStateFlow()
    val work = container.work.state
    val catalog = container.catalog
    private val conversation = MutableStateFlow("")
    val conversationId = conversation.asStateFlow()
    private val dao = container.database.conversations()
    private val draftLock = Mutex()
    private val backStack = ArrayDeque<Page>()
    private var searchJob: Job? = null
    private var inspectionJob: Job? = null
    val conversations = dao.observeConversations().catch { fail(R.string.data_unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val packages = container.database.packages().observe().catch { fail(R.string.data_unavailable) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val turns = conversation.flatMapLatest {
        dao.observeTurns(it)
    }.catch { fail(R.string.data_unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        action {
            container.preferences.options.collect { options ->
                if (!mutable.value.loaded) {
                    val saved = dao.find(options.conversation)
                    if (saved != null) {
                        conversation.value = saved.id
                        mutable.value = mutable.value.copy(draft = saved.draft)
                    } else {
                        createConversation()
                    }
                }
                mutable.value = mutable.value.copy(options = options, loaded = true)
            }
        }
    }

    fun navigate(page: Page) {
        if (page == mutable.value.page) return
        backStack.addLast(mutable.value.page)
        mutable.value = mutable.value.copy(page = page)
        if (page in setOf(Page.BROWSE, Page.SOURCES)) search("")
    }

    fun back(): Boolean {
        if (backStack.isEmpty()) return false
        mutable.value = mutable.value.copy(page = backStack.removeLast())
        return true
    }

    fun welcome() = action {
        mutable.value = mutable.value.copy(page = Page.WELCOME)
        container.preferences.welcome()
        navigate(Page.SETUP)
    }

    fun finishSetup() {
        if (selectedModel() == null || !hasStarterLibrary()) {
            navigate(Page.SETUP)
            return
        }
        goHome()
    }

    private fun hasStarterLibrary(): Boolean = packages.value.any {
        it.packageId == catalog.starterLibrary.manifest.id &&
            it.version == catalog.starterLibrary.manifest.version
    }

    fun finishImport() {
        container.work.clearNotice()
        val origin = mutable.value.importOrigin
        if (mutable.value.page == Page.IMPORT && backStack.lastOrNull() == origin) {
            back()
        } else {
            navigate(origin)
        }
    }

    fun draft(value: String) {
        val id = conversation.value
        if (id.isEmpty()) return
        mutable.value = mutable.value.copy(draft = value.take(4000))
        val captured = mutable.value.draft
        action { draftLock.withLock { dao.saveDraft(id, captured) } }
    }

    fun newConversation() = action {
        createConversation()
        goHome()
    }

    private suspend fun createConversation() {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.save(ConversationEntity(id, "", "", now, now))
        container.preferences.selectConversation(id)
        conversation.value = id
        mutable.value = mutable.value.copy(draft = "")
    }

    fun openConversation(item: ConversationEntity) = action {
        draftLock.withLock {
            val saved = dao.find(item.id) ?: return@withLock
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

    fun send(question: String = mutable.value.draft, widen: Boolean = false) = action {
        if (work.value.busy || question.isBlank()) return@action
        if (!hasStarterLibrary()) {
            navigate(Page.SETUP)
            return@action
        }
        val model = selectedModel() ?: run {
            navigate(Page.MODELS)
            return@action
        }
        val chosen = if (widen) null else selectedScope()
        if (chosen?.isEmpty() == true) {
            fail(R.string.empty_scope)
            return@action
        }
        draftLock.withLock {
            if (widen) container.preferences.selectScope("*")
            if (container.work.answer(
                    conversation.value,
                    question.trim(),
                    model,
                    packages.value.toList(),
                    chosen
                )
            ) {
                mutable.value = mutable.value.copy(draft = "")
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

    fun selectModel(pack: PackageEntity) = action {
        if (!work.value.busy) container.preferences.selectModel(pack.key)
    }

    fun selectScope(value: Set<String>?) = action {
        container.preferences.selectScope(
            value?.joinToString("\n") ?: "*"
        )
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
        container.preferences.selectScope(source.id)
        goHome()
    }

    fun search(query: String, offset: Int = 0) {
        searchJob?.cancel()
        mutable.value = mutable.value.copy(query = query, searching = true, offset = offset)
        searchJob = action {
            val result =
                withContext(Dispatchers.IO) {
                    container.library.browse(packages.value, query, offset)
                }
            mutable.value =
                mutable.value.copy(
                    sources = result.take(50),
                    hasMore = result.size > 50,
                    searching = false
                )
        }
    }

    fun inspect(uri: Uri) {
        if (work.value.busy) return
        if (work.value.pendingImport != null) {
            navigate(Page.IMPORT)
            return
        }
        inspectionJob?.cancel()
        mutable.value = mutable.value.copy(importUri = uri, manifest = null)
        inspectionJob = action {
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

    fun dismissImport() {
        inspectionJob?.cancel()
        mutable.value = mutable.value.copy(manifest = null, importUri = null)
    }

    fun install() {
        val uri = mutable.value.importUri ?: return
        val manifest = mutable.value.manifest ?: return
        if (container.work.install(uri, manifest)) {
            mutable.value = mutable.value.copy(importOrigin = mutable.value.page)
            dismissImport()
            navigate(Page.IMPORT)
        }
    }

    fun downloadPackage(asset: DownloadablePackage) {
        if (container.work.downloadAndInstall(asset)) {
            if (asset.manifest.kind == "model") {
                action { container.preferences.selectModel(asset.manifest.key) }
            }
            mutable.value = mutable.value.copy(importOrigin = mutable.value.page)
            navigate(Page.IMPORT)
        }
    }

    fun remove(pack: PackageEntity) = action {
        if (work.value.busy) return@action
        if (pack.kind == "model" &&
            packages.value.count { it.kind == "model" } <= 1
        ) {
            fail(R.string.keep_model)
            return@action
        }
        if (!container.work.mutate { container.installer.remove(pack) }) return@action
        if (mutable.value.options.model == pack.key) {
            container.preferences.selectModel(
                packages.value.firstOrNull {
                    it.kind == "model" &&
                        it.key != pack.key
                }?.key.orEmpty()
            )
        }
    }

    fun rename(item: ConversationEntity, title: String) = action {
        if (title.isNotBlank()) {
            dao.rename(
                item.id,
                title.trim().take(120),
                System.currentTimeMillis()
            )
        }
    }

    fun delete(item: ConversationEntity?) = action {
        if (work.value.busy) return@action
        draftLock.withLock {
            container.work.mutate {
                if (item == null) dao.deleteAll() else dao.delete(item.id)
                if (item == null || item.id == conversation.value) {
                    createConversation()
                    goHome()
                }
            }
        }
    }

    fun theme(mode: ThemeMode) = action { container.preferences.setThemeMode(mode) }
    fun textScale(scale: Float) = action { container.preferences.setTextScale(scale) }
    fun reducedMotion(value: Boolean) = action { container.preferences.setReducedMotion(value) }
    fun stop() = container.work.stop()
    fun resumeImport() = container.work.resumeImport()
    fun discardImport() = container.work.discardImport()
    fun dismissNotice() {
        mutable.value = mutable.value.copy(notice = null)
        container.work.clearNotice()
    }

    fun export(context: Context, uri: Uri) = action {
        withContext(Dispatchers.IO) {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri))
            output.bufferedWriter().use { writer ->
                writer.appendLine("# Blobs conversations\n")
                for (item in dao.all()) {
                    writer.appendLine("## ${item.title}\n")
                    if (item.draft.isNotBlank()) writer.appendLine("Draft: ${item.draft}\n")
                    for (turn in dao.turns(item.id)) {
                        writer.appendLine(
                            "### ${turn.question}\n\n${if (work.value.turnId == turn.id && work.value.text.isNotBlank()) work.value.text else turn.answer}\n\nStatus: ${turn.status}\nModel: ${turn.model}\n"
                        )
                        val live = work.value
                        val sourceData = if (live.turnId ==
                            turn.id
                        ) {
                            live.evidence
                        } else {
                            turn.evidence
                        }
                        val sources = Evidence.decodeOrNull(sourceData)
                        if (sources ==
                            null
                        ) {
                            writer.appendLine(
                                "Unreadable source record (preserved):\n$sourceData\n"
                            )
                        }
                        sources.orEmpty().forEachIndexed { index, evidence ->
                            writer.appendLine(
                                "[${index + 1}] ${evidence.title}\n${evidence.publisher}\n${evidence.url}\n${evidence.license}\nEdition: ${evidence.edition}\n\n${evidence.text}\n"
                            )
                        }
                    }
                }
            }
        }
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
