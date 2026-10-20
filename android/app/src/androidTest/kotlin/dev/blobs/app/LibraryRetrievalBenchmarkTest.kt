package dev.blobs.app

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
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
import org.junit.Test

class LibraryRetrievalBenchmarkTest {
    @Test
    fun packagedLibraryRetrievalOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val externalFiles = context.getExternalFilesDir(null)
        if (externalFiles == null) {
            Log.i(TAG, "SKIP: app-specific external files are unavailable")
            return
        }
        val packageFile = File(externalFiles, "retrieval-benchmark-package.blobs")
        val casesFile = File(externalFiles, "retrieval-benchmark-cases.json")
        if (!packageFile.isFile || !casesFile.isFile) {
            Log.i(TAG, "SKIP: push a pinned library package and retrieval case set to app storage")
            return
        }

        val caseSet = JSONObject(casesFile.readText())
        assertEquals(1, caseSet.getInt("format"))
        val caseSetId = caseSet.getString("id")
        val expectedLibrary = caseSet.getJSONObject("library")
        val casesJson = caseSet.getJSONArray("cases")
        assertTrue("The case set must contain queries", casesJson.length() > 0)
        val cases = (0 until casesJson.length()).map { index ->
            val row = casesJson.getJSONObject(index)
            val expectedTitles = row.getJSONArray("expected_document_titles")
            Triple(
                row.getString("id"),
                row.getString("question"),
                (0 until expectedTitles.length()).map(expectedTitles::getString)
            )
        }
        val passageRequirements = (0 until casesJson.length()).associate { index ->
            val row = casesJson.getJSONObject(index)
            row.getString("id") to row.optJSONArray("required_passages")
        }
        assertTrue(
            "Every query must have at least one expected source",
            cases.all {
                it.third.isNotEmpty() || (passageRequirements[it.first]?.length() ?: 0) > 0
            }
        )

        val database = File(context.cacheDir, "retrieval-benchmark-library.sqlite")
        val packageIdentity = extractVerifiedPackage(packageFile, database, expectedLibrary)
        val library = PackageEntity(
            key = packageIdentity.getString("id"),
            packageId = packageIdentity.getString("id"),
            version = packageIdentity.getString("version"),
            kind = "library",
            title = packageIdentity.getString("title"),
            path = database.absolutePath,
            bytes = database.length(),
            sha256 = packageIdentity.getString("payload_sha256"),
            license = packageIdentity.getString("license"),
            origin = packageIdentity.getString("origin"),
            installedAt = 0L
        )
        val store = LibraryStore()
        val resultRows = JSONArray()
        val expectedRanks = mutableListOf<Int>()
        val caseMedians = mutableListOf<Long>()
        val expectedTitleCount = cases.sumOf { it.third.size }

        for ((caseId, question, expectedTitles) in cases) {
            store.retrieve(listOf(library), question, null)
            val samples = mutableListOf<Long>()
            var titles = emptyList<String>()
            var selected = emptyList<Evidence>()
            repeat(REPEATS) {
                val start = SystemClock.elapsedRealtimeNanos()
                val evidence = store.retrieve(listOf(library), question, null)
                selected = evidence
                samples += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                titles = evidence.map { it.title }.distinct()
                for (expectedTitle in expectedTitles) {
                    assertTrue("$caseId must retrieve $expectedTitle", expectedTitle in titles)
                }
                passageRequirements[caseId]?.let { requirements ->
                    repeat(requirements.length()) { index ->
                        val requirement = requirements.getJSONObject(index)
                        val title = requirement.getString("title")
                        val terms = requirement.getJSONArray("contains_terms")
                        val rank = requirement.getInt("max_rank")
                        assertTrue(terms.length() > 0 && rank in 1..6)
                        assertTrue(
                            "$caseId needs a matching passage for $title within $rank sources",
                            evidence.take(rank).any { source ->
                                (source.title == title || source.title.startsWith("$title/")) &&
                                    (0 until terms.length()).all {
                                        source.text.contains(terms.getString(it), ignoreCase = true)
                                    }
                            }
                        )
                    }
                }
            }
            val sorted = samples.sorted()
            val ranks = expectedTitles.map { titles.indexOf(it) + 1 }
            expectedRanks += ranks
            caseMedians += sorted[sorted.size / 2]
            resultRows.put(
                JSONObject()
                    .put("case_id", caseId)
                    .put("expected_document_titles", JSONArray(expectedTitles))
                    .put("expected_title_ranks", JSONArray(ranks))
                    .put("selected_titles", JSONArray(titles))
                    .put(
                        "selected_evidence",
                        JSONArray(
                            selected.map { source ->
                                JSONObject()
                                    .put("document_id", source.id.substringAfter('/'))
                                    .put("title", source.title)
                                    .put("section", source.section)
                                    .put(
                                        "text_sha256",
                                        MessageDigest.getInstance("SHA-256")
                                            .digest(source.text.toByteArray())
                                            .joinToString("") { "%02x".format(it) }
                                    )
                            }
                        )
                    )
                    .put("elapsed_ms_samples", JSONArray(samples))
                    .put("median_elapsed_ms", sorted[sorted.size / 2])
            )
        }

        val report = JSONObject()
            .put("format", 1)
            .put("scope", "Android retrieval only; no generated answers are evaluated")
            .put("created_at_epoch_ms", System.currentTimeMillis())
            .put(
                "case_set",
                JSONObject()
                    .put("id", caseSetId)
                    .put("sha256", sha256(casesFile))
            )
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
                    .put("expected_titles", expectedTitleCount)
                    .put("expected_titles_found", expectedRanks.count { it > 0 })
                    .put(
                        "expected_title_recall",
                        expectedRanks.count { it > 0 }.toDouble() / expectedTitleCount
                    )
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

        val serialized = report.toString()
        val parts = serialized.chunked(LOG_PART_CHARACTERS)
        parts.forEachIndexed { index, part ->
            Log.i(TAG, "REPORT_PART:${index + 1}/${parts.size}:$part")
        }
        Log.i(TAG, "SUMMARY:${report.getJSONObject("summary")}")
        database.delete()
    }

    private fun extractVerifiedPackage(
        packageFile: File,
        database: File,
        expectedLibrary: JSONObject
    ): JSONObject = ZipFile(packageFile).use { archive ->
        val entries = archive.entries().toList().map { it.name }
        assertEquals(listOf("manifest.json", "library.sqlite"), entries)
        val manifest = JSONObject(
            archive.getInputStream(archive.getEntry("manifest.json"))
                .bufferedReader().use { it.readText() }
        )
        assertEquals(expectedLibrary.getString("id"), manifest.getString("id"))
        assertEquals(expectedLibrary.getString("version"), manifest.getString("version"))
        assertEquals("library", manifest.getString("kind"))
        assertEquals(expectedLibrary.getString("payload_sha256"), manifest.getString("sha256"))
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
        assertEquals(expectedLibrary.getString("payload_sha256"), payloadDigest)
        JSONObject()
            .put("id", manifest.getString("id"))
            .put("version", manifest.getString("version"))
            .put("title", manifest.getString("title"))
            .put("license", manifest.getString("license"))
            .put("origin", manifest.getString("origin"))
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
        private const val LOG_PART_CHARACTERS = 3000
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val MAX_DATABASE_BYTES = 1_000_000_000L
    }
}
