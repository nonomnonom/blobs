package dev.blobs.app.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceContextPolicyTest {
    @Test
    fun contextWindowGrowsWithDeviceMemory() {
        assertEquals(SMALL_CONTEXT_TOKENS, contextWindowForRam(4L shl 30))
        assertEquals(MEDIUM_CONTEXT_TOKENS, contextWindowForRam(6L shl 30))
        assertEquals(LARGE_CONTEXT_TOKENS, contextWindowForRam(8L shl 30))
        assertEquals(PHONE_CONTEXT_TOKENS, contextWindowForRam(12L shl 30))
        assertEquals(MAX_CONTEXT_TOKENS, contextWindowForRam(16L shl 30))
    }
}
