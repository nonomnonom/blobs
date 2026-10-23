package dev.blobs.app.inference

/** Runtime capacity is fixed until a larger native context is profiled on target devices. */
internal fun contextWindowForRam(@Suppress("UNUSED_PARAMETER") totalMemoryBytes: Long): Int =
    LOCAL_RESEARCH_CONTEXT_TOKENS

internal const val LOCAL_RESEARCH_CONTEXT_TOKENS = 4096

// Retained for existing callers while device-RAM tiers are intentionally inactive.
internal const val SMALL_CONTEXT_TOKENS = LOCAL_RESEARCH_CONTEXT_TOKENS
internal const val MEDIUM_CONTEXT_TOKENS = LOCAL_RESEARCH_CONTEXT_TOKENS
internal const val LARGE_CONTEXT_TOKENS = LOCAL_RESEARCH_CONTEXT_TOKENS
internal const val PHONE_CONTEXT_TOKENS = LOCAL_RESEARCH_CONTEXT_TOKENS
internal const val MAX_CONTEXT_TOKENS = LOCAL_RESEARCH_CONTEXT_TOKENS
internal const val MINIMUM_MODEL_CONTEXT_TOKENS = LOCAL_RESEARCH_CONTEXT_TOKENS
