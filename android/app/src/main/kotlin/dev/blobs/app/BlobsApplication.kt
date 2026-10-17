package dev.blobs.app

import android.app.Application

class BlobsApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
