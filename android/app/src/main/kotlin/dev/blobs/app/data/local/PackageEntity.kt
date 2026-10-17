package dev.blobs.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packages")
data class PackageEntity(
    @PrimaryKey val key: String,
    val packageId: String,
    val version: String,
    val kind: String,
    val title: String,
    val path: String,
    val bytes: Long,
    val sha256: String,
    val license: String,
    val origin: String,
    val installedAt: Long
)
