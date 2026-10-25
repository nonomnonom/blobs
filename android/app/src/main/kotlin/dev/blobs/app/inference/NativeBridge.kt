package dev.blobs.app.inference

import org.json.JSONObject

private const val MAX_INFERENCE_THREADS = 4

internal fun inferenceThreads(availableProcessors: Int): Int =
    availableProcessors.coerceIn(1, MAX_INFERENCE_THREADS)

/** Receives UTF-8 token fragments from a reusable JNI buffer. */
internal class TokenSink(private val receive: (ByteArray, Int) -> Boolean) {
    fun onToken(bytes: ByteArray, size: Int): Boolean {
        require(size in 0..bytes.size) { "TOKEN_SIZE" }
        return receive(bytes, size)
    }
}

/**
 * Stage costs of the most recent native completion. A stage that did not run reads -1, which keeps a
 * cancelled or failed turn distinguishable from a stage that took no measurable time.
 */
internal data class NativeTurnTimings(
    val loadMs: Long,
    val templateMs: Long,
    val tokenizeMs: Long,
    val contextPrepareMs: Long,
    val grammarMs: Long,
    val samplerMs: Long,
    val prefillMs: Long,
    val firstTokenMs: Long,
    val totalMs: Long,
    val modelWasCached: Boolean,
    val reusedPrefixTokens: Int,
    val promptTokens: Int,
    val contextCapacity: Int
) {
    fun json(): JSONObject = JSONObject().apply {
        put("load_ms", loadMs)
        put("template_ms", templateMs)
        put("tokenize_ms", tokenizeMs)
        put("context_prepare_ms", contextPrepareMs)
        put("grammar_ms", grammarMs)
        put("sampler_ms", samplerMs)
        put("prefill_ms", prefillMs)
        put("first_token_ms", firstTokenMs)
        put("total_ms", totalMs)
        put("model_cached", modelWasCached)
        put("reused_prefix_tokens", reusedPrefixTokens)
        put("prompt_tokens", promptTokens)
        put("context_capacity", contextCapacity)
    }

    companion object {
        val unavailable = NativeTurnTimings(-1, -1, -1, -1, -1, -1, -1, -1, -1, false, -1, -1, -1)

        /** The JNI side returns these thirteen slots in this order; a short array is a failed read. */
        fun fromNative(values: LongArray?): NativeTurnTimings = if (values == null || values.size < 13) {
            unavailable
        } else {
            NativeTurnTimings(
                loadMs = values[0],
                templateMs = values[1],
                tokenizeMs = values[2],
                contextPrepareMs = values[3],
                grammarMs = values[4],
                samplerMs = values[5],
                prefillMs = values[6],
                firstTokenMs = values[7],
                totalMs = values[8],
                modelWasCached = values[9] == 1L,
                reusedPrefixTokens = values[10].toInt(),
                promptTokens = values[11].toInt(),
                contextCapacity = values[12].toInt()
            )
        }
    }
}

/**
 * The sole model boundary. It has no socket, HTTP client, API key, or agent loop.
 * A model stays loaded until the selected model changes or the app explicitly releases it.
 */
internal class NativeBridge(private val backendDirectory: String = "") {
    external fun reset()

    external fun stop()

    external fun releaseModel()

    /** Stage costs of the last [generate] call on this process. */
    fun lastTurnTimings(): NativeTurnTimings = NativeTurnTimings.fromNative(lastTimingsNative())

    private external fun lastTimingsNative(): LongArray?

    fun generate(
        path: ByteArray,
        system: ByteArray,
        user: ByteArray,
        grammar: ByteArray,
        grammarRoot: ByteArray,
        limit: Int,
        sink: TokenSink
    ): Int = generateNative(
        path,
        system,
        user,
        grammar,
        grammarRoot,
        backendDirectory.toByteArray(Charsets.UTF_8),
        limit,
        inferenceThreads(Runtime.getRuntime().availableProcessors()),
        LOCAL_RESEARCH_CONTEXT_TOKENS,
        sink
    )

    private external fun generateNative(
        path: ByteArray,
        system: ByteArray,
        user: ByteArray,
        grammar: ByteArray,
        grammarRoot: ByteArray,
        backendDirectory: ByteArray,
        limit: Int,
        threads: Int,
        contextTokens: Int,
        sink: TokenSink
    ): Int

    companion object {
        const val FINISHED = 0
        const val CANCELLED = 1
        const val TOKEN_LIMIT = 2

        init {
            System.loadLibrary("blobs")
        }
    }
}
