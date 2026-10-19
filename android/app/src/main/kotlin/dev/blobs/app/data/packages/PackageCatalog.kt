package dev.blobs.app.data.packages

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.AtomicFile
import android.util.Log
import dev.blobs.app.data.local.PackageEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CatalogSnapshot(
    val packages: List<DownloadablePackage>,
    val lastCheckedAt: Long?,
    val lastUpdatedAt: Long?,
    val refreshing: Boolean,
    val source: CatalogSource
)

enum class CatalogSource { BUNDLED, CACHE, REMOTE }

enum class CatalogRefreshOutcome { UPDATED, UNCHANGED, UNAVAILABLE }

class PackageCatalog internal constructor(
    fallbackCatalog: String,
    private val cacheDirectory: File,
    private val publicKey: PublicKey,
    private val endpoint: URL,
    private val openConnection: (URL) -> HttpURLConnection,
    private val hasInternet: () -> Boolean = { true },
    private val additionalPublicKeys: List<PublicKey> = emptyList()
) {
    constructor(context: Context) : this(
        fallbackCatalog = context.assets.open(FILE_NAME).bufferedReader().use { it.readText() },
        cacheDirectory = File(context.filesDir, "asset-catalog"),
        publicKey = loadPublicKey(
            context.assets.open(PUBLIC_KEY_FILE).bufferedReader().use { it.readText() }
        ),
        endpoint = URL(CATALOG_URL),
        openConnection = { it.openConnection() as HttpURLConnection },
        hasInternet = { context.hasValidatedInternet() },
        additionalPublicKeys = listOf(
            loadPublicKey(
                context.assets.open(ROTATED_PUBLIC_KEY_FILE).bufferedReader().use { it.readText() }
            )
        )
    )

    private val bundledStarterId = parseCatalog(fallbackCatalog)
        .single { it.role == PackageRole.STARTER }.manifest.id

    private val trustedPublicKeys = listOf(publicKey) + additionalPublicKeys

    private val cacheFile = AtomicFile(File(cacheDirectory, CACHED_CATALOG_FILE))
    private val etagFile = File(cacheDirectory, ETAG_FILE)
    private val checkedFile = File(cacheDirectory, CHECKED_FILE)
    private val updates = Mutex()
    private val mutableSnapshot = MutableStateFlow(initialSnapshot(fallbackCatalog))
    val state = mutableSnapshot.asStateFlow()

    val models: List<DownloadablePackage>
        get() = state.value.packages.filter { it.manifest.kind == "model" }
    val starterLibrary: DownloadablePackage
        get() = requireNotNull(state.value.packages.singleOrNull { it.role == PackageRole.STARTER })
    val additionalLibraries: List<DownloadablePackage>
        get() = state.value.packages.filter { it.role == PackageRole.OPTIONAL }

    fun hasStarterLibrary(installed: List<PackageEntity>): Boolean = installed.any {
        it.kind == "library" &&
            (it.packageId == bundledStarterId || it.packageId == starterLibrary.manifest.id)
    }

    fun recommendedModel(totalMemoryBytes: Long, freeStorageBytes: Long): DownloadablePackage {
        val availableModels = models
        val memoryMb = (totalMemoryBytes / (1024L * 1024L)).coerceAtLeast(1)
        val preferredIndex = availableModels.indexOfFirst { it.recommendedUpToMemoryMb >= memoryMb }
            .let { if (it < 0) availableModels.lastIndex else it }
        return availableModels.subList(0, preferredIndex + 1).lastOrNull {
            packageFitsStorage(it, freeStorageBytes)
        }
            ?: availableModels.first()
    }

    fun packageFitsStorage(asset: DownloadablePackage, freeStorageBytes: Long): Boolean =
        freeStorageBytes >= requiredStorageBytes(asset)

    fun requiredStorageBytes(asset: DownloadablePackage): Long =
        asset.downloadBytes + asset.manifest.bytes + 64L * 1024 * 1024

    suspend fun refreshNow(): CatalogRefreshOutcome = refresh()

    private suspend fun refresh(): CatalogRefreshOutcome = withContext(Dispatchers.IO) {
        updates.withLock {
            val now = System.currentTimeMillis()
            mutableSnapshot.value = mutableSnapshot.value.copy(refreshing = true)
            if (!hasInternet()) {
                mutableSnapshot.value = mutableSnapshot.value.copy(refreshing = false)
                return@withLock CatalogRefreshOutcome.UNAVAILABLE
            }
            cacheDirectory.mkdirs()
            runCatching { writeAtomically(checkedFile, now.toString().toByteArray()) }
            val result = runCatching { fetchCatalog() }
            val outcome = result.getOrElse {
                Log.w(TAG, "Unable to refresh the download list", it)
                CatalogRefreshOutcome.UNAVAILABLE
            }
            mutableSnapshot.value = mutableSnapshot.value.copy(
                lastCheckedAt = now,
                refreshing = false
            )
            outcome
        }
    }

    private fun fetchCatalog(): CatalogRefreshOutcome {
        var current = endpoint
        var connection: HttpURLConnection? = null
        var redirects = 0
        try {
            while (true) {
                val opened = openConnection(current)
                connection = opened
                opened.instanceFollowRedirects = false
                opened.connectTimeout = CONNECT_TIMEOUT_MS
                opened.readTimeout = READ_TIMEOUT_MS
                opened.setRequestProperty("Accept", "application/json")
                opened.setRequestProperty("Accept-Encoding", "identity")
                if (etagFile.isFile && mutableSnapshot.value.source != CatalogSource.BUNDLED) {
                    etagFile.readText(Charsets.UTF_8).takeIf(String::isNotBlank)?.let {
                        opened.setRequestProperty("If-None-Match", it)
                    }
                }
                val response = opened.responseCode
                if (response == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return CatalogRefreshOutcome.UNCHANGED
                }
                if (response in 300..399) {
                    val location = opened.getHeaderField("Location")
                        ?: throw IOException("CATALOG_REDIRECT")
                    opened.disconnect()
                    if (++redirects > MAX_REDIRECTS) throw IOException("CATALOG_REDIRECT")
                    val next = URL(current, location)
                    require(next.protocol == "https" && next.host in ALLOWED_HOSTS) {
                        "CATALOG_HOST"
                    }
                    current = next
                    connection = null
                    continue
                }
                if (response !=
                    HttpURLConnection.HTTP_OK
                ) {
                    throw IOException("CATALOG_HTTP_$response")
                }
                val length = opened.getHeaderFieldLong("Content-Length", -1L)
                if (length > MAX_CATALOG_BYTES) throw IOException("CATALOG_SIZE")
                val envelope = opened.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (output.size() + read >
                            MAX_CATALOG_BYTES
                        ) {
                            throw IOException("CATALOG_SIZE")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                val parsed = verifyAndParse(envelope.toString(Charsets.UTF_8), trustedPublicKeys)
                writeAtomically(cacheFile.baseFile, envelope)
                opened.getHeaderField("ETag")?.takeIf(String::isNotBlank)?.let {
                    writeAtomically(etagFile, it.toByteArray(Charsets.UTF_8))
                }
                mutableSnapshot.value = CatalogSnapshot(
                    packages = parsed,
                    lastCheckedAt = System.currentTimeMillis(),
                    lastUpdatedAt = System.currentTimeMillis(),
                    refreshing = false,
                    source = CatalogSource.REMOTE
                )
                return CatalogRefreshOutcome.UPDATED
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun initialSnapshot(fallbackCatalog: String): CatalogSnapshot {
        val fallback = parseCatalog(fallbackCatalog)
        val cached = runCatching {
            verifyAndParse(cacheFile.baseFile.readText(Charsets.UTF_8), trustedPublicKeys)
        }.getOrNull()
        val lastChecked = checkedFile.takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)?.toLongOrNull()
        return if (cached == null) {
            CatalogSnapshot(fallback, lastChecked, null, false, CatalogSource.BUNDLED)
        } else {
            CatalogSnapshot(
                cached,
                lastChecked,
                cacheFile.baseFile.lastModified(),
                false,
                CatalogSource.CACHE
            )
        }
    }

    companion object {
        const val FILE_NAME = "package-catalog.json"
        const val PUBLIC_KEY_FILE = "catalog-public-key.pem"
        private const val ROTATED_PUBLIC_KEY_FILE = "catalog-public-key-v2.pem"
        const val CATALOG_URL =
            "https://huggingface.co/datasets/nonomnonom/blobs-catalog/resolve/main/catalog.signed.json"
        private const val TAG = "PackageCatalog"
        private const val CACHED_CATALOG_FILE = "catalog.signed.json"
        private const val ETAG_FILE = "catalog.etag"
        private const val CHECKED_FILE = "last-check.txt"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_CATALOG_BYTES = 1_048_576L
        private const val RELEASE_ROOT = "https://github.com/nonomnonom/blobs/releases/download"
        private val ALLOWED_HOSTS = setOf(
            "huggingface.co",
            "hf.co",
            "cdn-lfs.huggingface.co",
            "cdn-lfs-us-1.hf.co",
            "us.aws.cdn.hf.co",
            "cas-bridge.xethub.hf.co"
        )

        internal fun parseCatalog(text: String): List<DownloadablePackage> {
            val catalog = JSONObject(text)
            require(catalog.getInt("format") == 1)
            val tag = catalog.getString("release_tag")
            require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
            val entries = catalog.getJSONArray("packages")
            val packages = buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.getJSONObject(index)
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
                        require(
                            asset.compatibility.runtime == "llama.cpp" &&
                                asset.compatibility.format == "GGUF" &&
                                asset.compatibility.minimumRamMb > 0
                        ) { "CATALOG_MODEL_COMPATIBILITY" }
                    } else {
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
            return packages
        }

        internal fun verifyAndParse(
            envelopeText: String,
            publicKey: PublicKey
        ): List<DownloadablePackage> = verifyAndParse(envelopeText, listOf(publicKey))

        internal fun verifyAndParse(
            envelopeText: String,
            publicKeys: List<PublicKey>
        ): List<DownloadablePackage> {
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
            return parseCatalog(payload.toString(Charsets.UTF_8))
        }

        internal fun loadPublicKey(pem: String): PublicKey {
            val encoded = pem.replace(
                Regex("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s"),
                ""
            )
            return KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
        }

        private fun Context.hasValidatedInternet(): Boolean {
            val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
            val network = connectivity.activeNetwork ?: return false
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        private fun writeAtomically(file: File, bytes: ByteArray) {
            file.parentFile?.mkdirs()
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(bytes)
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }
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
