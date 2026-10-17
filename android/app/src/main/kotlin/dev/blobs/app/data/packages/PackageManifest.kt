package dev.blobs.app.data.packages

import org.json.JSONObject

data class PackageManifest(
    val id: String,
    val version: String,
    val kind: String,
    val title: String,
    val bytes: Long,
    val sha256: String,
    val license: String,
    val origin: String
) {
    val fileName: String get() = if (kind == "model") "model.gguf" else "library.sqlite"
    val key: String get() = "$id-$version-${sha256.take(16)}"

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
    }.toString()

    companion object {
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
                json.getString("origin")
            )
            require(manifest.id.matches(Regex("[a-z0-9-]{1,64}")))
            require(manifest.version.matches(Regex("[a-zA-Z0-9.-]{1,40}")))
            require(manifest.kind in setOf("model", "library"))
            require(manifest.bytes in 1..40_000_000_000L)
            require(manifest.sha256.matches(Regex("[a-f0-9]{64}")))
            require(
                manifest.title.length in 1..160 &&
                    manifest.license.length in 1..4000 &&
                    manifest.origin.length in 1..2000
            )
            return manifest
        }
    }
}
