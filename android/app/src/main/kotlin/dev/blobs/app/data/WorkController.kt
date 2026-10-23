package dev.blobs.app.data

import android.net.Uri
import dev.blobs.app.R
import dev.blobs.app.data.local.ConversationStore
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageManifest
import dev.blobs.app.data.transfer.PackageTransferExecutor
import dev.blobs.app.data.transfer.PackageTransferRecovery
import dev.blobs.app.data.transfer.PackageTransferWork
import dev.blobs.app.data.work.ResearchWork
import dev.blobs.app.data.work.WorkSession
import dev.blobs.app.engine.LocalResearchEngine

/**
 * Application-facing work contract.
 *
 * It selects the lifecycle owner for the requested operation while [WorkSession]
 * keeps answer and transfer work mutually exclusive.
 */
internal class WorkController(
    conversationStore: ConversationStore,
    research: LocalResearchEngine,
    transfers: PackageTransferExecutor,
    transferRecovery: PackageTransferRecovery
) {
    private val session = WorkSession()
    private val researchWork = ResearchWork(conversationStore, research, session)
    private val transferWork = PackageTransferWork(transfers, transferRecovery, session)

    val state = session.state

    internal val lastPerformanceSnapshot: WorkPerformanceSnapshot?
        get() = researchWork.lastPerformanceSnapshot

    init {
        session.launchChild {
            try {
                conversationStore.recoverInterrupted()
                transferWork.recover()
            } catch (_: Exception) {
                session.replace(WorkState(notice = R.string.data_unavailable))
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
    ): Boolean = researchWork.answer(thread, question, model, packages, allowed, retryTurnId)

    fun install(uri: Uri, manifest: PackageManifest): Boolean = transferWork.install(uri, manifest)

    fun importDocuments(uris: List<Uri>, title: String): Boolean =
        transferWork.importDocuments(uris, title)

    fun downloadAndInstall(asset: DownloadablePackage): Boolean = transferWork.downloadAndInstall(asset)

    fun stop(background: Boolean = false) {
        if (state.value.busy && state.value.turnId.isNotEmpty()) {
            researchWork.stop(background)
        } else if (state.value.busy && !background) {
            transferWork.stop()
        }
    }

    suspend fun releaseInferenceModel() = researchWork.releaseInferenceModel()

    fun trimInferenceMemory() {
        if (state.value.busy && state.value.turnId.isNotEmpty()) {
            // Releasing native weights waits for the runtime's model lock. Under
            // memory pressure, first stop the active turn so its terminal state
            // can persist and the release is not delayed by ongoing decode.
            researchWork.stop(background = false)
        } else {
            researchWork.trimInferenceMemory()
        }
    }

    fun clearNotice() {
        session.update { it.copy(notice = null) }
    }

    suspend fun mutate(block: suspend () -> Unit): Boolean = session.mutate(block)

    fun resumeImport() = transferWork.resume()

    fun discardImport() = transferWork.discard()
}
