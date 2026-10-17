package dev.blobs.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC, id ASC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun find(id: String): ConversationEntity?

    @Upsert
    suspend fun save(conversation: ConversationEntity)

    @Query("UPDATE conversations SET draft = :draft WHERE id = :id")
    suspend fun saveDraft(id: String, draft: String)

    @Query("UPDATE conversations SET title = :title, updatedAt = :time WHERE id = :id")
    suspend fun rename(id: String, title: String, time: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query(
        "SELECT * FROM turns WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC"
    )
    fun observeTurns(conversationId: String): Flow<List<TurnEntity>>

    @Query(
        "SELECT * FROM turns WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC"
    )
    suspend fun turns(conversationId: String): List<TurnEntity>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Upsert
    suspend fun saveTurn(turn: TurnEntity)

    @Query(
        "UPDATE turns SET answer = :answer, evidence = :evidence, status = :status, elapsedMs = :elapsed, error = :error WHERE id = :id AND status = 'RUNNING'"
    )
    suspend fun updateAnswer(
        id: String,
        answer: String,
        evidence: String,
        status: String,
        elapsed: Long,
        error: String
    ): Int

    @Query("UPDATE turns SET status = 'INTERRUPTED' WHERE status = 'RUNNING'")
    suspend fun recoverInterrupted()

    @Transaction
    suspend fun beginTurn(turn: TurnEntity) {
        saveTurn(turn)
        saveDraft(turn.conversationId, "")
        val conversation = requireNotNull(find(turn.conversationId))
        rename(
            conversation.id,
            if (conversation.title.isBlank()) turn.question.take(80) else conversation.title,
            turn.createdAt
        )
    }
}
