package dev.blobs.app.engine

import android.os.SystemClock
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.library.RetrievalQuery
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.inference.AnswerText
import dev.blobs.app.inference.CitedDigest
import dev.blobs.app.inference.ModelSupport
import dev.blobs.app.inference.NativeBridge
import dev.blobs.app.inference.NativeTurnTimings
import dev.blobs.app.inference.ResearchPrompt
import dev.blobs.app.inference.StructuredAnswer
import dev.blobs.app.inference.TokenSink

// Owner-set answer bound for every mode (2026-10-25): once prefill stopped dominating the turn, the
// longest remaining tail was decoding prose the reader did not need. It also frees 120 context tokens
// for the prompt, since the request must fit prompt plus output in one fixed capacity.
internal const val MAX_RESEARCH_OUTPUT_TOKENS = 200
private const val STREAM_RENDER_INTERVAL_MS = 50L
private const val MAX_RESEARCH_OUTPUT_BYTES = 32 * 1024

internal data class ResearchRequest(
    val question: String,
    val contextQuestions: List<String>,
    val history: List<Pair<String, String>>,
    val modelPath: String,
    val libraries: List<PackageEntity>,
    val scope: Set<String>?
)

internal enum class ResearchPhase {
    SEARCHING,
    REVIEWING,
    GENERATING
}

internal enum class ResearchFinish {
    FINISHED,
    CANCELLED,
    TOKEN_LIMIT,
    FAILED
}

internal data class ResearchTimings(
    val retrievalElapsedMs: Long,
    val firstAnswerElapsedMs: Long?,
    val totalElapsedMs: Long,
    val native: NativeTurnTimings = NativeTurnTimings.unavailable
)

internal data class ResearchUsage(
    val sourceCount: Int,
    val promptCharacters: Int,
    val historyPairCount: Int,
    val generationAttempts: Int,
    val outputTokenLimit: Int
)

internal data class ResearchOutcome(
    val answer: String,
    val support: ModelSupport?,
    val evidence: List<Evidence>,
    val finish: ResearchFinish,
    val timings: ResearchTimings,
    val usage: ResearchUsage
)

/**
 * Runs Blobs' single-pass local research flow.
 *
 * This engine owns retrieval, one bounded prompt, model streaming, and answer
 * normalization. Callers own persistence and presentation state.
 */
