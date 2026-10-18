package dev.blobs.app.data.packages

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PackageDownloaderTest {
    @Test
    fun verifiesAndActivatesTheExactCatalogArchive() = runBlocking {
        val root = Files.createTempDirectory("blobs-download-test").toFile()
        try {
            val bytes = "package bytes".toByteArray()
            val asset = asset(bytes)
            val downloader = PackageDownloader(root, { Long.MAX_VALUE }) { url ->
                Response(
                    url,
                    HttpURLConnection.HTTP_OK,
                    { ByteArrayInputStream(bytes) },
                    mapOf(
                        "ETag" to "\"pin\""
                    )
                )
            }

            val file = downloader.download(asset) { _, _ -> }

            assertEquals(asset.fileName, file.name)
            assertArrayEquals(bytes, file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun interruptedTransferResumesOnlyAgainstTheSameEntityTag() = runBlocking {
        val root = Files.createTempDirectory("blobs-resume-test").toFile()
        try {
            val bytes = "a verified package".toByteArray()
            val asset = asset(bytes)
            var requestRange: String? = null
            var calls = 0
            val downloader = PackageDownloader(root, { Long.MAX_VALUE }) { url ->
                calls += 1
                if (calls == 1) {
                    Response(
                        url,
                        HttpURLConnection.HTTP_OK,
                        {
                            object : InputStream() {
                                private val source = ByteArrayInputStream(bytes)
                                private var delivered = 0

                                override fun read(): Int = source.read()

                                override fun read(
                                    buffer: ByteArray,
                                    offset: Int,
                                    length: Int
                                ): Int {
                                    if (delivered >= 4) throw IOException("connection dropped")
                                    val read = source.read(
                                        buffer,
                                        offset,
                                        minOf(
                                            length,
                                            4 - delivered
                                        )
                                    )
                                    delivered += read.coerceAtLeast(0)
                                    return read
                                }
                            }
                        },
                        mapOf("ETag" to "\"same-asset\"", "Content-Length" to bytes.size.toString())
                    )
                } else {
                    requestRange = "bytes=4-"
                    Response(
                        url,
                        HttpURLConnection.HTTP_PARTIAL,
                        { ByteArrayInputStream(bytes.copyOfRange(4, bytes.size)) },
                        mapOf(
                            "ETag" to "\"same-asset\"",
                            "Content-Range" to "bytes 4-${bytes.lastIndex}/${bytes.size}",
                            "Content-Length" to (bytes.size - 4).toString()
                        )
                    )
                }
            }

            assertThrows(IOException::class.java) {
                runBlocking { downloader.download(asset) { _, _ -> } }
            }
            val completed = downloader.download(asset) { _, _ -> }

            assertEquals("bytes=4-", requestRange)
            assertArrayEquals(bytes, completed.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsRedirectsOutsideThePinnedReleaseHosts() = runBlocking {
        val root = Files.createTempDirectory("blobs-redirect-test").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            val asset = asset(bytes)
            val downloader = PackageDownloader(root, { Long.MAX_VALUE }) { url ->
                Response(
                    url,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    { ByteArrayInputStream(byteArrayOf()) },
                    mapOf("Location" to "https://example.com/package.blobs")
                )
            }

            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { downloader.download(asset) { _, _ -> } }
            }

            assertEquals("DOWNLOAD_HOST", error.message)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifiesRawGgufAfterPinnedHuggingFaceRedirect() = runBlocking {
        val root = Files.createTempDirectory("blobs-gguf-download-test").toFile()
        try {
            val bytes = "GGUF model bytes".toByteArray()
            val model = DownloadablePackage(
                fileName = "gemma-test.gguf",
                url = "https://huggingface.co/google/test/resolve/${"a".repeat(
                    40
                )}/gemma-test.gguf",
                downloadBytes = bytes.size.toLong(),
                downloadSha256 = sha256(bytes),
                format = DownloadFormat.GGUF,
                manifest = PackageManifest(
                    id = "gemma-test",
                    version = "a1234567",
                    kind = "model",
                    title = "Gemma test",
                    bytes = bytes.size.toLong(),
                    sha256 = sha256(bytes),
                    license = "Test",
                    origin = "https://huggingface.co/google/test"
                ),
                role = PackageRole.MODEL,
                recommendedUpToMemoryMb = Long.MAX_VALUE
            )
            val downloader = PackageDownloader(root, { Long.MAX_VALUE }) { url ->
                if (url.host == "huggingface.co") {
                    Response(
                        url,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        { ByteArrayInputStream(byteArrayOf()) },
                        mapOf("Location" to "https://us.aws.cdn.hf.co/gemma-test.gguf")
                    )
                } else {
                    Response(
                        url,
                        HttpURLConnection.HTTP_OK,
                        { ByteArrayInputStream(bytes) },
                        mapOf("Content-Length" to bytes.size.toString())
                    )
                }
            }

            val file = downloader.download(model) { _, _ -> }

            assertEquals("gemma-test.gguf", file.name)
            assertArrayEquals(bytes, file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun activatesACompleteVerifiedPartialWithoutStartingAnotherRequest() = runBlocking {
        val root = Files.createTempDirectory("blobs-complete-partial-test").toFile()
        try {
            val bytes = "completed before process exit".toByteArray()
            val asset = asset(bytes)
            File(root, "${asset.fileName}.part").writeBytes(bytes)
            File(root, "${asset.fileName}.resume").writeText("\"same-asset\"")
            val downloader = PackageDownloader(root, { Long.MAX_VALUE }) {
                throw AssertionError("A complete staged package should not start a network request")
            }

            val completed = downloader.download(asset) { _, _ -> }

            assertArrayEquals(bytes, completed.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun asset(bytes: ByteArray) = DownloadablePackage(
        fileName = "test-package.blobs",
        url = "https://github.com/nonomnonom/blobs/releases/download/v0.3.0/test-package.blobs",
        downloadBytes = bytes.size.toLong(),
        downloadSha256 = sha256(bytes),
        format = DownloadFormat.BLOBS,
        manifest = PackageManifest(
            id = "test-package",
            version = "1",
            kind = "model",
            title = "Test package",
            bytes = 1,
            sha256 = "0".repeat(64),
            license = "Test",
            origin = "https://example.com"
        ),
        role = PackageRole.MODEL,
        recommendedUpToMemoryMb = Long.MAX_VALUE
    )

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class Response(
        url: URL,
        private val status: Int,
        private val body: () -> InputStream,
        private val headers: Map<String, String>
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream(): InputStream = body()
        override fun getHeaderField(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }
}
