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

    @Query("UPDATE conversations SET draft = :draft, updatedAt = :updatedAt WHERE id = :id")
    suspend fun saveDraft(id: String, draft: String, updatedAt: Long)

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

    @Query(
        "SELECT * FROM turns WHERE id = :id AND conversationId = :conversationId LIMIT 1"
    )
    suspend fun turn(id: String, conversationId: String): TurnEntity?

    @Query(
        "SELECT id FROM turns WHERE conversationId = :conversationId " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1"
    )
    suspend fun latestTurnId(conversationId: String): String?

    @Query(
        "SELECT question FROM turns WHERE conversationId = :conversationId " +
            "AND status = 'COMPLETE' AND answer != '' " +
            "AND (:excludedTurnId IS NULL OR id != :excludedTurnId) " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit"
    )
    suspend fun completedQuestions(
        conversationId: String,
        excludedTurnId: String?,
        limit: Int
    ): List<String>

    @Query(
        "SELECT question,answer FROM turns WHERE conversationId = :conversationId " +
            "AND status = 'COMPLETE' AND answer != '' " +
            "AND (:excludedTurnId IS NULL OR id != :excludedTurnId) " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit"
    )
    suspend fun completedHistory(
        conversationId: String,
        excludedTurnId: String?,
        limit: Int
    ): List<ConversationHistory>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Upsert
    suspend fun saveTurn(turn: TurnEntity)

    @Query(
        "UPDATE turns SET answer = :answer, evidence = :evidence, status = :status, elapsedMs = :elapsed, error = :error, grounding = :grounding, finishReason = :finishReason WHERE id = :id AND status = 'RUNNING'"
    )
    suspend fun updateAnswer(
        id: String,
        answer: String,
        evidence: String,
        status: String,
        elapsed: Long,
        error: String,
        grounding: String,
        finishReason: String
    ): Int

    @Query(
        "UPDATE turns SET status = 'STOPPED', " +
            "grounding = CASE WHEN answer != '' THEN 'UNVERIFIED' ELSE 'NONE' END, " +
            "finishReason = 'CANCELLED' WHERE status = 'RUNNING'"
    )
    suspend fun recoverInterrupted()

    @Transaction
    suspend fun beginTurn(
        turn: TurnEntity,
        clearDraft: Boolean = true,
        conversationUpdatedAt: Long = turn.createdAt
    ) {
        saveTurn(turn)
        if (clearDraft) saveDraft(turn.conversationId, "", conversationUpdatedAt)
        val conversation = requireNotNull(find(turn.conversationId))
        rename(
            conversation.id,
            if (conversation.title.isBlank()) {
                truncateUtf16(turn.question, 80)
            } else {
                conversation.title
            },
            conversationUpdatedAt
        )
    }
}

data class ConversationHistory(val question: String, val answer: String)

private fun truncateUtf16(value: String, maximumLength: Int): String {
    var end = minOf(value.length, maximumLength)
    if (end < value.length && end > 0 && Character.isHighSurrogate(value[end - 1]) &&
        Character.isLowSurrogate(value[end])
    ) {
        end--
    }
    return value.substring(0, end)
}
