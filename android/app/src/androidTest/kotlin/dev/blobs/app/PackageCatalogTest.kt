package dev.blobs.app.data.packages

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageCatalogTest {
    @Test
    fun bundledCatalogOffersThePinnedOutdoorSafetyLibraryOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = PackageCatalog(context)
        val library = catalog.additionalLibraries.single {
            it.manifest.id == "nps-outdoor-safety"
        }

        assertEquals("Outdoor safety · U.S. national parks", library.manifest.title)
        assertEquals("2026-10.1", library.manifest.version)
        assertEquals(PackageRole.OPTIONAL, library.role)
        assertEquals(DownloadFormat.BLOBS, library.format)
        assertEquals(36_588L, library.downloadBytes)
        assertEquals(
            "U.S. Government work · National Park Service (public domain unless marked)",
            library.manifest.license
        )
    }

    @Test
    fun acceptsCatalogSignedByTrustedKey() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val packages = PackageCatalog.verifyAndParse(
            signedEnvelope(catalogPayload(), pair),
            pair.public
        )

        assertEquals(2, packages.size)
        assertEquals(
            "Research model",
            packages.single {
                it.manifest.kind == "model"
            }.manifest.title
        )
        assertEquals(PackageRole.STARTER, packages.single { it.role == PackageRole.STARTER }.role)
    }

    @Test
    fun acceptsCatalogSignedByEitherKeyDuringRotation() {
        val oldKey = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val newKey = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val envelope = signedEnvelope(catalogPayload(), newKey)

        val packages = PackageCatalog.verifyAndParse(
            envelope,
            listOf(oldKey.public, newKey.public)
        )

        assertEquals("Research model", packages.first().manifest.title)
    }

    @Test
    fun rejectsCatalogChangedAfterSigning() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val envelope = signedEnvelope(catalogPayload(), pair)
        val originalPayload = Base64.getEncoder().encodeToString(
            catalogPayload().toByteArray(StandardCharsets.UTF_8)
        )
        val changedPayload = Base64.getEncoder().encodeToString(
            catalogPayload().replace("Research model", "Replacement model")
                .toByteArray(StandardCharsets.UTF_8)
        )
        val changed = envelope.replace(originalPayload, changedPayload)

        assertThrows(IllegalArgumentException::class.java) {
            PackageCatalog.verifyAndParse(changed, pair.public)
        }
    }

    @Test
    fun rejectsCatalogSignedByDifferentKey() {
        val trusted = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val attacker = KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair()
        val envelope = signedEnvelope(catalogPayload(), attacker)

        assertThrows(IllegalArgumentException::class.java) {
            PackageCatalog.verifyAndParse(envelope, trusted.public)
        }
    }

    @Test
    fun refreshesRemoteCatalogAndRestoresVerifiedCacheOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheDirectory = File(context.cacheDir, "catalog-test-${UUID.randomUUID()}")
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val envelope = signedEnvelope(catalogPayload(), keyPair)
        val endpoint = URL("https://huggingface.co/catalog.signed.json")
        try {
            val online = PackageCatalog(
                fallbackCatalog = catalogPayload(),
                cacheDirectory = cacheDirectory,
                publicKey = keyPair.public,
                endpoint = endpoint,
                openConnection = {
                    Response(
                        it,
                        200,
                        envelope.toByteArray(),
                        mapOf(
                            "ETag" to "\"v1\""
                        )
                    )
                }
            )
            assertEquals(CatalogRefreshOutcome.UPDATED, online.refreshNow())
            assertEquals(CatalogSource.REMOTE, online.state.value.source)
            assertEquals("Research model", online.models.single().manifest.title)

            val offline = PackageCatalog(
                fallbackCatalog = catalogPayload(),
                cacheDirectory = cacheDirectory,
                publicKey = keyPair.public,
                endpoint = endpoint,
                openConnection = {
                    throw AssertionError("A fresh catalog cache should be used offline")
                }
            )
            assertEquals(CatalogSource.CACHE, offline.state.value.source)
            assertEquals("Research model", offline.models.single().manifest.title)
        } finally {
            cacheDirectory.deleteRecursively()
        }
    }

    private fun signedEnvelope(payload: String, keyPair: java.security.KeyPair): String {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(bytes)
        return """{"format":1,"payload":"${Base64.getEncoder().encodeToString(
            bytes
        )}","signature":"${Base64.getEncoder().encodeToString(signer.sign())}"}"""
    }

    private fun catalogPayload() = """
        {
          "format": 1,
          "release_tag": "v0.3.1",
          "packages": [
            {
              "file_name": "research.gguf",
              "url": "https://huggingface.co/blobs/model/resolve/${"a".repeat(40)}/research.gguf",
              "download_format": "gguf",
              "download_bytes": 100,
              "download_sha256": "${"1".repeat(64)}",
              "manifest": {"format":1,"id":"research-model","version":"1","kind":"model","title":"Research model","bytes":100,"sha256":"${"2".repeat(
        64
    )}","license":"Apache-2.0","origin":"https://huggingface.co/blobs/model"},
              "role": "model",
              "recommended_up_to_memory_mb": 4096,
              "description": "An offline research model.",
              "language": "English",
              "publisher": "Blobs",
              "compatibility": {"runtime":"llama.cpp","format":"GGUF","minimum_ram_mb":2048,"minimum_app_version":"0.3.1"}
            },
            {
              "file_name": "starter.blobs",
              "url": "https://huggingface.co/datasets/blobs/starter/resolve/${"b".repeat(
        40
    )}/starter.blobs",
              "download_format": "blobs",
              "download_bytes": 200,
              "download_sha256": "${"3".repeat(64)}",
              "manifest": {"format":1,"id":"starter-library","version":"1","kind":"library","title":"Starter library","bytes":300,"sha256":"${"4".repeat(
        64
    )}","license":"CC BY-SA 4.0","origin":"https://huggingface.co/datasets/blobs/starter"},
              "role": "starter",
              "description": "A small reference library.",
              "language": "English",
              "publisher": "Blobs",
              "compatibility": {"format":"Blobs library archive","corpus_schema":3,"retrieval_format":"SQLite FTS5","minimum_app_version":"0.3.1"}
            }
          ]
        }
    """.trimIndent()

    private class Response(
        url: URL,
        private val status: Int,
        private val body: ByteArray,
        private val headers: Map<String, String>
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun getHeaderField(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }
}
