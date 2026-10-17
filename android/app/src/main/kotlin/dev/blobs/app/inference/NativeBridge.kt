package dev.blobs.app.inference

class TokenSink(private val receive: (ByteArray) -> Unit) {
    fun onToken(bytes: ByteArray) = receive(bytes)
}

class GenerationMetrics {
    internal val values = LongArray(12) { -1 }

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
}

class NativeBridge {
    external fun reset()

    external fun stop()

    fun generate(
        path: ByteArray,
        system: ByteArray,
        user: ByteArray,
        limit: Int,
        sink: TokenSink,
        metrics: GenerationMetrics = GenerationMetrics()
    ): Int = generateMeasured(path, system, user, limit, sink, metrics.values)

    private external fun generateMeasured(
        path: ByteArray,
        system: ByteArray,
        user: ByteArray,
        limit: Int,
        sink: TokenSink,
        metrics: LongArray
    ): Int

    companion object {
        init {
            System.loadLibrary("blobs")
        }
    }
}
