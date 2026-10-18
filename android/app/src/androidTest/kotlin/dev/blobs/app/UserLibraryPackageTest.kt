package dev.blobs.app

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.library.UserLibraryPackage
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.packages.PackageInstaller
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserLibraryPackageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun localMarkdownBecomesSearchableAndExportsAsPortableJson() = runBlocking {
        val name = "user-library-${UUID.randomUUID()}.db"
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        val source = File(folder, "mountain-notes.md").apply {
            writeText(
                """
                # Mountain travel

                Carry enough drinking water for the route. In winter, check local weather and pack warm layers.
                """.trimIndent()
            )
        }
        val exported = File(folder, "collection.json")
        val database = BlobsDatabase.open(context, name)
        try {
            val library = LibraryStore()
            val installer = PackageInstaller(isolated, database.packages(), library)
            val userPackages = UserLibraryPackage(context)
            val archive = userPackages.prepare(listOf(Uri.fromFile(source)))
            val archiveUri = Uri.fromFile(archive)
            val installed = installer.install(archiveUri, installer.inspect(archiveUri)) {}

            assertEquals("mountain-notes", installed.title)
            database.packages().setEnabled(installed.key, false)
            val disabledEvidence = library.retrieve(
                database.packages().all(),
                "drinking water mountain route",
                null
            )
            assertTrue(disabledEvidence.isEmpty())
            database.packages().setEnabled(installed.key, true)
            val evidence = library.retrieve(
                database.packages().all(),
                "drinking water mountain route",
                null
            )
            assertTrue(evidence.isNotEmpty())
            assertTrue(evidence.first().text.contains("drinking water"))
            assertEquals("Your files", evidence.first().publisher)

            installer.exportCollection(installed, exported.outputStream())
            val reimported = userPackages.prepare(listOf(Uri.fromFile(exported)))
            val restoredUri = Uri.fromFile(reimported)
            val restored = installer.install(restoredUri, installer.inspect(restoredUri)) {}
            assertEquals(
                evidence.first().text,
                library.retrieve(database.packages().all(), "drinking water", null).first().text
            )
        } finally {
            database.close()
            context.deleteDatabase(name)
            folder.deleteRecursively()
        }
    }

    @Test
    fun textPdfBecomesSearchableOffline() = runBlocking {
        val name = "user-pdf-${UUID.randomUUID()}.db"
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        val pdf = File(folder, "trail-guide.pdf")
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Trail water safety")
                stream.endText()
            }
            document.save(pdf)
        }
        val database = BlobsDatabase.open(context, name)
        try {
            val installer = PackageInstaller(
                isolated,
                database.packages(),
                LibraryStore()
            )
            val pack = UserLibraryPackage(context).prepare(listOf(Uri.fromFile(pdf)))
            val uri = Uri.fromFile(pack)
            installer.install(uri, installer.inspect(uri)) {}
            val evidence = LibraryStore().retrieve(database.packages().all(), "water safety", null)
            assertTrue(evidence.isNotEmpty())
            assertTrue(evidence.first().text.contains("Trail water safety"))
        } finally {
            database.close()
            context.deleteDatabase(name)
            folder.deleteRecursively()
        }
    }
}
