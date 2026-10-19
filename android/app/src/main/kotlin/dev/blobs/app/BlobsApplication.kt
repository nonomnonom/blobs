package dev.blobs.app

import android.app.Application

class BlobsApplication : Application() {
    private val containerDelegate = lazy { AppContainer(this) }
    val container: AppContainer by containerDelegate

    override fun onLowMemory() {
        super.onLowMemory()
        if (containerDelegate.isInitialized()) {
            container.work.trimInferenceMemory()
        }
    }
}
