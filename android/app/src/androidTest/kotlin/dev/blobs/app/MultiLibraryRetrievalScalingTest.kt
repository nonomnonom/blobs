package dev.blobs.app

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.WorkController
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.library.LibraryStore.Companion.multiLibraryCandidateLimit
import dev.blobs.app.data.library.RetrievalQuery
import dev.blobs.app.data.local.PackageEntity
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiLibraryRetrievalScalingTest {
    @ManualCheck
    @Test
    fun retrievalAcrossGrowingInstalledLibrarySet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val externalFiles = context.getExternalFilesDir(null) ?: run {
            Log.i(TAG, "SKIP: app-specific external files are unavailable")
            return
        }
        val configFile = File(externalFiles, CONFIG_FILE)
        assertTrue("Push $CONFIG_FILE and all pinned packages to app storage", configFile.isFile)

        val config = JSONObject(configFile.readText())
        assertEquals(1, config.getInt("format"))
        val librarySpecs = config.getJSONArray("libraries")
        val caseRows = config.getJSONArray("cases")
        assertTrue("The run needs multiple libraries", librarySpecs.length() > 1)
        assertTrue("The run needs queries", caseRows.length() > 0)

        val temporaryDatabases = mutableListOf<File>()
        val store = LibraryStore()
        try {
            val importedLibraries = (0 until librarySpecs.length()).map { index ->
                val spec = librarySpecs.getJSONObject(index)
                val source = File(externalFiles, spec.getString("file"))
                assertTrue("Missing staged package: ${source.name}", source.isFile)
                val database =
                    File(context.cacheDir, "retrieval-scale-${spec.getString("id")}.sqlite")
                database.delete()
                temporaryDatabases += database
                extractVerifiedPackage(source, database, spec)
            }
            val libraries = importedLibraries.map { it.entity }
            val cases = (0 until caseRows.length()).map { index ->
                val row = caseRows.getJSONObject(index)
                Case(
                    id = row.getString("id"),
                    question = row.getString("question"),
                    expectedLibraryId = row.getString("expected_library_id"),
                    expectedTitle = row.getString("expected_document_title"),
                    maxExpectedTitleRank = row.optInt("max_expected_title_rank")
                        .takeIf { it > 0 }
                )
            }
            assertTrue(
                "Every expected source must belong to the pinned library set",
                cases.all { case -> libraries.any { it.packageId == case.expectedLibraryId } }
            )

            val scenarios = JSONArray()
            for (count in 1..libraries.size) {
                val active = libraries.take(count)
                val caseResults = JSONArray()
                val caseMedians = mutableListOf<Double>()
                for (case in cases) {
                    store.retrieve(active, case.question, null)
                    val queryTitleTerms = RetrievalQuery.titleTerms(case.question)
                    val samplesMs = mutableListOf<Double>()
                    var selectedEvidence = emptyList<Evidence>()
                    repeat(REPEATS) {
                        val start = SystemClock.elapsedRealtimeNanos()
                        selectedEvidence = store.retrieve(active, case.question, null)
                        samplesMs +=
                            (SystemClock.elapsedRealtimeNanos() - start) / NANOS_PER_MILLISECOND
                    }
                    val titles = selectedEvidence.map { it.title }.distinct()
                    val expectedSourceEnabled = active.any {
                        it.packageId == case.expectedLibraryId
                    }
                    val rank = selectedEvidence.indexOfFirst {
                        it.id.startsWith("${case.expectedLibraryId}/") &&
                            it.title == case.expectedTitle
                    }.let { if (it < 0) 0 else it + 1 }
                    if (expectedSourceEnabled) {
                        assertTrue(
                            "${case.id} expected ${case.expectedTitle} at rank $rank with $count libraries",
                            rank > 0
                        )
                        assertTrue(
                            "${case.id} must keep its expected source inside the " +
                                "${WorkController.MAX_ANSWER_SOURCES}-source chat context; got rank $rank",
                            rank <= WorkController.MAX_ANSWER_SOURCES
                        )
                        case.maxExpectedTitleRank?.let { maxRank ->
                            assertTrue(
                                "${case.id} expected ${case.expectedTitle} at or before rank " +
                                    "$maxRank with $count libraries; got $rank",
                                rank <= maxRank
                            )
                        }
                    }
                    val medianMs = median(samplesMs)
                    caseMedians += medianMs
                    caseResults.put(
                        JSONObject()
                            .put("case_id", case.id)
                            .put("expected_source_enabled", expectedSourceEnabled)
                            .put("expected_document_title", case.expectedTitle)
                            .put("expected_title_rank", rank)
                            .put("max_expected_title_rank", case.maxExpectedTitleRank)
                            .put("selected_titles", JSONArray(titles))
                            .put(
                                "selected_sources",
                                JSONArray(
                                    selectedEvidence.map { evidence ->
                                        JSONObject()
                                            .put("library_id", evidence.id.substringBefore('/'))
                                            .put("document_id", evidence.id.substringAfter('/'))
                                            .put("title", evidence.title)
                                            .put("section", evidence.section)
                                            .put(
                                                "title_overlap",
                                                RetrievalQuery.titleOverlap(
                                                    queryTitleTerms,
                                                    evidence.title
                                                )
                                            )
                                            .put(
                                                "evidence_overlap",
                                                RetrievalQuery.evidenceOverlap(
                                                    queryTitleTerms,
                                                    evidence.title,
                                                    evidence.section,
                                                    evidence.text
                                                )
                                            )
                                    }
                                )
                            )
                            .put("elapsed_ms_samples", JSONArray(samplesMs))
                            .put("median_elapsed_ms", medianMs)
                    )
                }
                val sorted = caseMedians.sorted()
                scenarios.put(
                    JSONObject()
                        .put("enabled_library_count", count)
                        .put("enabled_library_ids", JSONArray(active.map { it.packageId }))
                        .put("query_median_elapsed_ms_median", median(sorted))
                        .put(
                            "query_median_elapsed_ms_p95",
                            sorted[
                                ceil(sorted.size * 0.95).toInt() -
                                    1
                            ]
                        )
                        .put("cases", caseResults)
                )
            }

            val report = JSONObject()
                .put("format", 1)
                .put(
                    "scope",
                    "Production Android retrieval across enabled local libraries; no model or generated answers"
                )
                .put("created_at_epoch_ms", System.currentTimeMillis())
                .put(
                    "case_set",
                    JSONObject()
                        .put("id", config.getString("id"))
                        .put("sha256", sha256(configFile))
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
                            context.packageManager.getPackageInfo(
                                context.packageName,
                                0
                            ).versionName
                        )
                        .put("apk_sha256", sha256(File(context.applicationInfo.sourceDir)))
                        .put(
                            "test_apk_sha256",
                            sha256(File(instrumentation.context.applicationInfo.sourceDir))
                        )
                )
                .put("repeat_count", REPEATS)
                .put("multi_library_candidate_limit", multiLibraryCandidateLimit)
                .put("libraries", JSONArray(importedLibraries.map { it.identity }))
                .put("scenarios", scenarios)

            val serialized = report.toString()
            val parts = serialized.chunked(LOG_PART_CHARACTERS)
            parts.forEachIndexed { index, part ->
                Log.i(TAG, "REPORT_PART:${index + 1}/${parts.size}:$part")
            }
            Log.i(
                TAG,
                "SUMMARY:library_counts=1..${libraries.size}, case_count=${cases.size}, repeats=$REPEATS"
            )
        } finally {
            temporaryDatabases.forEach(File::delete)
        }
    }

    private fun extractVerifiedPackage(
        source: File,
        database: File,
        expected: JSONObject
    ): ImportedLibrary = ZipFile(source).use { archive ->
        val entries = archive.entries().toList().map { it.name }
        assertEquals(listOf("manifest.json", "library.sqlite"), entries)
        val manifest = JSONObject(
            archive.getInputStream(archive.getEntry("manifest.json"))
                .bufferedReader().use { it.readText() }
        )
        assertEquals(expected.getString("id"), manifest.getString("id"))
        assertEquals(expected.getString("version"), manifest.getString("version"))
        assertEquals(expected.getString("payload_sha256"), manifest.getString("sha256"))
        assertEquals("library", manifest.getString("kind"))
        val payload = archive.getInputStream(archive.getEntry("library.sqlite"))
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        payload.use { input ->
            FileOutputStream(database).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    bytes += read
                    assertTrue(
                        "Package payload exceeded the declared size",
                        bytes <= manifest.getLong("bytes")
                    )
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        assertEquals(manifest.getLong("bytes"), bytes)
        assertEquals(
            manifest.getString("sha256"),
            digest.digest().joinToString("") {
                "%02x".format(it)
            }
        )
        val entity = PackageEntity(
            key = manifest.getString("id"),
            packageId = manifest.getString("id"),
            version = manifest.getString("version"),
            kind = "library",
            title = manifest.getString("title"),
            path = database.absolutePath,
            bytes = manifest.getLong("bytes"),
            sha256 = manifest.getString("sha256"),
            license = manifest.getString("license"),
            origin = manifest.getString("origin"),
            installedAt = 0L
        )
        ImportedLibrary(entity, manifest)
    }

    private fun sha256(file: File): String = file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 ==
            0
        ) {
            (sorted[middle - 1] + sorted[middle]) / 2
        } else {
            sorted[middle]
        }
    }

    private data class Case(
        val id: String,
        val question: String,
        val expectedLibraryId: String,
        val expectedTitle: String,
        val maxExpectedTitleRank: Int?
    )

    private data class ImportedLibrary(val entity: PackageEntity, val identity: JSONObject)

    private companion object {
        const val TAG = "BlobsCorpusScaleBench"
        const val CONFIG_FILE = "retrieval-benchmark-multi-library.json"
        const val REPEATS = 5
        const val LOG_PART_CHARACTERS = 2_500
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}
