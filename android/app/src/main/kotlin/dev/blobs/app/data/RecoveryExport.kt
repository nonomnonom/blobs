package dev.blobs.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecoveryExport {
    suspend fun write(context: Context, destination: Uri) = withContext(Dispatchers.IO) {
        val files = buildList {
            for (name in listOf("blobs.db", "blobs.db-wal", "blobs.db-shm")) {
                val file = context.getDatabasePath(name)
                if (file.isFile) add("databases/$name" to file)
            }
            File(context.filesDir, "datastore").listFiles()?.filter { it.isFile }?.forEach {
                add("preferences/${it.name}" to it)
            }
        }
        ZipOutputStream(
            requireNotNull(context.contentResolver.openOutputStream(destination))
        ).use { zip ->
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(
                (
                    "Blobs recovery copy\nThis contains private conversations and preferences. " +
                        "It preserves raw files for repair; " +
                        "it is not a transactional or restorable backup. " +
                        "Model and library payloads are not included.\n"
                    ).toByteArray()
            )
            zip.closeEntry()
            for ((name, file) in files) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip, 256 * 1024) }
                zip.closeEntry()
            }
        }
    }
}