internal class LocalResearchEngine(
    private val library: LibraryStore,
    private val runtime: NativeBridge = NativeBridge()
) {
    private val systemPromptBytes = ResearchPrompt.system.toByteArray(Charsets.UTF_8)
    private val grammarBytes = StructuredAnswer.grammar.toByteArray(Charsets.UTF_8)
    private val grammarRootBytes = StructuredAnswer.GRAMMAR_ROOT.toByteArray(Charsets.UTF_8)

    fun reset() = runtime.reset()

    fun stop() = runtime.stop()

    fun releaseModel() = runtime.releaseModel()

    fun run(
        request: ResearchRequest,
        onPhase: (ResearchPhase) -> Unit,
        onEvidence: (List<Evidence>) -> Unit,
        onAnswer: (String) -> Unit
    ): ResearchOutcome {
        require(request.question.isNotBlank())
        val startedAt = SystemClock.elapsedRealtime()
        val query = RetrievalQuery.resolve(request.question, request.contextQuestions)

        onPhase(ResearchPhase.SEARCHING)
        val retrievalStartedAt = SystemClock.elapsedRealtime()
        val retrieved = library.retrieve(
            packages = request.libraries,
            question = query,
            scope = request.scope,
            maximumResults = ResearchPrompt.MAX_ANSWER_SOURCES
        )
        val retrievalElapsedMs = SystemClock.elapsedRealtime() - retrievalStartedAt
        // One trim, applied before anything is shown: the reader, the prompt, the citations and the
        // stored turn all describe the same passage set, and no evidence is displayed that the model
        // was not given.
        val evidence = ResearchPrompt.carriedSources(
            question = request.question,
            query = query,
            history = request.history,
            sources = retrieved
        )
        onEvidence(evidence)
        onPhase(if (evidence.isEmpty()) ResearchPhase.GENERATING else ResearchPhase.REVIEWING)

        val output = BoundedAnswerBytes(MAX_RESEARCH_OUTPUT_BYTES)
        var answer = ""
        var firstAnswerElapsedMs: Long? = null
        var renderedBytes = 0
        var lastRenderAt = 0L
        var promptCharacters = 0
        var historyPairCount = 0
        var generationAttempts = 0
        var nativeTimings = NativeTurnTimings.unavailable
        fun renderAnswer() {
            if (renderedBytes == output.size) return
            renderedBytes = output.size
            answer = AnswerText.validSourceCitations(
                readable(AnswerText.visible(output.completeUtf8Text())),
                evidence.size
            )
            if (firstAnswerElapsedMs == null && answer.isNotBlank()) {
                firstAnswerElapsedMs = SystemClock.elapsedRealtime() - startedAt
            }
            onAnswer(answer)
        }
        onPhase(ResearchPhase.GENERATING)
        val modelPathBytes = request.modelPath.toByteArray(Charsets.UTF_8)
        val nativeResult = try {
            ResearchPrompt.withContextBudget(
                question = request.question,
                query = query,
                history = request.history,
                sources = evidence
            ) { prompt ->
                generationAttempts++
                promptCharacters = prompt.text.length
                historyPairCount = prompt.historyPairCount
                val result = runtime.generate(
                    modelPathBytes,
                    systemPromptBytes,
                    prompt.text.toByteArray(Charsets.UTF_8),
                    grammarBytes,
                    grammarRootBytes,
                    MAX_RESEARCH_OUTPUT_TOKENS,
                    TokenSink { token, size ->
                        if (output.append(token, size)) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastRenderAt >= STREAM_RENDER_INTERVAL_MS) {
                                lastRenderAt = now
                                renderAnswer()
                            }
                            true
                        } else {
                            false
                        }
                    }
                )
                nativeTimings = runtime.lastTurnTimings()
                result
            }
        } finally {
            renderAnswer()
        }

        val decoded = StructuredAnswer.decode(AnswerText.visible(output.completeUtf8Text()))
        val digested = if (nativeResult == NativeBridge.CANCELLED) {
            null
        } else {
            CitedDigest.gate(answer, evidence, finished = nativeResult == NativeBridge.FINISHED)
        }
        return ResearchOutcome(
            answer = digested ?: answer,
            support = if (digested != null) ModelSupport.SOURCED else decoded?.support,
            evidence = evidence,
            finish = when (nativeResult) {
                NativeBridge.FINISHED -> ResearchFinish.FINISHED
                NativeBridge.CANCELLED -> ResearchFinish.CANCELLED
                NativeBridge.TOKEN_LIMIT -> ResearchFinish.TOKEN_LIMIT
                else -> ResearchFinish.FAILED
            },
            timings = ResearchTimings(
                retrievalElapsedMs = retrievalElapsedMs,
                firstAnswerElapsedMs = firstAnswerElapsedMs,
                totalElapsedMs = SystemClock.elapsedRealtime() - startedAt,
                native = nativeTimings
            ),
            usage = ResearchUsage(
                sourceCount = evidence.size,
                promptCharacters = promptCharacters,
                historyPairCount = historyPairCount,
                generationAttempts = generationAttempts,
                outputTokenLimit = MAX_RESEARCH_OUTPUT_TOKENS
            )
        )
    }

    /**
     * Prose a reader can see right now. A payload renders its decoded prefix, so a stream, a
     * cancellation, or a token-limited turn never shows JSON punctuation. Anything else is plain
     * text from an unconstrained completion and is shown as it arrives.
     */
    private fun readable(visible: String): String =
        if (visible.startsWith("{")) StructuredAnswer.partialText(visible).orEmpty() else visible
}

/** A bounded owned copy of the reusable JNI byte array for one answer turn. */
private class BoundedAnswerBytes(private val maximumCapacity: Int) {
    init {
        require(maximumCapacity > 0) { "OUTPUT_CAPACITY" }
    }

    private var bytes = ByteArray(minOf(INITIAL_CAPACITY, maximumCapacity))
    var size = 0
        private set

    fun append(token: ByteArray, length: Int): Boolean {
        require(length in 0..token.size) { "TOKEN_SIZE" }
        if (length > maximumCapacity - size) return false
        ensureCapacity(size + length)
        token.copyInto(bytes, destinationOffset = size, endIndex = length)
        size += length
        return true
    }

    fun completeUtf8Text(): String = AnswerText.decodeCompleteUtf8Prefix(bytes, size)

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= bytes.size) return
        var nextCapacity = bytes.size
        while (nextCapacity < requiredCapacity) {
            nextCapacity += minOf(nextCapacity, maximumCapacity - nextCapacity)
        }
        bytes = bytes.copyOf(nextCapacity)
    }

    private companion object {
        const val INITIAL_CAPACITY = 2 * 1024
    }
}
