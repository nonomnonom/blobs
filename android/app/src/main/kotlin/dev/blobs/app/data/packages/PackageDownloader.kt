package dev.blobs.app.data.packages

import android.content.Context
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Resumable, pinned downloads for the small first-run package catalog. */
class PackageDownloader(
    private val root: File,
    private val availableBytes: () -> Long,
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    constructor(context: Context) : this(
        File(context.filesDir, "package-downloads"),
        { StatFs(File(context.filesDir, "package-downloads").path).availableBytes }
    )

    init {
        root.mkdirs()
    }

    suspend fun download(asset: DownloadablePackage, progress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = File(root, asset.fileName)
            val previous = File(root, "${asset.fileName}.previous")
            if (!target.exists() && previous.exists()) previous.renameTo(target)
            if (target.exists() &&
                target.length() == asset.downloadBytes &&
                sha256(target) == asset.downloadSha256
            ) {
                previous.delete()
                progress(asset.downloadBytes, asset.downloadBytes)
                return@withContext target
            }

            val partial = File(root, "${asset.fileName}.part")
            val resume = File(root, "${asset.fileName}.resume")
            if (!partial.exists() || partial.length() > asset.downloadBytes || !resume.isFile) {
                partial.delete()
                resume.delete()
            }
            val resumeTag = resume.takeIf {
                it.isFile
            }?.readText(Charsets.UTF_8)?.takeIf(String::isNotBlank)
            var offset = partial.takeIf { it.isFile }?.length() ?: 0L
            if (resumeTag == null) offset = 0L
            if (offset == asset.downloadBytes) {
                if (sha256(partial) == asset.downloadSha256) {
                    activate(partial, target, previous)
                    resume.delete()
                    progress(asset.downloadBytes, asset.downloadBytes)
                    return@withContext target
                }
                partial.delete()
                resume.delete()
                offset = 0L
            }

            val available = availableBytes()
            val required = asset.downloadBytes - offset + asset.manifest.bytes + 64L * 1024 * 1024
            if (available < required) throw IOException("STORAGE")

            var currentUrl = URL(asset.url)
            var connection: HttpURLConnection? = null
            try {
                var redirects = 0
                var response: Int
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val opened = openConnection(currentUrl)
                    connection = opened
                    opened.instanceFollowRedirects = false
                    opened.connectTimeout = 20_000
                    opened.readTimeout = 60_000
                    opened.setRequestProperty("Accept-Encoding", "identity")
                    if (offset > 0) {
                        opened.setRequestProperty("Range", "bytes=$offset-")
                        opened.setRequestProperty("If-Range", resumeTag)
                    }
                    response = opened.responseCode
                    if (response !in 300..399) break
                    val location =
                        opened.getHeaderField("Location") ?: throw IOException("DOWNLOAD_REDIRECT")
                    opened.disconnect()
                    if (++redirects > 5) throw IOException("DOWNLOAD_REDIRECT")
                    val next = URL(currentUrl, location)
                    require(next.protocol == "https" && next.host in ALLOWED_HOSTS) {
                        "DOWNLOAD_HOST"
                    }
                    currentUrl = next
                    connection = null
                }

                val active = connection
                if (response == HttpURLConnection.HTTP_OK && offset > 0) {
                    offset = 0
                    partial.delete()
                    resume.delete()
                } else if (response == HttpURLConnection.HTTP_PARTIAL) {
                    val range =
                        active.getHeaderField("Content-Range")
                            ?: throw IOException("DOWNLOAD_RANGE")
                    if (!range.startsWith("bytes $offset-") ||
                        range.substringAfterLast('/').toLongOrNull() != asset.downloadBytes ||
                        resumeTag == null
                    ) {
                        throw IOException("DOWNLOAD_RANGE")
                    }
                } else if (response != HttpURLConnection.HTTP_OK) {
                    throw IOException("DOWNLOAD_HTTP_$response")
                }

                val tag = active.getHeaderField("ETag")?.takeIf(String::isNotBlank)
                if (offset > 0 &&
                    tag != null &&
                    tag != resumeTag
                ) {
                    throw IOException("DOWNLOAD_CHANGED")
                }
                if (tag != null) resume.writeText(tag, Charsets.UTF_8) else resume.delete()
                val remaining = asset.downloadBytes - offset
                val contentLength = active.getHeaderFieldLong("Content-Length", -1L)
                if (contentLength >= 0 &&
                    contentLength != remaining
                ) {
                    throw IOException("DOWNLOAD_SIZE")
                }

                active.inputStream.use { input ->
                    FileOutputStream(partial, offset > 0).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var count = offset
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            if (count > asset.downloadBytes) throw IOException("DOWNLOAD_SIZE")
                            output.write(buffer, 0, read)
                            progress(count, asset.downloadBytes)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
                if (partial.length() !=
                    asset.downloadBytes
                ) {
                    throw IOException("DOWNLOAD_INCOMPLETE")
                }
                if (sha256(partial) != asset.downloadSha256) {
                    partial.delete()
                    resume.delete()
                    throw IOException("DOWNLOAD_CHECKSUM")
                }
                activate(partial, target, previous)
                resume.delete()
                progress(asset.downloadBytes, asset.downloadBytes)
                target
            } finally {
                connection?.disconnect()
            }
        }

    fun discard(asset: DownloadablePackage) {
        val directory = root.canonicalFile
        listOf("", ".part", ".resume", ".previous").forEach { suffix ->
            val file = File(directory, asset.fileName + suffix).canonicalFile
            require(file.parentFile == directory) { "DOWNLOAD_PATH" }
            if (file.exists() && !file.delete()) throw IOException("DOWNLOAD_DELETE")
        }
    }

    fun removeInstalledArchive(uri: Uri) {
        if (uri.scheme != "file") return
        val source = File(requireNotNull(uri.path)).canonicalFile
        if (source.parentFile == root.canonicalFile && source.extension in setOf("blobs", "gguf")) {
            source.delete()
        }
    }

    private fun activate(partial: File, target: File, previous: File) {
        if (previous.exists() && !previous.delete()) throw IOException("DOWNLOAD_ACTIVATE")
        val hadTarget = target.exists()
        if (hadTarget && !target.renameTo(previous)) throw IOException("DOWNLOAD_ACTIVATE")
        if (!partial.renameTo(target)) {
            if (hadTarget) previous.renameTo(target)
            throw IOException("DOWNLOAD_ACTIVATE")
        }
        previous.delete()
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val ALLOWED_HOSTS = setOf(
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com",
            "github-releases.githubusercontent.com",
            "huggingface.co",
            "cdn-lfs.huggingface.co",
            "cdn-lfs-us-1.hf.co",
            "us.aws.cdn.hf.co",
            "cas-bridge.xethub.hf.co"
        )
    }
}
