package dev.blobs.app.data.work

import android.os.SystemClock
import dev.blobs.app.R
import dev.blobs.app.data.ResearchStage
import dev.blobs.app.data.WorkPerformanceSnapshot
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.local.ConversationStore
import dev.blobs.app.data.local.LibraryScopeSnapshot
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.local.TurnFinishReason
import dev.blobs.app.data.local.TurnGrounding
import dev.blobs.app.data.local.TurnStatus
import dev.blobs.app.engine.LocalResearchEngine
import dev.blobs.app.engine.MAX_RESEARCH_OUTPUT_TOKENS
import dev.blobs.app.engine.ResearchFinish
import dev.blobs.app.engine.ResearchPhase
import dev.blobs.app.engine.ResearchRequest
import dev.blobs.app.engine.ResearchUsage
import dev.blobs.app.inference.AnswerGrounding
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.FollowUpSuggestions
import dev.blobs.app.inference.ModelSupport
import dev.blobs.app.inference.ResearchPrompt
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Owns one durable research-turn lifecycle from validation through final persistence. */
internal class ResearchWork(
    private val conversations: ConversationStore,
    private val engine: LocalResearchEngine,
    private val session: WorkSession
) {
    @Volatile
    var lastPerformanceSnapshot: WorkPerformanceSnapshot? = null
        private set

    @Volatile private var stopped = false

    @Volatile private var interrupted = false

    suspend fun answer(
        thread: String,
        question: String,
        model: PackageEntity,
        packages: List<PackageEntity>,
        allowed: Set<String>?,
        retryTurnId: String? = null
    ): Boolean {
        if (!validQuestion(question)) {
            session.update { it.copy(notice = R.string.question_invalid) }
            return false
        }
        // A retry validates the preceding turn separately, but it must never
        // reuse that turn's primary key: Room's upsert would overwrite the
        // prior partial answer and its evidence.
        val id = UUID.randomUUID().toString()
        if (!reserveAnswer(id, thread)) return false
        lastPerformanceSnapshot = null
        val accepted = CompletableDeferred<Boolean>()
        session.launchWork {
            val start = SystemClock.elapsedRealtime()
            var answer = ""
            var retrievalElapsedMs = 0L
            var firstVisibleTextElapsedMs: Long? = null
            var researchUsage: ResearchUsage? = null
            var evidence = "[]"
            var begun = false
            var checkpoint: Job? = null
            try {
                val libraryScope = LibraryScopeSnapshot.capture(allowed, packages).encode()
                val turn = conversations.begin(
                    id = id,
                    conversationId = thread,
                    question = question,
                    modelEdition = "${model.title} · ${model.version} · ${model.sha256}",
                    scope = allowed?.sorted()?.joinToString("\n") ?: "*",
                    libraryScope = libraryScope,
                    retryTurnId = retryTurnId
                ) ?: return@launchWork
                begun = true
                accepted.complete(true)
                var lastSave = 0L
                val outcome = engine.run(
                    ResearchRequest(
                        question = question,
                        contextQuestions = turn.contextQuestions,
                        history = turn.history,
                        modelPath = model.path,
                        libraries = packages,
                        scope = allowed
                    ),
                    onPhase = { phase ->
                        val stage = when (phase) {
                            ResearchPhase.SEARCHING -> ResearchStage.SEARCHING_LIBRARY
                            ResearchPhase.REVIEWING -> ResearchStage.REVIEWING_SOURCES
                            ResearchPhase.GENERATING -> ResearchStage.WRITING_ANSWER
                        }
                        session.update { it.copy(researchStage = stage) }
                    },
                    onEvidence = { sources ->
                        evidence = Evidence.encode(sources)
                        session.update { it.copy(evidence = evidence) }
                        // Evidence belongs to this running turn as soon as retrieval
                        // completes. Persist it before model loading or first-token
                        // latency so process recovery does not discard an already
                        // selected local source.
                        if (sources.isNotEmpty() && checkpoint?.isActive != true) {
                            val checkpointAnswer = answer
                            val checkpointEvidence = evidence
                            val checkpointElapsedMs = SystemClock.elapsedRealtime() - start
                            checkpoint = session.launchChild {
                                runCatching {
                                    conversations.update(
                                        id,
                                        checkpointAnswer,
                                        checkpointEvidence,
                                        TurnStatus.RUNNING,
                                        checkpointElapsedMs,
                                        ""
                                    )
                                }
                            }
                        }
                    },
                    onAnswer = { streamed ->
                        answer = streamed
                        val now = SystemClock.elapsedRealtime()
                        if (firstVisibleTextElapsedMs == null && answer.isNotBlank()) {
                            firstVisibleTextElapsedMs = now - start
                        }
                        session.update { it.copy(text = answer) }
                        if (now - lastSave > 500 && checkpoint?.isActive != true) {
                            val checkpointAnswer = answer
                            val checkpointEvidence = evidence
                            val checkpointElapsedMs = now - start
                            checkpoint = session.launchChild {
                                runCatching {
                                    conversations.update(
                                        id,
                                        checkpointAnswer,
                                        checkpointEvidence,
                                        TurnStatus.RUNNING,
                                        checkpointElapsedMs,
                                        ""
                                    )
                                }
                            }
                            lastSave = now
                        }
                    }
                )
                answer = outcome.answer
                evidence = Evidence.encode(outcome.evidence)
                retrievalElapsedMs = outcome.timings.retrievalElapsedMs
                researchUsage = outcome.usage
                val finished = terminalBody(question, answer, outcome.evidence)
                answer = finished.body
                val followUps = finished.suggestions
                session.update { it.copy(text = answer, evidence = evidence) }
                val finishReason = when {
                    interrupted || stopped || outcome.finish == ResearchFinish.CANCELLED ->
                        TurnFinishReason.CANCELLED

                    outcome.finish == ResearchFinish.TOKEN_LIMIT -> TurnFinishReason.TOKEN_LIMIT

                    outcome.finish == ResearchFinish.FINISHED && answer.isNotBlank() ->
                        TurnFinishReason.STOP

                    else -> TurnFinishReason.ERROR
                }
                val grounding = terminalGrounding(
                    answer,
                    outcome.evidence.size,
                    finishReason,
                    outcome.support
                )
                val permitsCompletion = answer.isNotBlank()
                val status = when {
                    stopped || outcome.finish == ResearchFinish.CANCELLED -> TurnStatus.STOPPED

                    outcome.finish == ResearchFinish.TOKEN_LIMIT && permitsCompletion ->
                        TurnStatus.COMPLETE

                    outcome.finish == ResearchFinish.FINISHED && permitsCompletion ->
                        TurnStatus.COMPLETE

                    else -> TurnStatus.ERROR
                }
                checkpoint?.join()
                conversations.update(
                    id,
                    answer,
                    evidence,
                    status,
                    SystemClock.elapsedRealtime() - start,
                    error = "",
                    grounding = grounding,
                    finishReason = finishReason,
                    suggestions = followUps,
                    modelSupport = outcome.support?.storedValue.orEmpty()
                )
            } catch (_: CancellationException) {
                if (begun) {
                    withContext(NonCancellable) {
                        checkpoint?.join()
                        val sources = Evidence.decodeOrNull(evidence).orEmpty()
                        val finished = terminalBody(question, answer, sources)
                        answer = finished.body
                        runCatching {
                            conversations.update(
                                id,
                                answer,
                                evidence,
                                TurnStatus.STOPPED,
                                SystemClock.elapsedRealtime() - start,
                                error = "",
                                grounding = terminalGrounding(
                                    answer,
                                    sources.size,
                                    TurnFinishReason.CANCELLED,
                                    claimed = null
                                ),
                                finishReason = TurnFinishReason.CANCELLED,
                                suggestions = finished.suggestions
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                engine.stop()
                var notice = when (error.message) {
                    "CONTEXT_FULL" -> R.string.context_full
                    "MODEL_TEMPLATE" -> R.string.model_incompatible
                    else -> R.string.answer_failed
                }
                try {
                    if (begun) {
                        checkpoint?.join()
                        val finished = terminalBody(
                            question,
                            answer,
                            Evidence.decodeOrNull(evidence).orEmpty()
                        )
                        answer = finished.body
                        conversations.update(
                            id,
                            answer,
                            evidence,
                            TurnStatus.ERROR,
                            SystemClock.elapsedRealtime() - start,
                            error = error.message.orEmpty().take(80),
                            grounding = if (answer.isBlank()) {
                                TurnGrounding.NONE
                            } else {
                                TurnGrounding.UNVERIFIED
                            },
                            finishReason = if (error.message == "CONTEXT_FULL") {
                                TurnFinishReason.CONTEXT_FULL
                            } else {
                                TurnFinishReason.ERROR
                            },
                            suggestions = finished.suggestions
                        )
                    }
                } catch (_: Exception) {
                    notice = R.string.save_failed
                }
                session.update { it.copy(notice = notice) }
            } finally {
                if (begun) {
                    val usage = researchUsage
                    lastPerformanceSnapshot = WorkPerformanceSnapshot(
                        retrievalElapsedMs,
                        firstVisibleTextElapsedMs,
                        SystemClock.elapsedRealtime() - start,
                        usage?.sourceCount ?: 0,
                        usage?.promptCharacters ?: 0,
                        usage?.historyPairCount ?: 0,
                        usage?.generationAttempts ?: 0,
                        usage?.outputTokenLimit ?: MAX_RESEARCH_OUTPUT_TOKENS
                    )
                }
                accepted.complete(false)
                session.update {
                    if (begun) {
                        it.copy(busy = false, researchStage = null)
                    } else {
                        it.copy(
                            busy = false,
                            turnId = "",
                            threadId = "",
                            text = "",
                            evidence = "[]",
                            researchStage = null
                        )
                    }
                }
            }
        }
        return accepted.await()
    }

    fun stop(background: Boolean) {
        stopped = true
        interrupted = background
        engine.stop()
        val activeJob = session.currentJob()
        if (!background) activeJob?.invokeOnCompletion { releaseModelInBackground() }
        activeJob?.cancel()
    }

    suspend fun releaseInferenceModel() = withContext(Dispatchers.IO) { engine.releaseModel() }

    fun trimInferenceMemory() = releaseModelInBackground()

    private fun reserveAnswer(id: String, thread: String): Boolean = session.reserve { current ->
        engine.reset()
        stopped = false
        interrupted = false
        current.copy(
            busy = true,
            turnId = id,
            threadId = thread,
            text = "",
            evidence = "[]",
            researchStage = ResearchStage.PREPARING_MODEL,
            startedAt = SystemClock.elapsedRealtime(),
            transferActive = false,
            notice = null
        )
    }

    private fun releaseModelInBackground() {
        session.launchChild { runCatching { engine.releaseModel() } }
    }

    private fun validQuestion(value: String): Boolean {
        if (value.isBlank() || value.length > ResearchPrompt.MAX_QUESTION_CHARACTERS ||
            '\u0000' in value
        ) {
            return false
        }
        var index = 0
        while (index < value.length) {
            when {
                Character.isHighSurrogate(value[index]) -> {
                    if (index + 1 == value.length || !Character.isLowSurrogate(value[index + 1])) {
                        return false
                    }
                    index += 2
                }

                Character.isLowSurrogate(value[index]) -> return false

                else -> index++
            }
        }
        return true
    }

    private fun terminalGrounding(
        answer: String,
        evidenceCount: Int,
        finishReason: TurnFinishReason,
        claimed: ModelSupport?
    ): TurnGrounding = when {
        answer.isBlank() -> TurnGrounding.NONE
        finishReason != TurnFinishReason.STOP -> TurnGrounding.UNVERIFIED
        else -> AnswerGrounding.decide(answer, evidenceCount, claimed)
    }

    /**
     * The canonical persisted body of one turn. Offers written into an answer by
     * an older prompt are removed, and the next questions are derived from the
     * question, evidence, and body this turn already holds.
     */
    private fun terminalBody(
        question: String,
        raw: String,
        evidence: List<Evidence>
    ): TerminalBody {
        val body = AnswerText.extractFollowUps(raw).first
        return TerminalBody(
            body,
            FollowUpSuggestions.encode(FollowUpSuggestions.from(question, body, evidence))
        )
    }
}

private data class TerminalBody(val body: String, val suggestions: String)
