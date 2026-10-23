package dev.blobs.app

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.WorkController
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.data.local.ConversationStore
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.DownloadFormat
import dev.blobs.app.data.packages.DownloadablePackage
import dev.blobs.app.data.packages.LibraryProfile
import dev.blobs.app.data.packages.PackageDownloader
import dev.blobs.app.data.packages.PackageInstaller
import dev.blobs.app.data.packages.PackageRole
import dev.blobs.app.data.transfer.PackageTransferExecutor
import dev.blobs.app.data.transfer.PackageTransferRecovery
import dev.blobs.app.engine.LocalResearchEngine
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkFlowTest {
    @Test
    fun retryReusesOnlyLastUnfinishedTurnAndPreservesUnsentDraft() = isolated {
            db,
            installer,
            root
        ->
        val downloader = PackageDownloader(root, { Long.MAX_VALUE })
        val work = workController(db, installer, downloader)
        withTimeout(10_000) { work.state.first { it.ready } }
        db.conversations().save(ConversationEntity("thread", "", "", 1, 1))
        val model = PackageEntity(
            "fixture", "fixture", "1", "model", "Test model", "unused", 1,
            "0".repeat(64), "Test", "local:test", 1
        )
        assertTrue(work.answer("thread", "A question without sources", model, emptyList(), null))
        withTimeout(10_000) { work.state.first { !it.busy } }
        val original = db.conversations().turns("thread").single()
        assertEquals("ERROR", original.status)
        db.conversations().saveDraft("thread", "Keep my next question", 2)
        assertTrue(work.answer("thread", original.question, model, emptyList(), null, original.id))
        withTimeout(10_000) { work.state.first { !it.busy } }
        val turns = db.conversations().turns("thread")
        assertEquals(2, turns.size)
        val retried = turns.single { it.id != original.id }
        assertNotEquals(original.id, retried.id)
        assertEquals(original.question, retried.question)
        assertTrue(retried.createdAt >= original.createdAt)
        assertEquals("ERROR", turns.single { it.id == original.id }.status)
        assertEquals("Keep my next question", db.conversations().find("thread")?.draft)
        db.conversations().saveTurn(retried.copy(status = "COMPLETE", answer = "Saved answer"))
        assertFalse(work.answer("thread", original.question, model, emptyList(), null, original.id))
        assertEquals(
            "Saved answer",
            db.conversations().turns("thread").single { it.id == retried.id }.answer
        )
    }

    @Test
    fun failedDownloadKeepsItsContextAndResumeInstallsVerifiedLibrary() = isolated {
            db,
            installer,
            root
        ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("corpus-fixture.blobs").use {
            it.readBytes()
        }
        val archive = File(root.parentFile, "fixture.blobs").apply { writeBytes(bytes) }
        val manifest = installer.inspect(Uri.fromFile(archive))
        val asset =
            DownloadablePackage(
                "fixture.blobs",
                "https://huggingface.co/datasets/test/resolve/1111111111111111111111111111111111111111/fixture.blobs",
                bytes.size.toLong(),
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                },
                DownloadFormat.BLOBS,
                manifest,
                PackageRole.OPTIONAL,
                Long.MAX_VALUE,
                libraryProfile = LibraryProfile(
                    recordCount = 1,
                    scope = "Fixture library",
                    sourceSnapshotDate = "2026-01-01",
                    updatePolicy = "Static test fixture",
                    limitations = "Not production data"
                )
            )
        var requests = 0
        var range: String? = null
        val downloader = PackageDownloader(root, { Long.MAX_VALUE }) { url ->
            requests++
            object : HttpURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode() = if (requests == 1) HTTP_OK else HTTP_PARTIAL
                override fun getHeaderField(name: String): String? = when (name.lowercase()) {
                    "etag" -> "\"fixture\""

                    "content-length" -> (
                        if (requests ==
                            1
                        ) {
                            bytes.size
                        } else {
                            bytes.size - 512
                        }
                        ).toString()

                    "content-range" -> if (requests >
                        1
                    ) {
                        "bytes 512-${bytes.size - 1}/${bytes.size}"
                    } else {
                        null
                    }

                    else -> null
                }
                override fun getInputStream(): InputStream {
                    range = getRequestProperty("Range")
                    if (requests > 1) return ByteArrayInputStream(bytes, 512, bytes.size - 512)
                    return object : ByteArrayInputStream(bytes) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            if (pos >= 512) throw IOException("Connection dropped")
                            return super.read(buffer, offset, minOf(length, 512 - pos))
                        }
                    }
                }
            }
        }
        val work = workController(db, installer, downloader)
        withTimeout(10_000) { work.state.first { it.ready } }
        assertTrue(work.downloadAndInstall(asset))
        val paused = withTimeout(10_000) { work.state.first { !it.busy && it.hasPendingTransfer } }
        assertEquals(asset, paused.download)
        assertEquals(512L, paused.progress)
        assertFalse(paused.transferActive)
        assertEquals(R.string.download_failed, paused.notice)
        assertEquals(null, downloader.pending(listOf(asset.copy(downloadSha256 = "0".repeat(64)))))
        val restarted = workController(db, installer, downloader, listOf(asset))
        withTimeout(10_000) { restarted.state.first { it.ready } }
        assertEquals(asset, restarted.state.value.download)
        assertEquals(512L, restarted.state.value.progress)
        restarted.resumeImport()
        withTimeout(10_000) { restarted.state.first { !it.busy && it.importComplete } }
        assertEquals("bytes=512-", range)
        assertEquals(manifest.key, db.packages().all().single().key)
        assertFalse(restarted.state.value.hasPendingTransfer)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cancellingAnActiveDownloadRemovesOnlyItsStagingFiles() = isolated { db, installer, root ->
        val bytes = ByteArray(1024) { 7 }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val manifest = dev.blobs.app.data.packages.PackageManifest(
            "cancel-fixture",
            "1",
            "library",
            "Cancellation fixture",
            bytes.size.toLong(),
            "0".repeat(64),
            "Test",
            "local:test"
        )
        val asset = DownloadablePackage(
            "cancel-fixture.blobs",
            "https://github.com/nonomnonom/blobs/releases/download/test/cancel-fixture.blobs",
            bytes.size.toLong(),
            "0".repeat(64),
            DownloadFormat.BLOBS,
            manifest,
            PackageRole.OPTIONAL,
            Long.MAX_VALUE,
            libraryProfile = LibraryProfile(
                recordCount = 1,
                scope = "Cancellation fixture",
                sourceSnapshotDate = "2026-01-01",
                updatePolicy = "Static test fixture",
                limitations = "Not production data"
            )
        )
        val downloader = PackageDownloader(root, { Long.MAX_VALUE }) { url ->
            object : HttpURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode() = HTTP_OK
                override fun getHeaderField(name: String) = when (name.lowercase()) {
                    "etag" -> "\"fixture\""
                    "content-length" -> bytes.size.toString()
                    else -> null
                }
                override fun getInputStream(): InputStream = object : ByteArrayInputStream(bytes) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        entered.countDown()
                        check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        return super.read(buffer, offset, minOf(length, 512))
                    }
                }
            }
        }
        val work = workController(db, installer, downloader)
        withTimeout(10_000) { work.state.first { it.ready } }
        val unrelated = File(root.parentFile, "keep.txt").apply { writeText("Keep this file") }
        assertTrue(work.downloadAndInstall(asset))
        try {
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            work.discardImport()
        } finally {
            release.countDown()
        }
        withTimeout(10_000) { work.state.first { !it.busy && !it.hasPendingTransfer } }
        assertEquals(R.string.import_cancelled, work.state.value.notice)
        assertTrue(root.listFiles().orEmpty().isEmpty())
        assertTrue(db.packages().all().isEmpty())
        assertEquals("Keep this file", unrelated.readText())
    }

    private fun isolated(block: suspend (BlobsDatabase, PackageInstaller, File) -> Unit) =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val folder = File(context.cacheDir, "flow-${UUID.randomUUID()}").apply { mkdirs() }
            val name = "flow-${UUID.randomUUID()}.db"
            val isolated = object : ContextWrapper(context) {
                override fun getFilesDir() = folder
            }
            val db = BlobsDatabase.open(context, name)
            try {
                block(
                    db,
                    PackageInstaller(isolated, db.packages(), LibraryStore()),
                    File(folder, "package-downloads")
                )
            } finally {
                db.close()
                context.deleteDatabase(name)
                folder.deleteRecursively()
            }
        }

    private fun workController(
        database: BlobsDatabase,
        installer: PackageInstaller,
        downloader: PackageDownloader,
        availableDownloads: List<DownloadablePackage> = emptyList()
    ): WorkController {
        val library = LibraryStore()
        return WorkController(
            ConversationStore(database.conversations()),
            LocalResearchEngine(library),
            PackageTransferExecutor(installer, downloader),
            PackageTransferRecovery(
                installer,
                downloader,
                database.packages(),
                availableDownloads
            )
        )
    }
}
