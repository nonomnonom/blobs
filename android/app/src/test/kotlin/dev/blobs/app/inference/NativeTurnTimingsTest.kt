package dev.blobs.app.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTurnTimingsTest {
    @Test
    fun nativeSlotsMapInDocumentedOrder() {
        val timings = NativeTurnTimings.fromNative(
            longArrayOf(11, 12, 13, 14, 15, 16, 17, 18, 19, 1, 20, 21, 22)
        )

        assertEquals(11, timings.loadMs)
        assertEquals(12, timings.templateMs)
        assertEquals(13, timings.tokenizeMs)
        assertEquals(14, timings.contextPrepareMs)
        assertEquals(15, timings.grammarMs)
        assertEquals(16, timings.samplerMs)
        assertEquals(17, timings.prefillMs)
        assertEquals(18, timings.firstTokenMs)
        assertEquals(19, timings.totalMs)
        assertTrue(timings.modelWasCached)
        assertEquals(20, timings.reusedPrefixTokens)
        assertEquals(21, timings.promptTokens)
        assertEquals(22, timings.contextCapacity)
    }

    @Test
    fun anUncachedModelReadsFalseAndNotFromAnAbsentSlot() {
        assertFalse(NativeTurnTimings.fromNative(LongArray(13)).modelWasCached)
    }

    @Test
    fun aMissingNativeReadIsAllAbsentSlots() {
        assertEquals(NativeTurnTimings.unavailable, NativeTurnTimings.fromNative(null))
        assertEquals(NativeTurnTimings.unavailable, NativeTurnTimings.fromNative(longArrayOf(1, 2, 3)))
        assertTrue(NativeTurnTimings.unavailable.loadMs < 0)
        assertTrue(NativeTurnTimings.unavailable.promptTokens < 0)
    }

    @Test
    fun stagesAreDisjointSpansInsideTheTotal() {
        val timings = NativeTurnTimings.fromNative(
            longArrayOf(2400, 40, 25, 900, 30, 5, 5100, 300, 8800, 1, 120, 780, 2048)
        )
        val stages = listOf(
            timings.loadMs,
            timings.templateMs,
            timings.tokenizeMs,
            timings.contextPrepareMs,
            timings.grammarMs,
            timings.samplerMs,
            timings.prefillMs
        )
        assertTrue(stages.sum() <= timings.totalMs)
        assertTrue(timings.firstTokenMs <= timings.totalMs)
    }
}
