package dev.blobs.app.inference

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
 * The sole model boundary. It has no socket, HTTP client, API key, or agent loop.
 * A model stays loaded until the selected model changes or the app explicitly releases it.
 */
internal class NativeBridge(private val backendDirectory: String = "") {
    external fun reset()

    external fun stop()

    external fun releaseModel()

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
