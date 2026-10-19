package dev.blobs.app.data.packages

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
        require(downloadBytes in 1..40_000_000_000L)
        require(downloadSha256.matches(Regex("[a-f0-9]{64}")))
        require(
            if (format == DownloadFormat.BLOBS) {
                url.startsWith("https://github.com/nonomnonom/blobs/releases/download/") ||
                    url.startsWith("https://huggingface.co/") &&
                    url.contains(Regex("/resolve/[a-f0-9]{40}/"))
            } else {
                url.startsWith("https://huggingface.co/") &&
                    url.contains(Regex("/resolve/[a-f0-9]{40}/"))
            }
        )
        require(manifest.kind in setOf("model", "library"))
        require((manifest.kind == "model") == (role == PackageRole.MODEL))
        require(recommendedUpToMemoryMb > 0)
        require(description.length <= 2_000 && language.length <= 64 && publisher.length <= 160)
        require(compatibility == null || compatibility.minimumRamMb >= 0)
    }
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
