package dev.blobs.app.ui

import android.net.Uri
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.packages.PackageManifest
import dev.blobs.app.data.preferences.PreferencesStore

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

enum class SetupStep { MODEL, LIBRARY, READY }

data class ProductState(
    val page: Page = Page.CHAT,
    val draft: String = "",
    val options: PreferencesStore.Options = PreferencesStore.Options(),
    val loaded: Boolean = false,
    val setupStep: SetupStep? = null,
    val notice: Int? = null,
    val sources: List<Evidence> = emptyList(),
    val query: String = "",
    val browseCollection: String? = null,
    val offset: Int = 0,
    val hasMore: Boolean = false,
    val searching: Boolean = false,
    val reader: Evidence? = null,
    val readerIsFull: Boolean = false,
    val importOrigin: Page = Page.SETUP,
    val importUri: Uri? = null,
    val manifest: PackageManifest? = null
)
