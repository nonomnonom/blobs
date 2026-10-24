package dev.blobs.app.data.transfer

import dev.blobs.app.data.local.PackageDao
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PackageDownloader
import dev.blobs.app.data.packages.PackageInstaller
import dev.blobs.app.data.packages.PendingImport

/** Reconciles or discards interrupted package transfers without UI policy. */
internal class PackageTransferRecovery(
    private val installer: PackageInstaller,
    private val downloader: PackageDownloader,
    private val packages: PackageDao,
    private val availableDownloads: List<DownloadablePackage>
) {
    suspend fun recover(): RecoveredPackageTransfer {
        val pendingImport = installer.recover()
        val download = downloader.pending(availableDownloads)
        if (download == null) return RecoveredPackageTransfer(pendingImport, null, 0L)
        if (packages.all().any { it.key == download.manifest.key }) {
            downloader.discard(download)
            return RecoveredPackageTransfer(pendingImport, null, 0L)
        }
        return RecoveredPackageTransfer(
            pendingImport = pendingImport,
            download = download,
            downloadedBytes = downloader.savedBytes(download)
        )
    }

    fun pendingImport(): PendingImport? = installer.pending()

    suspend fun discard(download: DownloadablePackage?) {
        installer.discardPending()
        download?.let(downloader::discard)
    }
}

internal data class RecoveredPackageTransfer(
    val pendingImport: PendingImport?,
    val download: DownloadablePackage?,
    val downloadedBytes: Long
)
