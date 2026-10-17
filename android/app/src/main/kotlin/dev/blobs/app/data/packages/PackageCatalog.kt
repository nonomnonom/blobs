package dev.blobs.app.data.packages

import android.content.Context
import org.json.JSONObject

class PackageCatalog(context: Context) {
    private val packages: List<DownloadablePackage> = context.assets.open(
        FILE_NAME
    ).bufferedReader().use {
        parse(it.readText())
    }

    val recommendedModel: DownloadablePackage = requireNotNull(
        packages.singleOrNull {
            it.manifest.kind == "model"
        }
    )
    val starterLibrary: DownloadablePackage = requireNotNull(
        packages.singleOrNull {
            it.manifest.kind == "library"
        }
    )

    private fun parse(text: String): List<DownloadablePackage> {
        val catalog = JSONObject(text)
        require(catalog.getInt("format") == 1)
        val tag = catalog.getString("release_tag")
        require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
        val entries = catalog.getJSONArray("packages")
        require(entries.length() == 2)
        return buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val asset = DownloadablePackage(
                    fileName = item.getString("file_name"),
                    url = item.getString("url"),
                    archiveBytes = item.getLong("archive_bytes"),
                    archiveSha256 = item.getString("archive_sha256"),
                    manifest = PackageManifest.parse(item.getJSONObject("manifest").toString())
                )
                require(asset.url == "$RELEASE_ROOT/$tag/${asset.fileName}")
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
    val archiveBytes: Long,
    val archiveSha256: String,
    val manifest: PackageManifest
) {
    init {
        require(fileName.matches(Regex("[a-z0-9][a-z0-9.-]{0,100}\\.blobs")))
        require(url.startsWith("https://github.com/nonomnonom/blobs/releases/download/"))
        require(archiveBytes in 1..40_000_000_000L)
        require(archiveSha256.matches(Regex("[a-f0-9]{64}")))
        require(manifest.kind in setOf("model", "library"))
    }
}
