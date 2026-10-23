package dev.blobs.app.data.packages

import org.json.JSONObject

internal const val MAX_MODEL_PAYLOAD_BYTES = 40_000_000_000L
internal const val MAX_LIBRARY_PAYLOAD_BYTES = 6L * 1024L * 1024L * 1024L

internal fun maximumPayloadBytes(kind: String): Long = when (kind) {
    "model" -> MAX_MODEL_PAYLOAD_BYTES
    "library" -> MAX_LIBRARY_PAYLOAD_BYTES
    else -> 0L
}

data class PackageManifest(
    val id: String,
    val version: String,
    val kind: String,
    val title: String,
    val bytes: Long,
    val sha256: String,
    val license: String,
    val origin: String,
    val recordsSha256: String = ""
) {
    val fileName: String get() = if (kind == "model") "model.gguf" else "library.sqlite"
    val key: String get() = "$id-$version-${sha256.take(16)}"

    internal fun validate() {
        require(id.matches(Regex("[a-z0-9-]{1,64}")))
        require(version.matches(Regex("[a-zA-Z0-9.-]{1,40}")))
        require(kind in setOf("model", "library"))
        require(bytes in 1..maximumPayloadBytes(kind))
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(
            recordsSha256.isEmpty() ||
                (kind == "library" && recordsSha256.matches(Regex("[a-f0-9]{64}")))
        )
        require(
            validMetadataText(title, 160) &&
                validMetadataText(license, 4000) &&
                validMetadataText(origin, 2000)
        )
    }

    fun json(): String = JSONObject().apply {
        put("format", 1)
        put("id", id)
        put("version", version)
        put("kind", kind)
        put("title", title)
        put("bytes", bytes)
        put("sha256", sha256)
        put("license", license)
        put("origin", origin)
        if (recordsSha256.isNotBlank()) put("records_sha256", recordsSha256)
    }.toString()

    companion object {
        private fun validMetadataText(value: String, limit: Int): Boolean =
            value.length in 1..limit && value.any { !it.isWhitespace() } &&
                '\u0000' !in value && '\uFFFD' !in value && !value.hasUnpairedSurrogate()

        private fun String.hasUnpairedSurrogate(): Boolean {
            var index = 0
            while (index < length) {
                when {
                    Character.isHighSurrogate(this[index]) -> {
                        if (index + 1 == length || !Character.isLowSurrogate(this[index + 1])) {
                            return true
                        }
                        index += 2
                    }

                    Character.isLowSurrogate(this[index]) -> return true
                    else -> index++
                }
            }
            return false
        }

        fun parse(text: String): PackageManifest {
            val json = JSONObject(text)
            require(json.getInt("format") == 1)
            val manifest = PackageManifest(
                json.getString("id"),
                json.getString("version"),
                json.getString("kind"),
                json.getString("title"),
                json.getLong("bytes"),
                json.getString("sha256"),
                json.getString("license"),
                json.getString("origin"),
                if (json.has("records_sha256")) {
                    require(!json.isNull("records_sha256"))
                    json.getString("records_sha256")
                } else {
                    ""
                }
            )
            manifest.validate()
            return manifest
        }
    }
}
