package dev.blobs.app

import android.content.Context
import dev.blobs.app.data.WorkController
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.local.ConversationStore
import dev.blobs.app.data.local.PackageStore
import dev.blobs.app.data.packages.PackageCatalog
import dev.blobs.app.data.packages.PackageDownloader
import dev.blobs.app.data.packages.PackageInstaller
import dev.blobs.app.data.preferences.PreferencesStore
import dev.blobs.app.data.transfer.PackageTransferExecutor
import dev.blobs.app.data.transfer.PackageTransferRecovery
import dev.blobs.app.engine.LocalResearchEngine
import dev.blobs.app.inference.LOCAL_RESEARCH_CONTEXT_TOKENS
import dev.blobs.app.inference.NativeBridge

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    internal val contextWindowTokens = LOCAL_RESEARCH_CONTEXT_TOKENS

    val preferences: PreferencesStore by lazy { PreferencesStore.create(applicationContext) }
    val database: BlobsDatabase by lazy { BlobsDatabase.open(applicationContext) }
    val library: LibraryStore by lazy { LibraryStore() }
    val installer: PackageInstaller by lazy {
        PackageInstaller(applicationContext, database.packages(), library)
    }
    val downloader: PackageDownloader by lazy { PackageDownloader(applicationContext) }
    val catalog: PackageCatalog by lazy { PackageCatalog(applicationContext) }
    private val research: LocalResearchEngine by lazy {
        LocalResearchEngine(
            library,
            NativeBridge(applicationContext.applicationInfo.nativeLibraryDir)
        )
    }
    internal val conversationStore: ConversationStore by lazy {
        ConversationStore(database.conversations())
    }
    internal val packageStore: PackageStore by lazy { PackageStore(database.packages()) }
    private val transfers: PackageTransferExecutor by lazy {
        PackageTransferExecutor(installer, downloader)
    }
    private val transferRecovery: PackageTransferRecovery by lazy {
        PackageTransferRecovery(
            installer,
            downloader,
            database.packages(),
            catalog.recoverableDownloads
        )
    }
    internal val work: WorkController by lazy {
        WorkController(
            conversationStore,
            research,
            transfers,
            transferRecovery
        )
    }
}
