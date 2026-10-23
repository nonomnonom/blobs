package dev.blobs.app.data.local

import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** Sole Room owner for conversation browsing, edits, exports, and turn lifecycle. */
internal class ConversationStore(private val conversations: ConversationDao) {
    fun observeConversations(): Flow<List<ConversationEntity>> =
        conversations.observeConversations()

    fun observeTurns(conversationId: String): Flow<List<TurnEntity>> =
        conversations.observeTurns(conversationId)

    suspend fun find(id: String): ConversationEntity? = conversations.find(id)

    suspend fun create(): ConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(UUID.randomUUID().toString(), "", "", now, now)
        conversations.save(conversation)
        return conversation
    }

    suspend fun saveDraft(conversationId: String, draft: String) =
        conversations.saveDraft(conversationId, draft, System.currentTimeMillis())

    suspend fun rename(conversationId: String, title: String, updatedAt: Long) =
        conversations.rename(conversationId, title, updatedAt)

    suspend fun delete(conversationId: String) = conversations.delete(conversationId)

    suspend fun deleteAll() = conversations.deleteAll()

    suspend fun all(): List<ConversationEntity> = conversations.all()

    suspend fun turns(conversationId: String): List<TurnEntity> =
        conversations.turns(conversationId)

    suspend fun recoverInterrupted() = conversations.recoverInterrupted()

    suspend fun begin(
        id: String,
        conversationId: String,
        question: String,
        modelEdition: String,
        scope: String,
        libraryScope: String,
        retryTurnId: String?
    ): PreparedConversationTurn? {
        val retry = retryTurnId?.let { retryId ->
            if (conversations.latestTurnId(conversationId) != retryId) return null
            conversations.turn(retryId, conversationId)?.takeIf {
                it.id == retryId && it.question == question &&
                    it.statusKind != TurnStatus.RUNNING &&
                    (
                        it.statusKind != TurnStatus.COMPLETE ||
                            it.finishReasonKind == TurnFinishReason.TOKEN_LIMIT
                        )
            } ?: return null
        }
        val contextQuestions = conversations.completedQuestions(
            conversationId,
            retry?.id,
            MAX_RETRIEVAL_CONTEXT_QUESTIONS
        ).asReversed()
        val history = conversations.completedHistory(
            conversationId,
            retry?.id,
            MAX_PROMPT_HISTORY_PAIRS
        ).asReversed().map { it.question to it.answer }
        val now = System.currentTimeMillis()
        conversations.beginTurn(
            TurnEntity(
                id = id,
                conversationId = conversationId,
                question = question,
                answer = "",
                status = TurnStatus.RUNNING.storedValue,
                model = modelEdition,
                scope = scope,
                libraryScope = libraryScope,
                grounding = TurnGrounding.PENDING.storedValue,
                finishReason = TurnFinishReason.PENDING.storedValue,
                evidence = "[]",
                createdAt = now
            ),
            conversationUpdatedAt = now,
            clearDraft = retry == null
        )
        return PreparedConversationTurn(
            contextQuestions = contextQuestions,
            history = history
        )
    }

    suspend fun update(
        id: String,
        answer: String,
        evidence: String,
        status: TurnStatus,
        elapsedMs: Long,
        error: String = "",
        grounding: TurnGrounding = TurnGrounding.PENDING,
        finishReason: TurnFinishReason = TurnFinishReason.PENDING
    ) {
        conversations.updateAnswer(
            id,
            answer,
            evidence,
            status.storedValue,
            elapsedMs,
            error,
            grounding.storedValue,
            finishReason.storedValue
        )
    }
}

internal data class PreparedConversationTurn(
    val contextQuestions: List<String>,
    val history: List<Pair<String, String>>
)

private const val MAX_RETRIEVAL_CONTEXT_QUESTIONS = 32
private const val MAX_PROMPT_HISTORY_PAIRS = 2
