package dev.blobs.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {
    @Query("SELECT * FROM packages ORDER BY installedAt DESC, key ASC")
    fun observe(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages ORDER BY installedAt DESC, key ASC")
    suspend fun all(): List<PackageEntity>

    @Upsert
    suspend fun upsert(value: PackageEntity)

    @Query(
        "UPDATE packages SET enabled = 0 WHERE packageId = :packageId AND kind = 'library' AND `key` != :exceptKey"
    )
    suspend fun disableOtherEditions(packageId: String, exceptKey: String)

    @Transaction
    suspend fun save(value: PackageEntity) {
        if (value.kind == "library" && value.enabled) {
            disableOtherEditions(value.packageId, value.key)
        }
        upsert(value)
    }

    @Transaction
    suspend fun setEnabled(key: String, enabled: Boolean) {
        val pack = all().find { it.key == key && it.kind == "library" } ?: return
        save(pack.copy(enabled = enabled))
    }

    @Transaction
    suspend fun reconcileActiveEditions() {
        all().filter { it.kind == "library" && it.enabled }
            .groupBy { it.packageId }.values.forEach { editions ->
                disableOtherEditions(editions.first().packageId, editions.first().key)
            }
    }

    @Query("DELETE FROM packages WHERE `key` = :key")
    suspend fun delete(key: String)
}
