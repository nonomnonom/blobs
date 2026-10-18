package dev.blobs.app

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.PackageEntity
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LibraryRetrievalBenchmarkTest {
    @Test
    fun packagedTravelRetrievalOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val packageFile = File(
            context.getExternalFilesDir(null),
            "retrieval-benchmark-package.blobs"
        )
        assumeTrue(
            "Push the pinned travel package to app-specific external files",
            packageFile.isFile
        )

        val database = File(context.cacheDir, "retrieval-benchmark-library.sqlite")
        val packageIdentity = extractVerifiedPackage(packageFile, database)
        val library = PackageEntity(
            key = "wikivoyage-en",
            packageId = "wikivoyage-en",
            version = "2026-09.2",
            kind = "library",
            title = "Travel guides · English",
            path = database.absolutePath,
            bytes = database.length(),
            sha256 = packageIdentity.getString("payload_sha256"),
            license = "CC BY-SA 4.0",
            origin = "https://en.wikivoyage.org/",
            installedAt = 0L
        )
        val cases = listOf(
            "jakarta-city" to ("What should I know before visiting Jakarta?" to "Jakarta"),
            "japan-rail" to ("How can I travel around Japan by rail?" to "Rail travel in Japan"),
            "beach-packing" to
                ("What should I pack for a beach trip?" to "Packing for beach travel"),
            "china-visas" to
                ("How do visa requirements for China work?" to "Visa requirements for China"),
            "travel-basics" to ("What are the basics of travel?" to "Travel basics"),
            "bangkok-airport" to ("How do I get from the airport to Bangkok city?" to "Bangkok")
        )
        val store = LibraryStore()
        val resultRows = JSONArray()
        val expectedRanks = mutableListOf<Int>()
        val caseMedians = mutableListOf<Long>()

        for ((caseId, pair) in cases) {
            val (question, expectedTitle) = pair
            store.retrieve(listOf(library), question, null)
            val samples = mutableListOf<Long>()
            var titles = emptyList<String>()
            repeat(REPEATS) {
                val start = SystemClock.elapsedRealtimeNanos()
                val evidence = store.retrieve(listOf(library), question, null)
                samples += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                titles = evidence.map { it.title }.distinct()
                assertTrue("$caseId must retrieve $expectedTitle", expectedTitle in titles)
            }
            val sorted = samples.sorted()
            val rank = titles.indexOf(expectedTitle).let { if (it < 0) JSONObject.NULL else it + 1 }
            expectedRanks += titles.indexOf(expectedTitle) + 1
            caseMedians += sorted[sorted.size / 2]
            resultRows.put(
                JSONObject()
                    .put("case_id", caseId)
                    .put("expected_title", expectedTitle)
                    .put("expected_title_rank", rank)
                    .put("selected_titles", JSONArray(titles))
                    .put("elapsed_ms_samples", JSONArray(samples))
                    .put("median_elapsed_ms", sorted[sorted.size / 2])
            )
        }

        val report = JSONObject()
            .put("format", 1)
            .put("scope", "Android retrieval only; no generated answers are evaluated")
            .put("created_at_epoch_ms", System.currentTimeMillis())
            .put(
                "device",
                JSONObject()
                    .put("fingerprint", Build.FINGERPRINT)
                    .put("model", Build.MODEL)
                    .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
                    .put("sdk", Build.VERSION.SDK_INT)
            )
            .put(
                "application",
                JSONObject()
                    .put("package", context.packageName)
                    .put(
                        "version",
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    )
                    .put("apk_sha256", sha256(File(context.applicationInfo.sourceDir)))
                    .put(
                        "test_apk_sha256",
                        sha256(File(instrumentation.context.applicationInfo.sourceDir))
                    )
            )
            .put("package", packageIdentity)
            .put("repeat_count", REPEATS)
            .put(
                "summary",
                JSONObject()
                    .put("case_count", cases.size)
                    .put("expected_titles_found", expectedRanks.size)
                    .put("expected_title_recall", expectedRanks.size.toDouble() / cases.size)
                    .put("expected_titles_at_rank_1", expectedRanks.count { it == 1 })
                    .put(
                        "expected_title_mean_reciprocal_rank",
                        expectedRanks.sumOf { 1.0 / it } / expectedRanks.size
                    )
                    .put("case_median_elapsed_ms_median", median(caseMedians))
                    .put(
                        "case_median_elapsed_ms_p95",
                        caseMedians.sorted()[ceil(caseMedians.size * 0.95).toInt() - 1]
                    )
            )
            .put("cases", resultRows)

        File(context.filesDir, "library-retrieval-benchmark.json").writeText(report.toString(2))
        Log.i(TAG, report.toString())
        database.delete()
    }

    private fun extractVerifiedPackage(packageFile: File, database: File): JSONObject =
        ZipFile(packageFile).use { archive ->
            val entries = archive.entries().toList().map { it.name }
            assertEquals(listOf("manifest.json", "library.sqlite"), entries)
            val manifest = JSONObject(
                archive.getInputStream(archive.getEntry("manifest.json"))
                    .bufferedReader().use { it.readText() }
            )
            assertEquals("wikivoyage-en", manifest.getString("id"))
            assertEquals("2026-09.2", manifest.getString("version"))
            assertEquals("library", manifest.getString("kind"))
            assertEquals(EXPECTED_PAYLOAD_SHA256, manifest.getString("sha256"))
            assertTrue(manifest.getLong("bytes") in 1..MAX_DATABASE_BYTES)

            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            archive.getInputStream(archive.getEntry("library.sqlite")).use { input ->
                database.outputStream().buffered().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        assertTrue(
                            "The package payload exceeded its manifest",
                            count <= manifest.getLong("bytes")
                        )
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            assertEquals(manifest.getLong("bytes"), count)
            val payloadDigest = digest.digest().joinToString("") { "%02x".format(it) }
            assertEquals(EXPECTED_PAYLOAD_SHA256, payloadDigest)
            JSONObject()
                .put("id", manifest.getString("id"))
                .put("version", manifest.getString("version"))
                .put("payload_sha256", payloadDigest)
                .put("package_sha256", sha256(packageFile))
                .put("payload_bytes", count)
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2
        } else {
            sorted[middle]
        }
    }

    companion object {
        private const val TAG = "BlobsRetrievalBench"
        private const val REPEATS = 5
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val MAX_DATABASE_BYTES = 1_000_000_000L
        private const val EXPECTED_PAYLOAD_SHA256 =
            "d04019952ea5dc8b8b31568cea39b5203f18cb6969086225ec48d88eb0291056"
    }
}
