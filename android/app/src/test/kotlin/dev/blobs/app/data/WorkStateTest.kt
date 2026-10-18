package dev.blobs.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkStateTest {
    @Test
    fun setupProgressContinuesAcrossBothDownloadsAndInstallPhases() {
        val modelDownload = WorkState(
            busy = true,
            progress = 60,
            total = 100,
            downloading = true,
            inAppDownload = true,
            packageKind = "model",
            downloadTotal = 100,
            installTotal = 200
        )
        assertEquals(0.1f, modelDownload.setupProgress(emptySet()), 0.0001f)

        val modelDownloadEnd = modelDownload.copy(progress = 100)
        val modelInstall = modelDownloadEnd.copy(progress = 0, total = 200, downloading = false)
        assertEquals(
            modelDownloadEnd.setupProgress(emptySet()),
            modelInstall.setupProgress(emptySet()),
            0.0001f
        )
        assertEquals(0.5f, modelInstall.copy(progress = 200).setupProgress(emptySet()), 0.0001f)

        val libraryDownload = WorkState(
            busy = true,
            progress = 50,
            total = 100,
            downloading = true,
            inAppDownload = true,
            packageKind = "library",
            downloadTotal = 100,
            installTotal = 100
        )
        assertEquals(0.625f, libraryDownload.setupProgress(setOf("model")), 0.0001f)
        assertEquals(
            1f,
            libraryDownload.copy(importComplete = true)
                .setupProgress(setOf("model")),
            0.0001f
        )
    }

    @Test
    fun importedPackageProgressUsesItsSingleInstallPhase() {
        val work = WorkState(progress = 25, total = 100)

        assertEquals(0.25f, work.phaseProgress, 0.0001f)
        assertTrue(work.setupProgress(setOf("model")) > 0f)
    }
}
