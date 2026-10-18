package dev.blobs.app.data.packages

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.util.AtomicFile
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.PackageDao
import dev.blobs.app.data.local.PackageEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PendingImport(val uri: Uri, val manifest: PackageManifest)

class PackageInstaller(
    private val context: Context,
    private val dao: PackageDao,
    private val library: LibraryStore
) {
    private val mutations = Mutex()
    private val root = File(context.filesDir, "packages").apply { mkdirs() }
    private val downloadRoot = File(context.filesDir, "package-downloads").canonicalFile
    private val journal = AtomicFile(File(context.filesDir, "pending-import.json"))

    fun pending(): PendingImport? {
        if (!journal.baseFile.exists()) return null
        val json = JSONObject(journal.openRead().bufferedReader().use { it.readText() })
        return PendingImport(
            Uri.parse(json.getString("uri")),
            PackageManifest.parse(json.getString("manifest"))
        )
    }

    private fun record(uri: Uri, manifest: PackageManifest) {
        val output = journal.startWrite()
        try {
            output.write(
                JSONObject().put(
                    "uri",
                    uri.toString()
                ).put("manifest", manifest.json()).toString().toByteArray()
            )
            journal.finishWrite(output)
        } catch (error: Exception) {
            journal.failWrite(output)
            throw error
        }
    }

    suspend fun discardPending() = withContext(Dispatchers.IO) {
        mutations.withLock {
            pending()?.let { File(root, "${it.manifest.key}.partial").delete() }
            journal.delete()
        }
    }

    suspend fun inspect(uri: Uri): PackageManifest = withContext(Dispatchers.IO) {
        stream(uri).use { readManifest(it) }
    }

    suspend fun install(
        uri: Uri,
        expected: PackageManifest,
        progress: (Long) -> Unit
    ): PackageEntity = withContext(Dispatchers.IO) {
        mutations.withLock {
            dao.all().find { it.key == expected.key }?.let { return@withContext it }
            val rawModel =
                uri.scheme == "file" && File(requireNotNull(uri.path)).extension == "gguf"
            if (rawModel) {
                val source = File(requireNotNull(uri.path)).canonicalFile
                require(source.parentFile == downloadRoot && expected.kind == "model") {
                    "MODEL_SOURCE"
                }
            }
            val storage = context.getSystemService(StorageManager::class.java)
            val volume = storage.getUuidForPath(root)
            check(dao.all().sumOf { it.bytes } + expected.bytes < 48_000_000_000L) { "FOOTPRINT" }
            val staging = File(root, "${expected.key}.partial")
            val target = File(root, expected.key)
            var activated = false
            var paused = false
            val previous = pending()
            require(previous == null || previous.manifest.key == expected.key) { "PENDING_IMPORT" }
            val resumeBytes =
                staging.takeIf { it.exists() }?.length()?.coerceAtMost(expected.bytes) ?: 0L
            val required = expected.bytes - resumeBytes + 64L * 1024 * 1024
            check(storage.getAllocatableBytes(volume) > required) { "STORAGE" }
            storage.allocateBytes(volume, required)
            record(uri, expected)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                val archive = if (rawModel) null else stream(uri)
                val input = if (rawModel) {
                    File(requireNotNull(uri.path)).inputStream()
                } else {
                    require(readManifest(requireNotNull(archive)) == expected) {
                        "MANIFEST_CHANGED"
                    }
                    requireNotNull(archive).also {
                        val entry = it.nextEntry ?: error("MISSING_PAYLOAD")
                        require(entry.name == expected.fileName && !entry.isDirectory)
                    }
                }
                input.use { source ->
                    java.io.FileOutputStream(staging, resumeBytes > 0).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val length = source.read(buffer)
                            if (length < 0) break
                            count += length
                            require(count <= expected.bytes) { "SIZE" }
                            val skip = (resumeBytes - (count - length)).coerceIn(
                                0,
                                length.toLong()
                            ).toInt()
                            output.write(buffer, skip, length - skip)
                            digest.update(buffer, 0, length)
                            progress(count)
                        }
                        output.fd.sync()
                    }
                    if (!rawModel) require(archive?.nextEntry == null) { "EXTRA_FILES" }
                }
                require(
                    count == expected.bytes &&
                        digest.digest().joinToString("") {
                            "%02x".format(it)
                        } == expected.sha256
                ) { "CHECKSUM" }
                if (resumeBytes > 0) {
                    val hash = MessageDigest.getInstance("SHA-256")
                    staging.inputStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            hash.update(buffer, 0, count)
                        }
                    }
                    require(hash.digest().joinToString("") { "%02x".format(it) } == expected.sha256)
                }
                if (expected.kind == "library") {
                    library.validate(staging)
                } else {
                    GgufValidator.validate(staging)
                }
                currentCoroutineContext().ensureActive()
                val installed = PackageEntity(
                    expected.key, expected.id, expected.version, expected.kind, expected.title,
                    target.path,
                    expected.bytes, expected.sha256, expected.license, expected.origin,
                    System.currentTimeMillis()
                )
                withContext(NonCancellable) {
                    if (target.exists()) check(target.delete())
                    check(staging.renameTo(target)) { "ACTIVATE" }
                    dao.save(installed)
                    activated = true
                    journal.delete()
                }
                installed
            } catch (error: CancellationException) {
                paused = !activated
                throw error
            } finally {
                if (!paused) {
                    staging.delete()
                    journal.delete()
                }
                if (!activated) target.delete()
            }
        }
    }

    suspend fun remove(pack: PackageEntity) = withContext(Dispatchers.IO + NonCancellable) {
        mutations.withLock {
            if (pack.kind ==
                "model"
            ) {
                check(dao.all().count { it.kind == "model" } > 1) { "LAST_MODEL" }
            }
            val source = File(pack.path)
            check(source.parentFile?.canonicalFile == root.canonicalFile)
            val trash = File(root, "${pack.key}.removed")
            check(!source.exists() || source.renameTo(trash))
            try {
                dao.delete(pack.key)
            } catch (error: Exception) {
                trash.renameTo(source)
                throw error
            }
            trash.delete()
        }
    }

    suspend fun recover() = withContext(Dispatchers.IO) {
        mutations.withLock {
            val installed = dao.all().map { it.key }.toSet()
            var pending = try {
                pending()
            } catch (error: Exception) {
                val damaged =
                    File(context.filesDir, "pending-import-${System.currentTimeMillis()}.damaged")
                journal.baseFile.renameTo(damaged)
                throw error
            }
            if (pending?.manifest?.key in installed) {
                journal.delete()
                pending = null
            }
            root.listFiles()?.forEach { file ->
                when {
                    file.name.endsWith(".removed") &&
                        file.name.removeSuffix(".removed") in installed -> {
                        file.renameTo(File(root, file.name.removeSuffix(".removed")))
                    }
                    file.name !in installed &&
                        file.name != "${pending?.manifest?.key}.partial" -> file.delete()
                }
            }
            pending
        }
    }

    private fun stream(uri: Uri) = ZipInputStream(
        context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read file")
    )

    private fun readManifest(archive: ZipInputStream): PackageManifest {
        val entry = archive.nextEntry ?: error("EMPTY_FILE")
        require(entry.name == "manifest.json" && !entry.isDirectory)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = archive.read(buffer)
            if (read < 0) break
            require(output.size() + read <= 65_536)
            output.write(buffer, 0, read)
        }
        val bytes = output.toByteArray()
        return PackageManifest.parse(bytes.toString(Charsets.UTF_8))
    }
}
