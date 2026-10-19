package dev.blobs.app

import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.packages.PackageInstaller
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpusPackageTest {
    @Test
    fun pythonBuiltCompressedLibraryResumesAndKeepsReadableEvidence() =
        verifyLibrary("corpus-fixture.blobs")

    @Test
    fun pythonBuiltZip64LibraryResumesAndKeepsReadableEvidence() =
        verifyLibrary("corpus-zip64-fixture.blobs")

    @Test
    fun libraryUpdateActivatesOneEditionAndFailedUpdatePreservesIt() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "editions-${UUID.randomUUID()}.db"
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        val db = BlobsDatabase.open(context, name)
        try {
            val source = File(folder, "original.blobs")
            instrumentation.context.assets.open("corpus-fixture.blobs").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            val installer = PackageInstaller(isolated, db.packages(), LibraryStore())
            val original = installer.inspect(Uri.fromFile(source))
            val old = installer.install(Uri.fromFile(source), original) {}
            val next = original.copy(version = "2")
            val update = File(folder, "update.blobs")
            ZipOutputStream(update.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(next.json().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(next.fileName))
                ZipFile(source).use { archive ->
                    archive.getInputStream(archive.getEntry(original.fileName)).use {
                        it.copyTo(zip)
                    }
                }
                zip.closeEntry()
            }
            try {
                installer.install(Uri.fromFile(update), next) { error("interrupted") }
                throw AssertionError("Expected interrupted install")
            } catch (expected: IllegalStateException) {
                assertEquals("interrupted", expected.message)
            }
            assertEquals(listOf(old), db.packages().all())
            assertTrue(File(old.path).exists())
            val newer = installer.install(Uri.fromFile(update), next) {}
            assertEquals(
                listOf(newer.key),
                db.packages().all().filter {
                    it.enabled
                }.map { it.key }
            )
            assertTrue(File(old.path).exists())
            db.packages().setEnabled(old.key, true)
            assertEquals(listOf(old.key), db.packages().all().filter { it.enabled }.map { it.key })
            db.packages().setEnabled(old.key, false)
            assertTrue(db.packages().all().none { it.enabled })
            // Simulate rows left by an earlier app version before startup recovery.
            db.packages().upsert(old.copy(enabled = true, installedAt = 1))
            db.packages().upsert(newer.copy(enabled = true, installedAt = 2))
            installer.recover()
            assertEquals(
                listOf(newer.key),
                db.packages().all().filter {
                    it.enabled
                }.map { it.key }
            )
        } finally {
            db.close()
            context.deleteDatabase(name)
            folder.deleteRecursively()
        }
    }

    @Test
    fun rejectedArchivesCloseBeforePayloadCopy() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "archive-close-${UUID.randomUUID()}.db"
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        val db = BlobsDatabase.open(context, name)
        try {
            val source = File(folder, "fixture.blobs")
            instrumentation.context.assets.open("corpus-fixture.blobs").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            val installer = PackageInstaller(isolated, db.packages(), LibraryStore())
            val expected = installer.inspect(Uri.fromFile(source))
            val rejected = File(folder, "rejected.blobs")
            val manifests =
                listOf("not-json", expected.copy(version = "changed").json(), expected.json())
            for (manifest in manifests) {
                ZipOutputStream(rejected.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(manifest.toByteArray())
                    zip.closeEntry()
                }
                try {
                    installer.install(Uri.fromFile(rejected), expected) {}
                    throw AssertionError("Invalid archive was accepted")
                } catch (_: Exception) {
                    val openSources = File("/proc/self/fd").listFiles().orEmpty().count {
                        runCatching { android.system.Os.readlink(it.path) }.getOrNull() ==
                            rejected.path
                    }
                    assertEquals("Rejected archive must not retain its descriptor", 0, openSources)
                    assertTrue(db.packages().all().isEmpty())
                    assertEquals(null, installer.pending())
                }
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
            folder.deleteRecursively()
        }
    }

    private fun verifyLibrary(asset: String) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "corpus-${UUID.randomUUID()}.db"
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        val db = BlobsDatabase.open(context, name)
        try {
            val source = File(folder, "fixture.blobs")
            instrumentation.context.assets.open(asset).use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            ZipFile(source).use {
                assertEquals(ZipEntry.DEFLATED, it.getEntry("library.sqlite").method)
            }
            val library = LibraryStore()
            val installer = PackageInstaller(isolated, db.packages(), library)
            val uri = Uri.fromFile(source)
            val manifest = installer.inspect(uri)
            try {
                installer.install(uri, manifest) { throw CancellationException("pause") }
            } catch (_: CancellationException) {
                assertTrue(db.packages().all().isEmpty())
            }
            val recreated = PackageInstaller(isolated, db.packages(), library)
            assertEquals(manifest, recreated.recover()?.manifest)
            val installed = recreated.install(uri, manifest) {}
            val results = library.retrieve(listOf(installed), "evaporation", null)
            assertTrue(results.single().text.contains("does not prove lower total energy"))
            assertTrue(results.single().text.length > 1000)
            assertTrue(library.retrieve(listOf(installed), "thermal notebook", null).isNotEmpty())
            val document = library.readDocument(listOf(installed), results.single().id)!!
            assertTrue(document.text.contains("Temperature (°C)"))
            assertTrue(document.text.contains("Sample B | 30"))
            assertEquals("Blobs test authors", document.publisher)
            assertEquals(null, recreated.pending())
        } finally {
            db.close()
            context.deleteDatabase(name)
            folder.deleteRecursively()
        }
    }
}
