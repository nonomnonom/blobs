package dev.blobs.app.data.packages

import java.util.GregorianCalendar
import org.json.JSONObject

data class DownloadablePackage(
    val fileName: String,
    val url: String,
    val downloadBytes: Long,
    val downloadSha256: String,
    val format: DownloadFormat,
    val manifest: PackageManifest,
    val role: PackageRole,
    val recommendedUpToMemoryMb: Long,
    val description: String = "",
    val language: String = "",
    val publisher: String = "",
    val libraryProfile: LibraryProfile? = null,
    val compatibility: PackageCompatibility? = null
) {
    init {
        require(
            fileName.matches(
                if (format == DownloadFormat.BLOBS) {
                    Regex("[a-z0-9][a-z0-9.-]{0,100}\\.blobs")
                } else {
                    Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,120}\\.gguf")
                }
            )
        )
        require(manifest.kind in setOf("model", "library"))
        require(downloadBytes in 1..maximumPayloadBytes(manifest.kind))
        require(downloadSha256.matches(Regex("[a-f0-9]{64}")))
        require(
            url.isSafeCatalogText(8_000) && (
                if (format == DownloadFormat.BLOBS) {
                    url.startsWith("https://github.com/nonomnonom/blobs/releases/download/") ||
                        (
                            url.startsWith("https://huggingface.co/") &&
                                url.contains(Regex("/resolve/[a-f0-9]{40}/"))
                            )
                } else {
                    url.startsWith("https://huggingface.co/") &&
                        url.contains(Regex("/resolve/[a-f0-9]{40}/"))
                }
                )
        )
        require((manifest.kind == "model") == (role == PackageRole.MODEL))
        require(recommendedUpToMemoryMb > 0)
        require(
            description.isSafeCatalogText(2_000) && language.isSafeCatalogText(64) &&
                publisher.isSafeCatalogText(160)
        )
        require(compatibility == null || compatibility.minimumRamMb >= 0)
    }
}

data class LibraryProfile(
    val recordCount: Long,
    val scope: String,
    val sourceSnapshotDate: String,
    val updatePolicy: String,
    val limitations: String
) {
    init {
        require(recordCount in 1..100_000_000L)
        require(scope.isRequiredCatalogText(1_000))
        require(sourceSnapshotDate.isValidCatalogDate())
        require(updatePolicy.isRequiredCatalogText(2_000))
        require(limitations.isRequiredCatalogText(2_000))
    }

    fun storageJson(): String = JSONObject().apply {
        put("record_count", recordCount)
        put("scope", scope)
        put("source_snapshot_date", sourceSnapshotDate)
        put("update_policy", updatePolicy)
        put("limitations", limitations)
    }.toString()

    companion object {
        fun fromStorageJson(value: String): LibraryProfile {
            val json = JSONObject(value)
            return LibraryProfile(
                recordCount = json.getLong("record_count"),
                scope = json.getString("scope"),
                sourceSnapshotDate = json.getString("source_snapshot_date"),
                updatePolicy = json.getString("update_policy"),
                limitations = json.getString("limitations")
            )
        }
    }
}

internal fun String.isSafeCatalogText(limit: Int): Boolean {
    if (length > limit || '\u0000' in this || '\uFFFD' in this) return false
    var index = 0
    while (index < length) {
        when {
            Character.isHighSurrogate(this[index]) -> {
                if (index + 1 == length || !Character.isLowSurrogate(this[index + 1])) {
                    return false
                }
                index += 2
            }

            Character.isLowSurrogate(this[index]) -> return false

            else -> index++
        }
    }
    return true
}

internal fun String.isRequiredCatalogText(limit: Int): Boolean =
    isNotBlank() && isSafeCatalogText(limit)

private fun String.isValidCatalogDate(): Boolean {
    val match = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})").matchEntire(this) ?: return false
    val year = match.groupValues[1].toInt()
    if (year !in 1..9999) return false
    return runCatching {
        GregorianCalendar(year, match.groupValues[2].toInt() - 1, match.groupValues[3].toInt())
            .apply { isLenient = false }
            .timeInMillis
    }.isSuccess
}

data class PackageCompatibility(
    val runtime: String,
    val format: String,
    val minimumRamMb: Long,
    val corpusSchema: Int,
    val retrievalFormat: String,
    val minimumAppVersion: String
)

enum class DownloadFormat { BLOBS, GGUF }

enum class PackageRole { MODEL, STARTER, OPTIONAL }
