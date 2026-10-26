package dev.blobs.app.data

import dev.blobs.app.inference.NativeTurnTimings

internal data class WorkPerformanceSnapshot(
    val retrievalElapsedMs: Long,
    val firstVisibleTextElapsedMs: Long?,
    val endToEndElapsedMs: Long,
    val sourceCount: Int,
    val promptCharacters: Int,
    val historyPairCount: Int,
    val generationAttempts: Int,
    val outputTokenLimit: Int,
    val nativeStageTimings: NativeTurnTimings?
)
