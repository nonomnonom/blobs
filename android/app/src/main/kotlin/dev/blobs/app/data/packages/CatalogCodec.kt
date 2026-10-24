package dev.blobs.app.data.packages

import dev.blobs.app.BuildConfig
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

internal object CatalogCodec {
    const val MAX_CATALOG_BYTES = 1_048_576L
    private const val RELEASE_ROOT = "https://github.com/nonomnonom/blobs/releases/download"

    fun parseCatalog(text: String): List<DownloadablePackage> {
        val catalog = JSONObject(text)
        require(catalog.getInt("format") == 1)
        CatalogRevision.requireNumber(catalog)
        val tag = catalog.getString("release_tag")
        require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
        val entries = catalog.getJSONArray("packages")
        val packages = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val compatibility = requireNotNull(item.optJSONObject("compatibility")) {
                    "CATALOG_COMPATIBILITY"
                }
                if (!supportsMinimumVersion(
                        compatibility.optString("minimum_app_version", "0.0.0")
                    )
                ) {
                    continue
                }
                val asset = DownloadablePackage(
                    fileName = item.getString("file_name"),
                    url = item.getString("url"),
                    downloadBytes = item.getLong("download_bytes"),
                    downloadSha256 = item.getString("download_sha256"),
                    format = DownloadFormat.valueOf(
                        item.getString("download_format").uppercase()
                    ),
                    manifest = PackageManifest.parse(item.getJSONObject("manifest").toString()),
                    role = PackageRole.valueOf(item.getString("role").uppercase()),
                    recommendedUpToMemoryMb = item.optLong(
                        "recommended_up_to_memory_mb",
                        Long.MAX_VALUE
                    ),
                    description = item.optString("description"),
                    language = item.optString("language"),
                    publisher = item.optString("publisher"),
                    libraryProfile = item.parseLibraryProfile(),
                    compatibility = item.optJSONObject("compatibility")?.let { compatibility ->
                        PackageCompatibility(
                            runtime = compatibility.optString("runtime"),
                            format = compatibility.optString("format"),
                            minimumRamMb = compatibility.optLong("minimum_ram_mb", 0),
                            corpusSchema = compatibility.optInt("corpus_schema", 0),
                            retrievalFormat = compatibility.optString("retrieval_format"),
                            minimumAppVersion = compatibility.optString(
                                "minimum_app_version",
                                "0.0.0"
                            )
                        )
                    }
                )
                val pinnedHuggingFaceRevision =
                    asset.url.startsWith("https://huggingface.co/") &&
                        asset.url.contains(Regex("/resolve/[a-f0-9]{40}/"))
                val pinnedRelease = asset.url == "$RELEASE_ROOT/$tag/${asset.fileName}"
                require(pinnedHuggingFaceRevision || pinnedRelease) { "CATALOG_URL" }
                require(asset.description.isNotBlank()) { "CATALOG_DESCRIPTION" }
                require(asset.language.isNotBlank()) { "CATALOG_LANGUAGE" }
                require(asset.publisher.isNotBlank()) { "CATALOG_PUBLISHER" }
                requireNotNull(asset.compatibility) { "CATALOG_COMPATIBILITY" }
                if (asset.manifest.kind == "model") {
                    require(asset.libraryProfile == null) { "CATALOG_MODEL_LIBRARY_PROFILE" }
                    require(
                        asset.compatibility.runtime == "llama.cpp" &&
                            asset.compatibility.format == "GGUF" &&
                            asset.compatibility.minimumRamMb > 0
                    ) { "CATALOG_MODEL_COMPATIBILITY" }
                } else {
                    requireNotNull(asset.libraryProfile) { "CATALOG_LIBRARY_PROFILE" }
                    require(
                        asset.compatibility.format == "Blobs library archive" &&
                            asset.compatibility.corpusSchema == 3 &&
                            asset.compatibility.retrievalFormat == "SQLite FTS5"
                    ) { "CATALOG_LIBRARY_COMPATIBILITY" }
                }
                add(asset)
            }
        }
        require(packages.any { it.manifest.kind == "model" })
        require(packages.count { it.role == PackageRole.STARTER } == 1)
        require(packages.map { it.manifest.key }.distinct().size == packages.size)
        require(packages.map { it.fileName }.distinct().size == packages.size) {
            "CATALOG_FILE_NAME"
        }
        return packages
    }

    private fun JSONObject.parseLibraryProfile(): LibraryProfile? {
        if (!has("library_profile")) return null
        require(!isNull("library_profile")) { "CATALOG_LIBRARY_PROFILE" }
        val profile = getJSONObject("library_profile")
        val rawRecordCount = profile.get("record_count")
        require(rawRecordCount is Int || rawRecordCount is Long) { "CATALOG_LIBRARY_RECORD_COUNT" }
        return LibraryProfile(
            recordCount = (rawRecordCount as Number).toLong(),
            scope = profile.getString("scope"),
            sourceSnapshotDate = profile.getString("source_snapshot_date"),
            updatePolicy = profile.getString("update_policy"),
            limitations = profile.getString("limitations")
        )
    }

    private fun supportsMinimumVersion(version: String): Boolean {
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "CATALOG_APP_VERSION" }
        val required = version.split('.').map {
            requireNotNull(it.toIntOrNull()) { "CATALOG_APP_VERSION" }
        }
        val current = BuildConfig.VERSION_NAME.split('.').map(String::toInt)
        val difference = required.zip(current).firstOrNull { (minimum, installed) ->
            minimum != installed
        }
        return difference == null || difference.first < difference.second
    }

    fun verifyAndParse(envelopeText: String, publicKey: PublicKey): List<DownloadablePackage> =
        verifyAndParse(envelopeText, listOf(publicKey))

    fun verifyAndParse(
        envelopeText: String,
        publicKeys: List<PublicKey>
    ): List<DownloadablePackage> = parseCatalog(verifiedPayload(envelopeText, publicKeys))

    fun verifiedPayload(envelopeText: String, publicKeys: List<PublicKey>): String {
        require(envelopeText.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES)
        val envelope = JSONObject(envelopeText)
        require(envelope.getInt("format") == 1) { "CATALOG_FORMAT" }
        val payload = Base64.getDecoder().decode(envelope.getString("payload"))
        val signatureBytes = Base64.getDecoder().decode(envelope.getString("signature"))
        val verified = publicKeys.any { key ->
            runCatching {
                Signature.getInstance("SHA256withECDSA").apply {
                    initVerify(key)
                    update(payload)
                }.verify(signatureBytes)
            }.getOrDefault(false)
        }
        require(verified) { "CATALOG_SIGNATURE" }
        return payload.toString(Charsets.UTF_8)
    }

    fun loadPublicKey(pem: String): PublicKey {
        val encoded = pem.replace(
            Regex("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s"),
            ""
        )
        return KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
    }
}

internal data class CatalogRevision(val number: Long, val digest: String) {
    fun accepts(previous: CatalogRevision): Boolean = number > previous.number ||
        (number == previous.number && digest == previous.digest)

    fun json(): String = JSONObject().put("revision", number).put("sha256", digest).toString()

    companion object {
        fun fromPayload(payload: String): CatalogRevision = CatalogRevision(
            requireNumber(JSONObject(payload)),
            MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        )

        fun fromRecord(record: String): CatalogRevision {
            val json = JSONObject(record)
            val digest = json.getString("sha256")
            require(digest.matches(Regex("[a-f0-9]{64}")))
            return CatalogRevision(requireNumber(json), digest)
        }

        fun requireNumber(json: JSONObject): Long {
            val raw = json.get("revision")
            require(raw is Int || raw is Long) { "CATALOG_REVISION" }
            return (raw as Number).toLong().also {
                require(it in 1..9_007_199_254_740_991L) { "CATALOG_REVISION" }
            }
        }
    }
}
