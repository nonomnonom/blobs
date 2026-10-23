package dev.blobs.app.data.transfer

import android.net.Uri
import dev.blobs.app.R
import dev.blobs.app.data.WorkState
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageManifest
import dev.blobs.app.data.work.WorkSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Owns package import, catalog-download, pause, discard, and recovery lifecycles. */
internal class PackageTransferWork(
    private val transfers: PackageTransferExecutor,
    private val recovery: PackageTransferRecovery,
    private val session: WorkSession
) {
    @Volatile private var discardRequested = false

    suspend fun recover() {
        try {
            val recovered = recovery.recover()
            val download = recovered.download
            session.replace(
                if (download != null) {
                    WorkState(
                        ready = true,
                        pendingImport = recovered.pendingImport,
                        download = download,
                        progress = if (recovered.pendingImport == null) {
                            recovered.downloadedBytes
                        } else {
                            0L
                        },
                        total = if (recovered.pendingImport == null) {
                            download.downloadBytes
                        } else {
                            download.manifest.bytes
                        },
                        downloading = recovered.pendingImport == null,
                        inAppDownload = true,
                        packageKind = download.manifest.kind,
                        importTitle = download.manifest.title,
                        downloadTotal = download.downloadBytes,
                        installTotal = download.manifest.bytes
                    )
                } else {
                    WorkState(ready = true, pendingImport = recovered.pendingImport)
                }
            )
        } catch (_: Exception) {
            session.replace(WorkState(ready = true, notice = R.string.import_recovery_failed))
        }
    }

    fun install(uri: Uri, manifest: PackageManifest): Boolean {
        var asset: DownloadablePackage? = null
        val reserved = session.reserve { current ->
            val pending = current.pendingImport
            val currentDownload = current.download
            if ((pending != null && pending.manifest.key != manifest.key) ||
                (currentDownload != null && currentDownload.manifest != manifest)
            ) {
                return@reserve null
            }
            discardRequested = false
            asset = currentDownload?.takeIf { it.manifest == manifest }
            WorkState(
                ready = true,
                busy = true,
                total = manifest.bytes,
                importTitle = manifest.title,
                packageKind = manifest.kind,
                transferActive = true,
                download = asset,
                inAppDownload = asset != null,
                downloadTotal = asset?.downloadBytes ?: 0,
                installTotal = manifest.bytes
            )
        }
        if (!reserved) return false
        session.launchWork {
            try {
                transfers.install(
                    uri,
                    manifest,
                    catalogDigestPinned = asset != null,
                    libraryProfile = asset?.libraryProfile
                ) { bytes ->
                    session.update { it.copy(progress = bytes) }
                }
                session.update {
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
                    when (error.message) {
                        "STORAGE" -> R.string.storage_failed
                        "GGUF_CONTEXT_LENGTH" -> R.string.model_context_incompatible
                        "GGUF_TEMPLATE" -> R.string.model_incompatible
                        else -> R.string.import_failed
                    }
                )
            }
        }
        return true
    }

    fun importDocuments(uris: List<Uri>, title: String): Boolean {
        val reserved = session.reserve { current ->
            if (current.hasPendingTransfer) return@reserve null
            require(uris.isNotEmpty())
            discardRequested = false
            WorkState(
                ready = true,
                busy = true,
                importTitle = title,
                packageKind = "library",
                transferActive = true
            )
        }
        if (!reserved) return false
        session.launchWork {
            try {
                val pack = transfers.importDocuments(uris) { progress, total ->
                    session.update { it.copy(progress = progress, total = total) }
                }
                session.replace(
                    WorkState(
                        ready = true,
                        notice = R.string.collection_added,
                        importTitle = pack.title,
                        importComplete = true,
                        packageKind = "library"
                    )
                )
            } catch (_: CancellationException) {
                pauseTransfer(R.string.download_paused)
            } catch (error: Exception) {
                session.replace(
                    WorkState(
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
                )
            }
        }
        return true
    }

    fun downloadAndInstall(asset: DownloadablePackage): Boolean {
        val reserved = session.reserve { current ->
            val pending = current.pendingImport
            if ((pending != null && pending.manifest.key != asset.manifest.key) ||
                (current.download != null && current.download != asset)
            ) {
                return@reserve null
            }
            discardRequested = false
            WorkState(
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
        }
        if (!reserved) return false
        session.launchWork {
            try {
                transfers.downloadAndInstall(
                    asset,
                    onDownloadProgress = { bytes, total ->
                        session.update { it.copy(progress = bytes, total = total) }
                    },
                    onReadyToInstall = {
                        session.update {
                            it.copy(progress = 0, total = asset.manifest.bytes, downloading = false)
                        }
                    },
                    onInstallProgress = { bytes ->
                        session.update { it.copy(progress = bytes) }
                    }
                )
                session.replace(
                    WorkState(
                        ready = true,
                        notice = R.string.package_added,
                        importTitle = asset.manifest.title,
                        importComplete = true,
                        inAppDownload = true,
                        packageKind = asset.manifest.kind
                    )
                )
            } catch (_: CancellationException) {
                pauseTransfer(R.string.download_paused)
            } catch (error: Exception) {
                pauseTransfer(
                    when (error.message) {
                        "STORAGE" -> R.string.storage_failed
                        "DOWNLOAD_HOST", "DOWNLOAD_REDIRECT" -> R.string.download_source_failed
                        "GGUF_CONTEXT_LENGTH" -> R.string.model_context_incompatible
                        "GGUF_TEMPLATE" -> R.string.model_incompatible
                        else -> R.string.download_failed
                    }
                )
            }
        }
        return true
    }

    fun stop() {
        session.currentJob()?.cancel()
    }

    fun resume() {
        val current = session.state.value
        val download = current.download
        if (download != null) {
            downloadAndInstall(download)
        } else {
            current.pendingImport?.let { install(it.uri, it.manifest) }
        }
    }

    fun discard() {
        val current = session.state.value
        if (current.busy) {
            if (!current.transferActive) return
            discardRequested = true
            session.currentJob()?.cancel()
            return
        }
        if (!session.beginMaintenance()) return
        session.launchChild {
            try {
                recovery.discard(session.state.value.download)
                session.update {
                    it.copy(
                        pendingImport = null,
                        download = null,
                        notice = R.string.import_cancelled
                    )
                }
            } catch (_: Exception) {
                session.update { it.copy(notice = R.string.save_failed) }
            } finally {
                session.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun pauseTransfer(notice: Int) = withContext(NonCancellable) {
        val transfer = session.state.value
        val pending = runCatching { recovery.pendingImport() }.getOrNull()
        if (discardRequested) {
            val discarded = runCatching { recovery.discard(transfer.download) }.isSuccess
            session.replace(
                transfer.copy(
                    busy = false,
                    transferActive = false,
                    download = if (discarded) null else transfer.download,
                    pendingImport = if (discarded) null else pending,
                    notice = if (discarded) R.string.import_cancelled else R.string.save_failed
                )
            )
        } else {
            session.replace(
                transfer.copy(
                    busy = false,
                    transferActive = false,
                    pendingImport = pending,
                    notice = notice
                )
            )
        }
    }
}
