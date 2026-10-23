package dev.blobs.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(
    tableName = "turns",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversationId"),
        Index(
            value = ["conversationId", "status", "createdAt", "id"],
            name = "index_turns_context"
        )
    ]
)
data class TurnEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val question: String,
    val answer: String,
    val status: String,
    val model: String,
    val scope: String,
    val evidence: String,
    val createdAt: Long,
    val elapsedMs: Long = 0,
    val error: String = "",
    @ColumnInfo(defaultValue = "''") val libraryScope: String = "",
    @ColumnInfo(defaultValue = "'LEGACY'") val grounding: String = TurnGrounding.LEGACY.storedValue,
    @ColumnInfo(defaultValue = "'LEGACY'") val finishReason: String = TurnFinishReason.LEGACY.storedValue
)
