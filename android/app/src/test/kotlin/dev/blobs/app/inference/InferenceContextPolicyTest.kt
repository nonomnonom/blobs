package dev.blobs.app.inference

import dev.blobs.app.engine.MAX_RESEARCH_OUTPUT_TOKENS
import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceContextPolicyTest {
    @Test
    fun runtimeCapacityIsOneFixedValueSharedByEveryTier() {
        assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, contextWindowForRam(2L shl 30))
        assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, contextWindowForRam(16L shl 30))
        assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, SMALL_CONTEXT_TOKENS)
        assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, MEDIUM_CONTEXT_TOKENS)
        assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, LARGE_CONTEXT_TOKENS)
        assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, PHONE_CONTEXT_TOKENS)
        assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS)
        assertEquals(LOCAL_RESEARCH_CONTEXT_TOKENS, MINIMUM_MODEL_CONTEXT_TOKENS)
    }

    @Test
    fun theFixedCapacityIsTheNativeCeilingAndLeavesRoomForTheWholeAnswer() {
        // The runtime asks for exactly this many tokens every turn, so the output bound has to fit
        // inside it beside a full prompt; a prompt at the ceiling is rejected, not re-bucketed.
        assertEquals(4096, LOCAL_RESEARCH_CONTEXT_TOKENS)
        assertEquals(320, MAX_RESEARCH_OUTPUT_TOKENS)
    }
}
