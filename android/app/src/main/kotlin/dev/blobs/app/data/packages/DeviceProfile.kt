package dev.blobs.app.data.packages

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

data class DeviceProfile(
    val totalMemoryBytes: Long,
    val freeStorageBytes: Long,
    val lowMemoryDevice: Boolean,
    val recommendedModel: DownloadablePackage
) {
    companion object {
        fun inspect(context: Context, catalog: PackageCatalog): DeviceProfile {
            val memory = ActivityManager.MemoryInfo()
            val activityManager = context.getSystemService(ActivityManager::class.java)
            activityManager.getMemoryInfo(memory)
            val lowRamDevice = activityManager.isLowRamDevice
            val freeStorage = StatFs(context.filesDir.path).availableBytes
            return DeviceProfile(
                totalMemoryBytes = memory.totalMem.coerceAtLeast(1),
                freeStorageBytes = freeStorage,
                lowMemoryDevice = lowRamDevice,
                recommendedModel = if (lowRamDevice) {
                    catalog.models.first()
                } else {
                    catalog.recommendedModel(memory.totalMem, freeStorage)
                }
            )
        }
    }
}
