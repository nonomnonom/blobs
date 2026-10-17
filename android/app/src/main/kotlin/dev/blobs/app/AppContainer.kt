package dev.blobs.app

import android.content.Context
import dev.blobs.app.data.WorkController
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.packages.PackageCatalog
import dev.blobs.app.data.packages.PackageDownloader
import dev.blobs.app.data.packages.PackageInstaller
import dev.blobs.app.data.preferences.PreferencesStore

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val preferences: PreferencesStore by lazy { PreferencesStore.create(applicationContext) }
    val database: BlobsDatabase by lazy { BlobsDatabase.open(applicationContext) }
    val library: LibraryStore by lazy { LibraryStore() }
    val installer: PackageInstaller by lazy {
        PackageInstaller(applicationContext, database.packages(), library)
    }
    val downloader: PackageDownloader by lazy { PackageDownloader(applicationContext) }
    val catalog: PackageCatalog by lazy { PackageCatalog(applicationContext) }
    val work: WorkController by lazy {
        WorkController(database, library, installer, downloader)
    }
}
