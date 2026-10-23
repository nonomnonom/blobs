package dev.blobs.app.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceThreadPolicyTest {
    @Test
    fun capsInferenceThreadsForDeviceCompatibility() {
        assertEquals(1, inferenceThreads(0))
        assertEquals(1, inferenceThreads(1))
        assertEquals(2, inferenceThreads(2))
        assertEquals(4, inferenceThreads(4))
        assertEquals(4, inferenceThreads(8))
    }
}
