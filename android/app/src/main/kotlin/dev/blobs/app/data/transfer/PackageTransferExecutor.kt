package dev.blobs.app.data.transfer

import android.net.Uri
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DownloadFormat
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.LibraryProfile
import dev.blobs.app.data.packages.PackageDownloader
import dev.blobs.app.data.packages.PackageInstaller
import dev.blobs.app.data.packages.PackageManifest

/**
 * Owns package movement from a caller-provided URI or catalog download to an
 * atomically installed asset. UI state and error copy remain outside this class.
 */
internal class PackageTransferExecutor(
    private val installer: PackageInstaller,
    private val downloader: PackageDownloader
) {
    suspend fun install(
        uri: Uri,
        manifest: PackageManifest,
        catalogDigestPinned: Boolean = false,
        libraryProfile: LibraryProfile? = null,
        onProgress: (Long) -> Unit
    ): PackageEntity {
        val installed = installer.install(
            uri,
            manifest,
            catalogDigestPinned,
            libraryProfile,
            onProgress
        )
        downloader.removeInstalledArchive(uri)
        return installed
    }

    suspend fun importDocuments(
        uris: List<Uri>,
        onProgress: (Long, Long) -> Unit
    ): PackageEntity = installer.importDocuments(uris, onProgress)

    suspend fun downloadAndInstall(
        asset: DownloadablePackage,
        onDownloadProgress: (Long, Long) -> Unit,
        onReadyToInstall: () -> Unit,
        onInstallProgress: (Long) -> Unit
    ): PackageEntity {
        require(asset.manifest.kind != "library" || asset.libraryProfile != null) {
            "CATALOG_LIBRARY_PROFILE"
        }
        downloader.remember(asset)
        val archive = downloader.download(asset, onDownloadProgress)
        val uri = Uri.fromFile(archive)
        if (asset.format == DownloadFormat.BLOBS) {
            require(installer.inspect(uri) == asset.manifest) { "PACKAGE_MANIFEST" }
        }
        onReadyToInstall()
        return install(
            uri,
            asset.manifest,
            catalogDigestPinned = true,
            libraryProfile = asset.libraryProfile,
            onProgress = onInstallProgress
        )
    }
}
