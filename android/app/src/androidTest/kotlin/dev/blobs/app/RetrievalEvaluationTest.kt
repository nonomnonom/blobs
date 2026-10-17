package dev.blobs.app

import android.app.ActivityManager
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.library.RetrievalQuery
import dev.blobs.app.data.local.BlobsDatabase
import dev.blobs.app.data.packages.PackageInstaller
import dev.blobs.app.data.packages.PackageManifest
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

@ManualCheck
class RetrievalEvaluationTest {
    @Test
    fun recordNativeRetrievalForLocalPackages() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val runId = args.getString("runId") ?: UUID.randomUUID().toString()
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "retrieval-$runId.json")
        check(!output.exists())
        val caseFile = File(requireNotNull(args.getString("casesPath")))
        require(caseFile.length() in 1..512_000)
        val caseBytes = caseFile.readBytes()
        val definition = JSONObject(caseBytes.toString(Charsets.UTF_8))
        require(definition.getInt("format") == 1)
        val cases = definition.getJSONArray("cases")
        require(cases.length() in 1..100)
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val results = JSONArray()
        val report = JSONObject().apply {
            put("format", 1)
            put("run_id", runId)
            put("complete", false)
            put("cases", definition)
            put(
                "cases_sha256",
                MessageDigest.getInstance("SHA-256").digest(caseBytes).joinToString("") {
                    "%02x".format(it)
                }
            )
            put(
                "app_version",
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            )
            put("device", Build.MODEL)
            put("fingerprint", Build.FINGERPRINT)
            put("api", Build.VERSION.SDK_INT)
            put("abis", JSONArray(Build.SUPPORTED_ABIS))
            put("total_memory_bytes", memory.totalMem)
            put(
                "timing_scope",
                "Three sequential retrieval calls per query; first call is not guaranteed filesystem-cold. Emulator timings do not predict phone latency."
            )
            put(
                "metric_scope",
                "Expected-title routing coverage only; review complete passages before judging relevance or support. No answer generation or quality score."
            )
            put("results", results)
        }
        save(output, report)
        val folder = File(context.cacheDir, "retrieval-$runId").apply { check(mkdirs()) }
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = folder
        }
        val dbName = "retrieval-$runId.db"
        val database = BlobsDatabase.open(context, dbName)
        try {
            val library = LibraryStore()
            val installer = PackageInstaller(isolated, database.packages(), library)
            for (argument in listOf("baselineLibrary", "candidateLibrary", "installedLibrary")) {
                val source = args.getString(argument) ?: continue
                val existing = argument == "installedLibrary"
                val installed = if (existing) {
                    val container = (context.applicationContext as BlobsApplication).container
                    withTimeout(15_000) { container.work.state.first { it.ready } }
                    check(!container.work.state.value.busy)
                    val pack = container.database.packages().all().single { it.key == source }
                    require(pack.kind == "library")
                    val file = File(pack.path)
                    check(file.length() == pack.bytes)
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val length = input.read(buffer)
                            if (length < 0) break
                            digest.update(buffer, 0, length)
                        }
                    }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == pack.sha256)
                    pack
                } else {
                    val uri = Uri.fromFile(File(source))
                    val inspected = installer.inspect(uri)
                    require(inspected.kind == "library")
                    installer.install(uri, inspected) {}
                }
                val manifest = PackageManifest(
                    installed.packageId,
                    installed.version,
                    installed.kind,
                    installed.title,
                    installed.bytes,
                    installed.sha256,
                    installed.license,
                    installed.origin
                )
                for (index in 0 until cases.length()) {
                    val case = cases.getJSONObject(index)
                    val question = case.getString("question")
                    require(question.length in 1..2000)
                    val query = RetrievalQuery.resolve(
                        question,
                        case.optString("previous_question")
                    )
                    val timings = JSONArray()
                    val evidence = (0 until 3).map {
                        val start = SystemClock.elapsedRealtimeNanos()
                        library.retrieve(listOf(installed), query, null).also {
                            timings.put((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
                        }
                    }.last()
                    val titles = evidence.map { it.title }.toSet()
                    val expected = case.getJSONArray("expected_titles")
                    val found = JSONArray()
                    val missing = JSONArray()
                    for (i in 0 until expected.length()) {
                        val title = expected.getString(i)
                        if (title in titles) found.put(title) else missing.put(title)
                    }
                    results.put(
                        JSONObject().apply {
                            put("variant", argument)
                            put(
                                "preparation",
                                if (existing) {
                                    "Existing installed library verified by size and SHA-256; no import or removal"
                                } else {
                                    "Normal package import into an isolated temporary store"
                                }
                            )
                            put("library", JSONObject(manifest.json()))
                            put("case_id", case.getString("id"))
                            put("resolved_query", query)
                            put("base_fts_query", LibraryStore.searchTerms(query))
                            put(
                                "query_policy",
                                "source-name title phrases plus base lexical terms; versioned title/section indexes exclude structural reference sections except article openings"
                            )
                            put("elapsed_ms", timings)
                            put("expected_titles_found", found)
                            put("expected_titles_missing", missing)
                            put(
                                "evidence",
                                JSONArray().apply {
                                    evidence.forEach { put(it.json()) }
                                }
                            )
                        }
                    )
                    save(output, report)
                }
                if (!existing) installer.remove(installed)
            }
            check(results.length() > 0)
            report.put("complete", true)
        } catch (error: Exception) {
            report.put("error", error.toString())
            throw error
        } finally {
            save(output, report)
            database.close()
            context.deleteDatabase(dbName)
            folder.deleteRecursively()
        }
        println("Retrieval evidence: ${output.absolutePath}")
    }

    private fun save(output: File, report: JSONObject) {
        val file = AtomicFile(output)
        val stream = file.startWrite()
        try {
            stream.write(report.toString(2).toByteArray())
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
