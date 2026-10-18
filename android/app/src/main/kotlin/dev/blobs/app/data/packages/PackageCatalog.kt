package dev.blobs.app.data.packages

import android.content.Context
import org.json.JSONObject

class PackageCatalog(context: Context) {
    private val packages: List<DownloadablePackage> = context.assets.open(
        FILE_NAME
    ).bufferedReader().use {
        parse(it.readText())
    }

    val models: List<DownloadablePackage> = packages.filter { it.manifest.kind == "model" }
    val starterLibrary: DownloadablePackage = requireNotNull(
        packages.singleOrNull { it.role == PackageRole.STARTER }
    )
    val additionalLibraries: List<DownloadablePackage> =
        packages.filter { it.role == PackageRole.OPTIONAL }

    init {
        require(models.isNotEmpty())
        require(packages.map { it.manifest.key }.distinct().size == packages.size)
    }

    fun recommendedModel(totalMemoryBytes: Long, freeStorageBytes: Long): DownloadablePackage {
        val memoryMb = (totalMemoryBytes / (1024L * 1024L)).coerceAtLeast(1)
        val preferredIndex = models.indexOfFirst { it.recommendedUpToMemoryMb >= memoryMb }
            .let { if (it < 0) models.lastIndex else it }
        return models.subList(0, preferredIndex + 1).lastOrNull {
            packageFitsStorage(it, freeStorageBytes)
        }
            ?: models.first()
    }

    fun packageFitsStorage(asset: DownloadablePackage, freeStorageBytes: Long): Boolean =
        freeStorageBytes >= asset.downloadBytes + asset.manifest.bytes + 64L * 1024 * 1024

    private fun parse(text: String): List<DownloadablePackage> {
        val catalog = JSONObject(text)
        require(catalog.getInt("format") == 1)
        val tag = catalog.getString("release_tag")
        require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
        val entries = catalog.getJSONArray("packages")
        return buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val asset = DownloadablePackage(
                    fileName = item.getString("file_name"),
                    url = item.getString("url"),
                    downloadBytes = item.getLong("download_bytes"),
                    downloadSha256 = item.getString("download_sha256"),
                    format = DownloadFormat.valueOf(item.getString("download_format").uppercase()),
                    manifest = PackageManifest.parse(item.getJSONObject("manifest").toString()),
                    role = PackageRole.valueOf(item.getString("role").uppercase()),
                    recommendedUpToMemoryMb = item.optLong(
                        "recommended_up_to_memory_mb",
                        Long.MAX_VALUE
                    )
                )
                require(
                    if (asset.format == DownloadFormat.BLOBS) {
                        asset.url == "$RELEASE_ROOT/$tag/${asset.fileName}"
                    } else {
                        asset.url.startsWith("https://huggingface.co/") &&
                            asset.url.contains(Regex("/resolve/[a-f0-9]{40}/"))
                    }
                )
                add(asset)
            }
        }
    }

    private companion object {
        const val FILE_NAME = "package-catalog.json"
        const val RELEASE_ROOT = "https://github.com/nonomnonom/blobs/releases/download"
    }
}

data class DownloadablePackage(
    val fileName: String,
    val url: String,
    val downloadBytes: Long,
    val downloadSha256: String,
    val format: DownloadFormat,
    val manifest: PackageManifest,
    val role: PackageRole,
    val recommendedUpToMemoryMb: Long
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
                url.startsWith("https://github.com/nonomnonom/blobs/releases/download/")
            } else {
                url.startsWith("https://huggingface.co/") &&
                    url.contains(Regex("/resolve/[a-f0-9]{40}/"))
            }
        )
        require(manifest.kind in setOf("model", "library"))
        require((manifest.kind == "model") == (role == PackageRole.MODEL))
        require(recommendedUpToMemoryMb > 0)
    }
}

enum class DownloadFormat { BLOBS, GGUF }

enum class PackageRole { MODEL, STARTER, OPTIONAL }
