package dev.blobs.app.inference

private const val MAX_INFERENCE_THREADS = 4

internal fun inferenceThreadCount(availableProcessors: Int): Int =
    availableProcessors.coerceIn(1, MAX_INFERENCE_THREADS)

class TokenSink(private val receive: (ByteArray) -> Unit) {
    fun onToken(bytes: ByteArray) = receive(bytes)
}

class GenerationMetrics {
    internal val values = LongArray(15) { -1 }

    val totalUs: Long get() = values[0]
    val queueUs: Long get() = values[1]
    val modelLoadUs: Long get() = values[2]
    val templateAndTokenizeUs: Long get() = values[3]
    val contextSetupUs: Long get() = values[4]
    val prefillUs: Long get() = values[5]
    val generationLoopUs: Long get() = values[6]
    val tokenCallbackUs: Long get() = values[7]
    val firstTokenCallbackUs: Long get() = values[8]
    val promptTokens: Long get() = values[9]
    val outputTokens: Long get() = values[10]
    val nonThinkingPrefix: Boolean get() = values[11] == 1L
    val inferenceThreads: Long get() = values[12]
    val contextTokens: Long get() = values[13]
    val modelCacheHit: Boolean get() = values[14] == 1L
}

class NativeBridge {
    external fun reset()

    external fun stop()

    external fun releaseModel()

    fun generate(
        path: ByteArray,
        system: ByteArray,
        user: ByteArray,
        limit: Int,
        sink: TokenSink,
        metrics: GenerationMetrics = GenerationMetrics()
    ): Int = generateWithThreads(
        path,
        system,
        user,
        limit,
        inferenceThreadCount(Runtime.getRuntime().availableProcessors()),
        sink,
        metrics
    )

    internal fun generateWithThreads(
        path: ByteArray,
        system: ByteArray,
        user: ByteArray,
        limit: Int,
        threads: Int,
        sink: TokenSink,
        metrics: GenerationMetrics
    ): Int {
        require(threads in 1..MAX_INFERENCE_THREADS)
        return generateMeasured(path, system, user, limit, threads, sink, metrics.values)
    }

    private external fun generateMeasured(
        path: ByteArray,
        system: ByteArray,
        user: ByteArray,
        limit: Int,
        threads: Int,
        sink: TokenSink,
        metrics: LongArray
    ): Int

    companion object {
        init {
            System.loadLibrary("blobs")
        }
    }
}
