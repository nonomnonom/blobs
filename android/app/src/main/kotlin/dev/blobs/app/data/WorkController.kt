package dev.blobs.app.data

import android.net.Uri
import android.os.SystemClock
import dev.blobs.app.R
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.library.RetrievalQuery
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.packages.DownloadFormat
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageDownloader
import dev.blobs.app.data.packages.PackageInstaller
import dev.blobs.app.data.packages.PackageManifest
import dev.blobs.app.inference.AnswerGrounding
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.GenerationMetrics
import dev.blobs.app.inference.NativeBridge
import dev.blobs.app.inference.OfflineAnswerNotice
import dev.blobs.app.inference.ResearchPrompt
import dev.blobs.app.inference.TokenSink
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal data class WorkPerformanceSnapshot(
    val retrievalElapsedMs: Long,
    val generation: GenerationMetrics,
    val nativeResult: Int
)

class WorkController(
    private val db: BlobsDatabase,
    private val library: LibraryStore,
    private val installer: PackageInstaller,
    private val downloader: PackageDownloader,
    private val availableDownloads: List<DownloadablePackage> = emptyList()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(WorkState())
    val state = mutable.asStateFlow()
    private val bridgeDelegate = lazy { NativeBridge() }
    private val bridge get() = bridgeDelegate.value
    private var job: Job? = null

    @Volatile
    internal var lastPerformanceSnapshot: WorkPerformanceSnapshot? = null
        private set

    @Volatile private var stopped = false

    @Volatile private var interrupted = false

    @Volatile private var discardTransfer = false

    init {
        scope.launch {
            try {
                db.conversations().recoverInterrupted()
                try {
                    val pending = installer.recover()
                    val download = downloader.pending(availableDownloads)
                    val alreadyInstalled = download != null &&
                        db.packages().all().any { it.key == download.manifest.key }
                    if (alreadyInstalled) downloader.discard(requireNotNull(download))
                    mutable.value = if (download != null && !alreadyInstalled) {
                        WorkState(
                            ready = true,
                            pendingImport = pending,
                            download = download,
                            progress = if (pending == null) downloader.savedBytes(download) else 0L,
                            total = if (pending ==
                                null
                            ) {
                                download.downloadBytes
                            } else {
                                download.manifest.bytes
                            },
                            downloading = pending == null,
                            inAppDownload = true,
                            packageKind = download.manifest.kind,
                            importTitle = download.manifest.title,
                            downloadTotal = download.downloadBytes,
                            installTotal = download.manifest.bytes
                        )
                    } else {
                        WorkState(ready = true, pendingImport = pending)
                    }
                } catch (_: Exception) {
                    mutable.value =
                        WorkState(ready = true, notice = R.string.import_recovery_failed)
                }
            } catch (_: Exception) {
                mutable.value = WorkState(notice = R.string.data_unavailable)
            }
        }
    }

    suspend fun answer(
        thread: String,
        question: String,
        model: PackageEntity,
        packages: List<PackageEntity>,
        allowed: Set<String>?,
        retryTurnId: String? = null
    ): Boolean {
        if (question.isBlank() || allowed?.isEmpty() == true) return false
        val id = retryTurnId ?: UUID.randomUUID().toString()
        if (!reserveAnswer(id, thread)) return false
        lastPerformanceSnapshot = null
        val accepted = CompletableDeferred<Boolean>()
        job = scope.launch {
            val start = SystemClock.elapsedRealtime()
            var answer = ""
            var rawAnswer = ""
            var answerNotice = ""
            fun displayedAnswer() = if (answer.isBlank()) answer else answerNotice + answer
            var evidence = "[]"
            var begun = false
            try {
                val dao = db.conversations()
                val previous = dao.turns(thread)
                val retry = retryTurnId?.let { retryId ->
                    previous.lastOrNull()?.takeIf {
                        it.id == retryId && it.question == question &&
                            it.status !in setOf("COMPLETE", "RUNNING")
                    } ?: return@launch
                }
                val previousTurns = previous.filterNot { it.id == retryTurnId }
                val history = previousTurns.takeLast(2)
                val turn = TurnEntity(
                    id, thread, question, "", "RUNNING",
                    "${model.title} · ${model.version} · ${model.sha256}",
                    allowed?.joinToString("\n") ?: "*", "[]",
                    retry?.createdAt ?: System.currentTimeMillis()
                )
                dao.beginTurn(turn, clearDraft = retry == null)
                begun = true
                accepted.complete(true)
                val query = RetrievalQuery.resolve(question, previousTurns.map { it.question })
                answerNotice = OfflineAnswerNotice.forQuestion(query)
                val retrievalStarted = SystemClock.elapsedRealtime()
                var sources = library.retrieve(packages, query, allowed)
                    .take(MAX_ANSWER_SOURCES)
                val retrievalElapsedMs = SystemClock.elapsedRealtime() - retrievalStarted
                evidence = Evidence.encode(sources)
                mutable.update { it.copy(evidence = evidence) }
                if (stopped) {
                    dao.updateAnswer(
                        id,
                        "",
                        evidence,
                        if (interrupted) "INTERRUPTED" else "STOPPED",
                        0,
                        ""
                    )
                    return@launch
                }
                if (sources.isEmpty()) {
                    dao.updateAnswer(
                        id,
                        "",
                        evidence,
                        "NO_SOURCES",
                        SystemClock.elapsedRealtime() - start,
                        ""
                    )
                    return@launch
                }
                val bytes = ByteArrayOutputStream()
                val generationMetrics = GenerationMetrics()
                var lastSave = 0L
                val result = ResearchPrompt.withContextBudget(
                    question,
                    query,
                    history.map { it.question to it.answer },
                    sources
                ) { prompt ->
                    sources = prompt.sources
                    evidence = Evidence.encode(sources)
                    mutable.update { it.copy(evidence = evidence) }
                    bridge.generate(
                        model.path.toByteArray(),
                        ResearchPrompt.system.toByteArray(),
                        prompt.text.toByteArray(),
                        768,
                        TokenSink { token ->
                            bytes.write(token)
                            rawAnswer = AnswerText.visible(bytes.toString("UTF-8"))
                            answer = AnswerText.validSourceCitations(rawAnswer, sources.size)
                            val displayed = displayedAnswer()
                            mutable.update { it.copy(text = displayed) }
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastSave > 500) {
                                runBlocking {
                                    dao.updateAnswer(
                                        id,
                                        displayed,
                                        evidence,
                                        "RUNNING",
                                        now - start,
                                        ""
                                    )
                                }
                                lastSave = now
                            }
                        },
                        generationMetrics
                    )
                }
                lastPerformanceSnapshot = WorkPerformanceSnapshot(
                    retrievalElapsedMs,
                    generationMetrics,
                    result
                )
                val unverified = !interrupted &&
                    !stopped &&
                    AnswerGrounding.shouldWithholdCompletion(result, rawAnswer, sources)
                if (unverified) {
                    answer = ""
                    mutable.update { it.copy(text = "") }
                }
                val status = when {
                    interrupted -> "INTERRUPTED"
                    stopped || result == 1 -> "STOPPED"
                    unverified -> "UNVERIFIED"
                    result == 2 -> "LIMITED"
                    result == 0 && answer.isNotBlank() -> "COMPLETE"
                    else -> "ERROR"
                }
                dao.updateAnswer(
                    id,
                    displayedAnswer(),
                    evidence,
                    status,
                    SystemClock.elapsedRealtime() - start,
                    ""
                )
            } catch (error: Exception) {
                bridge.stop()
                var notice = R.string.answer_failed
                if (error.message in
                    setOf("MEMORY", "CONTEXT_ALLOC")
                ) {
                    notice = R.string.memory_failed
                }
                if (error.message == "CONTEXT_FULL") notice = R.string.context_full
                try {
                    if (begun) {
                        db.conversations().updateAnswer(
                            id,
                            displayedAnswer(),
                            evidence,
                            if (error.message == "CONTEXT_FULL") "CONTEXT_FULL" else "ERROR",
                            SystemClock.elapsedRealtime() - start,
                            error.message.orEmpty().take(80)
                        )
                    }
                } catch (_: Exception) {
                    notice = R.string.save_failed
                }
                mutable.update { it.copy(notice = notice) }
            } finally {
                accepted.complete(false)
                mutable.update {
                    if (begun) {
                        it.copy(busy = false)
                    } else {
                        it.copy(
                            busy = false,
                            turnId = "",
                            threadId = "",
                            text = "",
                            evidence = "[]"
                        )
                    }
                }
            }
        }
        return accepted.await()
    }

    @Synchronized
    private fun reserveAnswer(id: String, thread: String): Boolean {
        if (!mutable.value.ready || mutable.value.busy) return false
        bridge.reset()
        stopped = false
        interrupted = false
        mutable.value =
            mutable.value.copy(
                busy = true,
                turnId = id,
                threadId = thread,
                text = "",
                evidence = "[]",
                startedAt = SystemClock.elapsedRealtime(),
                transferActive = false,
                notice = null
            )
        return true
    }

    @Synchronized
    fun install(uri: Uri, manifest: PackageManifest): Boolean {
        if (!mutable.value.ready || mutable.value.busy) return false
        val pending = mutable.value.pendingImport
        if ((pending != null && pending.manifest.key != manifest.key) ||
            (mutable.value.download != null && mutable.value.download?.manifest != manifest)
        ) {
            mutable.update { it.copy(notice = R.string.finish_pending_import) }
            return false
        }
        discardTransfer = false
        val asset = mutable.value.download?.takeIf { it.manifest == manifest }
        mutable.value = WorkState(
            ready = true, busy = true, total = manifest.bytes,
            importTitle = manifest.title, packageKind = manifest.kind,
            transferActive = true, download = asset, inAppDownload = asset != null,
            downloadTotal = asset?.downloadBytes ?: 0, installTotal = manifest.bytes
        )
        job = scope.launch {
            try {
                installer.install(uri, manifest) { bytes ->
                    mutable.update { it.copy(progress = bytes) }
                }
                downloader.removeInstalledArchive(uri)
                mutable.update {
                    it.copy(
                        busy = false,
                        transferActive = false,
                        notice = R.string.package_added,
                        importComplete = true,
                        download = null,
                        pendingImport = null,
                        progress = manifest.bytes,
                        total = manifest.bytes
                    )
                }
            } catch (_: CancellationException) {
                pauseTransfer(R.string.download_paused)
            } catch (error: Exception) {
                pauseTransfer(
                    if (error.message ==
                        "STORAGE"
                    ) {
                        R.string.storage_failed
                    } else {
                        R.string.import_failed
                    }
                )
            }
        }
        return true
    }

    @Synchronized
    fun importDocuments(uris: List<Uri>, title: String): Boolean {
        if (!mutable.value.ready || mutable.value.busy) return false
        if (mutable.value.hasPendingTransfer) {
            mutable.update { it.copy(notice = R.string.finish_pending_import) }
            return false
        }
        require(uris.isNotEmpty())
        discardTransfer = false
        mutable.value = WorkState(
            ready = true,
            busy = true,
            importTitle = title,
            packageKind = "library",
            transferActive = true
        )
        job = scope.launch {
            try {
                val pack = installer.importDocuments(uris) { progress, total ->
                    mutable.update { it.copy(progress = progress, total = total) }
                }
                mutable.value = WorkState(
                    ready = true,
                    notice = R.string.collection_added,
                    importTitle = pack.title,
                    importComplete = true,
                    packageKind = "library"
                )
            } catch (_: CancellationException) {
                pauseTransfer(R.string.download_paused)
            } catch (error: Exception) {
                mutable.value = WorkState(
                    ready = true,
                    notice = when (error.message) {
                        "STORAGE" -> R.string.storage_failed

                        "PDF_NO_TEXT" -> R.string.pdf_no_text

                        "PDF_PASSWORD" -> R.string.pdf_password

                        "PDF_UNSUPPORTED_ENCRYPTION" -> R.string.pdf_unsupported_encryption

                        "PDF_PAGE_COUNT", "DOCUMENT_TOO_LARGE", "COLLECTION_TOO_LARGE",
                        "IMPORT_TOO_LARGE" -> R.string.document_too_large

                        "UNSUPPORTED_DOCUMENT" -> R.string.unsupported_document

                        "DOCUMENT_INVALID", "PACK_INVALID", "PACK_VERSION" ->
                            R.string.document_invalid

                        else -> R.string.import_failed
                    }
                )
            }
        }
        return true
    }

    @Synchronized
    fun downloadAndInstall(asset: DownloadablePackage): Boolean {
        if (!mutable.value.ready || mutable.value.busy) return false
        val pending = mutable.value.pendingImport
        if ((pending != null && pending.manifest.key != asset.manifest.key) ||
            (mutable.value.download != null && mutable.value.download != asset)
        ) {
            mutable.update { it.copy(notice = R.string.finish_pending_import) }
            return false
        }
        discardTransfer = false
        mutable.value = WorkState(
            ready = true,
            busy = true,
            transferActive = true,
            download = asset,
            total = asset.downloadBytes,
            importTitle = asset.manifest.title,
            downloading = true,
            inAppDownload = true,
            packageKind = asset.manifest.kind,
            downloadTotal = asset.downloadBytes,
            installTotal = asset.manifest.bytes
        )
        job = scope.launch {
            try {
                downloader.remember(asset)
                val file = downloader.download(asset) { bytes, total ->
                    mutable.update { it.copy(progress = bytes, total = total) }
                }
                val uri = Uri.fromFile(file)
                if (asset.format == DownloadFormat.BLOBS) {
                    require(installer.inspect(uri) == asset.manifest) { "PACKAGE_MANIFEST" }
                }
                mutable.update {
                    it.copy(progress = 0, total = asset.manifest.bytes, downloading = false)
                }
                installer.install(uri, asset.manifest, catalogDigestPinned = true) { bytes ->
                    mutable.update { it.copy(progress = bytes) }
                }
                downloader.removeInstalledArchive(uri)
                mutable.value = WorkState(
                    ready = true,
                    notice = R.string.package_added,
                    importTitle = asset.manifest.title,
                    importComplete = true,
                    inAppDownload = true,
                    packageKind = asset.manifest.kind
                )
            } catch (_: CancellationException) {
                pauseTransfer(R.string.download_paused)
            } catch (error: Exception) {
                pauseTransfer(
                    when (error.message) {
                        "STORAGE" -> R.string.storage_failed
                        "DOWNLOAD_HOST", "DOWNLOAD_REDIRECT" -> R.string.download_source_failed
                        else -> R.string.download_failed
                    }
                )
            }
        }
        return true
    }

    fun stop(background: Boolean = false) {
        if (mutable.value.busy && mutable.value.turnId.isNotEmpty()) {
            stopped = true
            interrupted = background
            bridge.stop()
        } else if (mutable.value.busy && !background) {
            job?.cancel()
        }
        if (background) releaseModelInBackground()
    }

    suspend fun releaseInferenceModel() = withContext(Dispatchers.IO) {
        if (bridgeDelegate.isInitialized()) bridge.releaseModel()
    }

    fun trimInferenceMemory() = releaseModelInBackground()

    private fun releaseModelInBackground() {
        if (bridgeDelegate.isInitialized()) {
            scope.launch { bridge.releaseModel() }
        }
    }

    fun clearNotice() {
        mutable.update { it.copy(notice = null) }
    }

    companion object {
        internal const val MAX_ANSWER_SOURCES = 4
    }

    suspend fun mutate(block: suspend () -> Unit): Boolean {
        synchronized(this) {
            if (!mutable.value.ready || mutable.value.busy) return false
            job = null
            mutable.update { it.copy(busy = true, turnId = "", threadId = "") }
        }
        try {
            block()
        } finally {
            mutable.update { it.copy(busy = false) }
        }
        return true
    }

    fun resumeImport() {
        val download = state.value.download
        if (download != null) {
            downloadAndInstall(download)
        } else {
            state.value.pendingImport?.let { install(it.uri, it.manifest) }
        }
    }

    @Synchronized
    fun discardImport() {
        if (state.value.busy) {
            if (!state.value.transferActive) return
            discardTransfer = true
            job?.cancel()
            return
        }
        mutable.update { it.copy(busy = true, turnId = "", threadId = "") }
        job = null
        scope.launch {
            try {
                installer.discardPending()
                mutable.value.download?.let(downloader::discard)
                mutable.update {
                    it.copy(
                        pendingImport = null,
                        download = null,
                        notice = R.string.import_cancelled
                    )
                }
            } catch (_: Exception) {
                mutable.update { it.copy(notice = R.string.save_failed) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun pauseTransfer(notice: Int) = withContext(NonCancellable) {
        val transfer = mutable.value
        val pending = runCatching { installer.pending() }.getOrNull()
        if (discardTransfer) {
            val discarded = runCatching {
                installer.discardPending()
                transfer.download?.let(downloader::discard)
            }.isSuccess
            mutable.value = transfer.copy(
                busy = false,
                transferActive = false,
                download = if (discarded) null else transfer.download,
                pendingImport = if (discarded) null else pending,
                notice = if (discarded) R.string.import_cancelled else R.string.save_failed
            )
        } else {
            mutable.value = transfer.copy(
                busy = false,
                transferActive = false,
                pendingImport = pending,
                notice = notice
            )
        }
    }
}
