package dev.blobs.app.data

import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.PendingImport

data class WorkState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val turnId: String = "",
    val threadId: String = "",
    val text: String = "",
    val evidence: String = "[]",
    val startedAt: Long = 0,
    val progress: Long = 0,
    val total: Long = 0,
    val importTitle: String = "",
    val importComplete: Boolean = false,
    val notice: Int? = null,
    val pendingImport: PendingImport? = null,
    val downloading: Boolean = false,
    val inAppDownload: Boolean = false,
    val packageKind: String = "",
    val downloadTotal: Long = 0,
    val installTotal: Long = 0,
    val download: DownloadablePackage? = null,
    val transferActive: Boolean = false
) {
    val hasPendingTransfer: Boolean
        get() = pendingImport != null || (download != null && !importComplete)

    val phaseProgress: Float
        get() {
            val denominator = downloadTotal + installTotal
            if (denominator <= 0) {
                return if (total > 0) (progress.toFloat() / total).coerceIn(0f, 1f) else 0f
            }
            val completed = if (downloading) {
                progress.coerceAtMost(downloadTotal)
            } else {
                downloadTotal + progress.coerceAtMost(installTotal)
            }
            return (completed.toDouble() / denominator).toFloat().coerceIn(0f, 1f)
        }

    fun setupProgress(readyKinds: Set<String>): Float {
        val kinds = setOf("model", "library")
        val ready = kinds.intersect(readyKinds)
        var progress = ready.size.toFloat()
        if (inAppDownload && packageKind in kinds && packageKind !in ready) {
            progress += when {
                importComplete -> 1f
                else -> phaseProgress
            }
        }
        val fraction = (progress / kinds.size).coerceIn(0f, 1f)
        return if (inAppDownload && !importComplete) fraction.coerceAtMost(.99f) else fraction
    }
}
