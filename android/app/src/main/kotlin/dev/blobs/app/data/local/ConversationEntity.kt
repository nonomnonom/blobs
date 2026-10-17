package dev.blobs.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val draft: String,
    val createdAt: Long,
    val updatedAt: Long
)
