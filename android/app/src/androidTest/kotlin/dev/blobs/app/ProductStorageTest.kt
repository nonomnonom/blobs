package dev.blobs.app

import android.content.ContextWrapper
import android.net.Uri
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.data.local.TurnEntity
import dev.blobs.app.data.packages.PackageInstaller
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun schemaOneDraftSurvivesMigrationAndOldWorkBecomesInterrupted() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        BundledSQLiteDriver().open(file.path).use { db ->
            listOf(
                "CREATE TABLE conversations(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,draft TEXT NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL)",
                "INSERT INTO conversations VALUES ('a','Original','Keep my draft',1,2)",
                "PRAGMA user_version=1"
            ).forEach { sql -> db.prepare(sql).use { it.step() } }
        }
        val db = BlobsDatabase.open(context, name)
        try {
            val dao = db.conversations()
            assertEquals("Keep my draft", dao.find("a")?.draft)
            dao.saveTurn(
                TurnEntity("turn", "a", "Why?", "Partial", "RUNNING", "model", "*", "[]", 3)
            )
            dao.recoverInterrupted()
            assertEquals("INTERRUPTED", dao.turns("a").single().status)
            assertEquals(0, dao.updateAnswer("turn", "late result", "[]", "COMPLETE", 10, ""))
            assertEquals("Partial", dao.turns("a").single().answer)
            assertEquals("Keep my draft", dao.find("a")?.draft)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun turnOwnershipDraftAndEvidencePersistIndependently() = runBlocking {
        val name = "ownership-${UUID.randomUUID()}.db"
        val db = BlobsDatabase.open(context, name)
        try {
            val dao = db.conversations()
            dao.save(ConversationEntity("a", "", "Question", 1, 1))
            dao.save(ConversationEntity("b", "Second", "Other draft", 2, 2))
            dao.beginTurn(
                TurnEntity(
                    "request", "a", "Question", "", "RUNNING", "model-v1",
                    "source-v1", "[]", 3
                )
            )
            dao.saveDraft("a", "Next question")
            dao.updateAnswer(
                "request",
                "Saved [1]",
                "[{\"title\":\"Original\"}]",
                "STOPPED",
                100,
                ""
            )
            assertEquals(0, dao.updateAnswer("request", "Late output", "[]", "COMPLETE", 101, ""))
            assertEquals("Next question", dao.find("a")?.draft)
            assertEquals("Other draft", dao.find("b")?.draft)
            assertEquals("[{\"title\":\"Original\"}]", dao.turns("a").single().evidence)
            dao.delete("b")
            assertEquals("Saved [1]", dao.turns("a").single().answer)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun corruptedAndExtraPayloadCannotReplaceInstalledPackage() = runBlocking {
        val name = "installer-${UUID.randomUUID()}.db"
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        val db = BlobsDatabase.open(context, name)
        try {
            val installer = PackageInstaller(isolated, db.packages(), LibraryStore())
            val bytes = structuralGgufFixture()
            val good = writePackage(folder, bytes, bytes)
            val manifest = installer.inspect(Uri.fromFile(good))
            val installed = installer.install(Uri.fromFile(good), manifest) {}
            assertEquals(bytes.size.toLong(), File(installed.path).length())
            assertEquals(installed, installer.install(Uri.fromFile(good), manifest) {})
            val bad = writePackage(folder, bytes, bytes + 1.toByte(), version = "2")
            val badManifest = installer.inspect(Uri.fromFile(bad))
            var rejected = false
            try {
                installer.install(Uri.fromFile(bad), badManifest) {}
            } catch (
                _: Exception
            ) {
                rejected =
                    true
            }
            assertTrue(rejected)
            assertEquals(listOf(installed), db.packages().all())
            assertTrue(File(installed.path).readBytes().contentEquals(bytes))
            assertFalse(File(folder, "packages/${badManifest.key}.partial").exists())
            val extra = writePackage(folder, bytes, bytes, version = "3", extra = true)
            rejected = false
            try {
                installer.install(Uri.fromFile(extra), installer.inspect(Uri.fromFile(extra))) {}
            } catch (
                _: Exception
            ) {
                rejected =
                    true
            }
            assertTrue(rejected)
            assertTrue(File(installed.path).exists())
            File(folder, "pending-import.json").writeText("{broken")
            assertTrue(runCatching { installer.recover() }.isFailure)
            assertEquals(null, installer.pending())
            assertEquals(listOf(installed), db.packages().all())
            assertTrue(File(installed.path).readBytes().contentEquals(bytes))
        } finally {
            db.close()
            context.deleteDatabase(name)
            folder.deleteRecursively()
        }
    }

    private fun structuralGgufFixture(): ByteArray {
        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        fun int(value: Int) = output.writeInt(Integer.reverseBytes(value))
        fun long(value: Long) = output.writeLong(java.lang.Long.reverseBytes(value))
        fun text(value: String) {
            val encoded = value.toByteArray()
            long(encoded.size.toLong())
            output.write(encoded)
        }
        output.write("GGUF".toByteArray())
        int(3)
        long(1)
        long(2)
        text("general.architecture")
        int(8)
        text("qwen3")
        text("tokenizer.chat_template")
        int(8)
        text("chatml")
        text("test.weight")
        int(1)
        long(32)
        int(0)
        long(0)
        while (bytes.size() % 32 != 0) output.writeByte(0)
        output.write(ByteArray(128))
        return bytes.toByteArray()
    }

    @Test
    fun interruptedImportResumesAfterInstallerRecreation() = runBlocking {
        val name = "resume-${UUID.randomUUID()}.db"
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        val db = BlobsDatabase.open(context, name)
        try {
            val bytes = structuralGgufFixture()
            val file = writePackage(folder, bytes, bytes)
            val installer = PackageInstaller(isolated, db.packages(), LibraryStore())
            val uri = Uri.fromFile(file)
            val manifest = installer.inspect(uri)
            try {
                installer.install(uri, manifest) { throw CancellationException("pause") }
            } catch (
                _: CancellationException
            ) { }
            assertTrue(db.packages().all().isEmpty())
            assertTrue(File(folder, "packages/${manifest.key}.partial").exists())
            val recreated = PackageInstaller(isolated, db.packages(), LibraryStore())
            assertEquals(manifest, recreated.recover()?.manifest)
            val installed = recreated.install(uri, manifest) {}
            assertTrue(File(installed.path).readBytes().contentEquals(bytes))
            assertEquals(null, recreated.pending())
            assertFalse(File(folder, "packages/${manifest.key}.partial").exists())
        } finally {
            db.close()
            context.deleteDatabase(name)
            folder.deleteRecursively()
        }
    }

    private fun writePackage(
        folder: File,
        expected: ByteArray,
        actual: ByteArray,
        version: String = "1",
        extra: Boolean = false
    ): File {
        val file = File(folder, "package-$version.blobs")
        val manifest = JSONObject().apply {
            put("format", 1)
            put("id", "test-model")
            put("version", version)
            put("kind", "model")
            put("title", "Test model")
            put("bytes", expected.size)
            put(
                "sha256",
                MessageDigest.getInstance("SHA-256").digest(expected).joinToString("") {
                    "%02x".format(it)
                }
            )
            put("license", "Test fixture")
            put("origin", "instrumentation")
        }
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("model.gguf"))
            zip.write(actual)
            zip.closeEntry()
            if (extra) {
                zip.putNextEntry(ZipEntry("../escape"))
                zip.write(0)
                zip.closeEntry()
            }
        }
        return file
    }
}
