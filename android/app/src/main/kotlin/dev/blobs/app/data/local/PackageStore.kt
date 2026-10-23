package dev.blobs.app.data.local

import kotlinx.coroutines.flow.Flow

/** Room boundary for package state read and changed by application UI. */
internal class PackageStore(private val packages: PackageDao) {
    fun observe(): Flow<List<PackageEntity>> = packages.observe()

    suspend fun all(): List<PackageEntity> = packages.all()

    suspend fun setLibraryEnabled(key: String, enabled: Boolean) = packages.setEnabled(key, enabled)
}
