package dev.blobs.app.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeBridgeTest {
    @Test
    fun inferenceThreadsFollowAvailableProcessorsWithinSupportedRange() {
        assertEquals(1, inferenceThreadCount(0))
        assertEquals(1, inferenceThreadCount(1))
        assertEquals(2, inferenceThreadCount(2))
        assertEquals(4, inferenceThreadCount(4))
        assertEquals(4, inferenceThreadCount(8))
    }
}
