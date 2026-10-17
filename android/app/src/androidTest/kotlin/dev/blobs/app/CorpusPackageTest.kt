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
