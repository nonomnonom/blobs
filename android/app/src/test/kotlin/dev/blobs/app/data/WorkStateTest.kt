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

    @Test
    fun onboardingProgressNeverMovesBackwardAcrossBothPackages() {
        val setupProgress = buildList {
            for (downloaded in listOf(0L, 25L, 50L, 75L, 100L)) {
                add(
                    WorkState(
                        busy = true,
                        progress = downloaded,
                        total = 100,
                        downloading = true,
                        inAppDownload = true,
                        packageKind = "model",
                        downloadTotal = 100,
                        installTotal = 200
                    ).setupProgress(emptySet())
                )
            }
            for (installed in listOf(0L, 50L, 100L, 150L, 200L)) {
                add(
                    WorkState(
                        busy = true,
                        progress = installed,
                        total = 200,
                        inAppDownload = true,
                        packageKind = "model",
                        downloadTotal = 100,
                        installTotal = 200
                    ).setupProgress(emptySet())
                )
            }
            for (downloaded in listOf(0L, 25L, 50L, 75L, 100L)) {
                add(
                    WorkState(
                        busy = true,
                        progress = downloaded,
                        total = 100,
                        downloading = true,
                        inAppDownload = true,
                        packageKind = "library",
                        downloadTotal = 100,
                        installTotal = 100
                    ).setupProgress(setOf("model"))
                )
            }
            for (installed in listOf(0L, 25L, 50L, 75L, 100L)) {
                add(
                    WorkState(
                        busy = true,
                        progress = installed,
                        total = 100,
                        inAppDownload = true,
                        packageKind = "library",
                        downloadTotal = 100,
                        installTotal = 100
                    ).setupProgress(setOf("model"))
                )
            }
        }

        assertEquals(0f, setupProgress.first(), 0.0001f)
        assertEquals(1f, setupProgress.last(), 0.0001f)
        assertTrue(setupProgress.zipWithNext().all { (before, after) -> after >= before })
    }
}
