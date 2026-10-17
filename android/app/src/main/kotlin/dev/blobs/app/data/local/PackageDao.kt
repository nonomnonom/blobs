package dev.blobs.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {
    @Query("SELECT * FROM packages ORDER BY installedAt DESC, key ASC")
    fun observe(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages ORDER BY installedAt DESC, key ASC")
    suspend fun all(): List<PackageEntity>

    @Upsert
    suspend fun save(value: PackageEntity)

    @Query("DELETE FROM packages WHERE `key` = :key")
    suspend fun delete(key: String)
}
