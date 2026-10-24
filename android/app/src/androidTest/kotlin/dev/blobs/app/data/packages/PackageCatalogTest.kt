package dev.blobs.app.data.packages

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.BuildConfig
import dev.blobs.app.data.local.PackageEntity
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageCatalogTest {
    @Test
    fun newerAppOnlyLibrariesAreNotOfferedToThisVersion() {
        val payload = JSONObject(catalogPayload())
        val packages = payload.getJSONArray("packages")
        val future = JSONObject(packages.getJSONObject(1).toString())
        future.put("file_name", "future.blobs")
        future.put("url", future.getString("url").replace("starter.blobs", "future.blobs"))
        future.put("role", "optional")
        future.getJSONObject("manifest").put("id", "future-library")
        future.getJSONObject("compatibility").put("minimum_app_version", "999.0.0")
        packages.put(future)
        assertEquals(2, CatalogCodec.parseCatalog(payload.toString()).size)
    }

    @Test
    fun acceptsTheCurrentAppVersionAndRejectsMalformedVersionRequirements() {
        val payload = JSONObject(catalogPayload())
        val compatibility = payload.getJSONArray("packages").getJSONObject(0)
            .getJSONObject("compatibility")
        compatibility.put("minimum_app_version", BuildConfig.VERSION_NAME)
        assertEquals(2, CatalogCodec.parseCatalog(payload.toString()).size)
        compatibility.put("minimum_app_version", "unknown")
        assertThrows(IllegalArgumentException::class.java) {
            CatalogCodec.parseCatalog(payload.toString())
        }
    }

    @Test
    fun modelRecommendationRespectsMemoryEvenWhenTheListIsReordered() {
        val payload = JSONObject(catalogPayload())
        val packages = payload.getJSONArray("packages")
        val larger = JSONObject(packages.getJSONObject(0).toString())
        larger.put("file_name", "larger.gguf")
        larger.put("url", larger.getString("url").replace("research.gguf", "larger.gguf"))
        larger.put("recommended_up_to_memory_mb", 8192)
        larger.getJSONObject("manifest").put("id", "larger-model")
        larger.getJSONObject("compatibility").put("minimum_ram_mb", 8192)
        payload.put(
            "packages",
            JSONArray().put(larger).put(packages.getJSONObject(0)).put(packages.getJSONObject(1))
        )
        val cache =
            File(
                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                UUID.randomUUID().toString()
            )
        val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        try {
            val catalog = PackageCatalog(
                payload.toString(),
                cache,
                key.public,
                URL("https://huggingface.co/catalog.json"),
                { throw AssertionError("Recommendation must stay offline") }
            )
            assertEquals(
                "research-model",
                catalog.recommendedModel(6L * 1024 * 1024 * 1024, Long.MAX_VALUE).manifest.id
            )
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun bundledCatalogOffersThePinnedOutdoorSafetyLibraryOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = PackageCatalog(context)
        val library = catalog.additionalLibraries.single {
            it.manifest.id == "nps-outdoor-safety"
        }

        assertEquals("Outdoor safety · U.S. national parks", library.manifest.title)
        assertEquals("2026-10.2", library.manifest.version)
        assertEquals(PackageRole.OPTIONAL, library.role)
        assertEquals(DownloadFormat.BLOBS, library.format)
        assertEquals(39_256L, library.downloadBytes)
        assertEquals(
            "U.S. Government work · National Park Service (public domain unless marked)",
            library.manifest.license
        )
    }

    @Test
    fun bundledCatalogOffersThePinnedEmergencyPreparednessLibraryOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = PackageCatalog(context)
        val library = catalog.additionalLibraries.single {
            it.manifest.id == "ready-emergency-preparedness"
        }

        assertEquals("Emergency preparedness · U.S. households", library.manifest.title)
        assertEquals("2026-10.1", library.manifest.version)
        assertEquals(PackageRole.OPTIONAL, library.role)
        assertEquals(DownloadFormat.BLOBS, library.format)
        assertEquals(25_054L, library.downloadBytes)
        assertEquals(
            "FEMA/Ready.gov text; mostly reusable subject to page-specific third-party restrictions",
            library.manifest.license
        )
    }

    @Test
    fun acceptsCatalogSignedByTrustedKey() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val packages = CatalogCodec.verifyAndParse(
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

        val packages = CatalogCodec.verifyAndParse(
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
            CatalogCodec.verifyAndParse(changed, pair.public)
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
            CatalogCodec.verifyAndParse(envelope, trusted.public)
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

    @Test
    fun starterUpdateKeepsInstalledEditionReadyOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = File(context.cacheDir, "catalog-update-${UUID.randomUUID()}")
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val updated = catalogPayload().replace(
            "\"revision\": 1",
            "\"revision\": 2"
        ).replace("\"version\":\"1\"", "\"version\":\"2\"")
        try {
            val catalog = PackageCatalog(
                catalogPayload(),
                cache,
                pair.public,
                URL("https://huggingface.co/catalog.json"),
                { Response(it, 200, signedEnvelope(updated, pair).toByteArray(), emptyMap()) }
            )
            val manifest = catalog.starterLibrary.manifest
            val installed = PackageEntity(
                manifest.key, manifest.id, manifest.version, "library", manifest.title,
                "installed.sqlite", manifest.bytes, manifest.sha256, manifest.license,
                manifest.origin, 1L
            )
            assertEquals(true, catalog.hasStarterLibrary(listOf(installed)))
            assertEquals(CatalogRefreshOutcome.UPDATED, catalog.refreshNow())
            assertEquals("2", catalog.starterLibrary.manifest.version)
            assertEquals(true, catalog.hasStarterLibrary(listOf(installed)))
            assertEquals(false, catalog.hasStarterLibrary(emptyList()))
            assertEquals(false, catalog.hasStarterLibrary(listOf(installed.copy(kind = "model"))))
            val offline = PackageCatalog(
                catalogPayload(),
                cache,
                pair.public,
                URL("https://huggingface.co/catalog.json"),
                { throw AssertionError("Offline readiness must not use the network") }
            )
            assertEquals(true, offline.hasStarterLibrary(listOf(installed)))
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun rejectsRollbackAndRevisionReuseAfterRestartAndCacheLoss() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = File(context.cacheDir, "catalog-rollback-${UUID.randomUUID()}")
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val latest = catalogPayload().replace("\"revision\": 1", "\"revision\": 2")
            .replace("Research model", "Updated model")
        var response = signedEnvelope(latest, pair)
        fun open() = PackageCatalog(
            catalogPayload(),
            cache,
            pair.public,
            URL("https://huggingface.co/catalog.json"),
            { Response(it, 200, response.toByteArray(), emptyMap()) }
        )
        try {
            val first = open()
            assertEquals(CatalogRefreshOutcome.UPDATED, first.refreshNow())
            response = signedEnvelope(catalogPayload(), pair)
            val restarted = open()
            assertEquals("Updated model", restarted.models.single().manifest.title)
            assertEquals(CatalogRefreshOutcome.UNAVAILABLE, restarted.refreshNow())
            assertEquals("Updated model", restarted.models.single().manifest.title)
            response = signedEnvelope(latest.replace("Updated model", "Reused revision"), pair)
            assertEquals(CatalogRefreshOutcome.UNAVAILABLE, restarted.refreshNow())
            File(cache, "catalog.signed.json").delete()
            response = signedEnvelope(catalogPayload(), pair)
            val missingCache = open()
            assertEquals(CatalogSource.BUNDLED, missingCache.state.value.source)
            assertEquals(CatalogRefreshOutcome.UNAVAILABLE, missingCache.refreshNow())
            response = signedEnvelope(latest, pair)
            assertEquals(CatalogRefreshOutcome.UPDATED, missingCache.refreshNow())
            File(cache, "accepted-revision.json").writeText("damaged")
            val damagedState = open()
            assertEquals(CatalogSource.BUNDLED, damagedState.state.value.source)
            assertEquals(CatalogRefreshOutcome.UNAVAILABLE, damagedState.refreshNow())
            assertEquals(1, damagedState.models.size)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun retiredKeyCannotRefreshOrRestoreCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = File(context.cacheDir, "catalog-retired-${UUID.randomUUID()}")
        val old = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val current = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        cache.mkdirs()
        val envelope = signedEnvelope(catalogPayload(), old)
        File(cache, "catalog.signed.json").writeText(envelope)
        try {
            val catalog = PackageCatalog(
                catalogPayload(),
                cache,
                current.public,
                URL("https://huggingface.co/catalog.json"),
                { Response(it, 200, envelope.toByteArray(), emptyMap()) }
            )
            assertEquals(CatalogSource.BUNDLED, catalog.state.value.source)
            assertEquals(CatalogRefreshOutcome.UNAVAILABLE, catalog.refreshNow())
        } finally {
            cache.deleteRecursively()
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
          "revision": 1,
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
              "compatibility": {"format":"Blobs library archive","corpus_schema":3,"retrieval_format":"SQLite FTS5","minimum_app_version":"0.3.1"},
              "library_profile": {"record_count": 10, "scope": "A small reference library for tests.", "source_snapshot_date": "2026-10-01", "update_policy": "New editions are published as new immutable entries.", "limitations": "Test fixture data only."}
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
