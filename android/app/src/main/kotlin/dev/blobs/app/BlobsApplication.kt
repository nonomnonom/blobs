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

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!containerDelegate.isInitialized()) return

        if (level == TRIM_MEMORY_RUNNING_LOW ||
            level == TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            container.work.trimInferenceMemory()
        }
    }
}
