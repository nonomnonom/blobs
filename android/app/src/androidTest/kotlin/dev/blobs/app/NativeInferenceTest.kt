package dev.blobs.app

import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.ConversationEntity
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.GgufValidator
import dev.blobs.app.data.packages.PackageManifest
import dev.blobs.app.inference.FollowUpSuggestions
import dev.blobs.app.inference.ModelSupport
import dev.blobs.app.inference.ResearchPrompt
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

@ManualCheck
class NativeInferenceTest {
    @Test
    fun validatesSuppliedLibraryFile() {
        val args = InstrumentationRegistry.getArguments()
        val path = args.getString("libraryFile")
        assumeTrue(
            "Provide a verified SQLite file with -e libraryFile <app-private path>",
            path != null
        )
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val expectedBytes = requireNotNull(args.getString("expectedBytes")).toLong()
        val expectedSha256 = requireNotNull(args.getString("expectedSha256"))
        require(expectedBytes in 1..40_000_000_000L)
        require(expectedSha256.matches(Regex("[a-f0-9]{64}")))
        val file = File(requireNotNull(path))
        check(file.isFile && file.length() == expectedBytes)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "library-validation-$runId.json")
        check(!output.exists())
        val report = JSONObject().apply {
            put("format", 1)
            put("run_id", runId)
            put("complete", false)
            put("fingerprint", Build.FINGERPRINT)
            put("api", Build.VERSION.SDK_INT)
            put("library_bytes", expectedBytes)
            put("library_sha256_expected", expectedSha256)
            put("phase", "hashing")
        }
        fun save() = saveReport(output, report)
        save()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashStart = SystemClock.elapsedRealtime()
            file.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    digest.update(buffer, 0, length)
                }
            }
            report.put("hash_elapsed_ms", SystemClock.elapsedRealtime() - hashStart)
            check(digest.digest().joinToString("") { "%02x".format(it) } == expectedSha256)
            report.put("phase", "database_validation")
            save()
            val validationStart = SystemClock.elapsedRealtime()
            LibraryStore().validate(file, catalogDigestPinned = true)
            report.put("validation_elapsed_ms", SystemClock.elapsedRealtime() - validationStart)
            report.put("complete", true)
        } catch (error: Exception) {
            report.put("error", error.toString())
            throw error
        } finally {
            save()
        }
        println("Library validation evidence: ${output.absolutePath}")
    }

    @Test
    fun prepareEvaluationDrop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = requireNotNull(context.getExternalFilesDir("eval"))
        check(folder.mkdirs() || folder.isDirectory)
        println("Evaluation drop: ${folder.absolutePath}")
    }

    @Test
    fun stageEvaluationFileFromAppExternalStorage() {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val sourceName = requireNotNull(args.getString("sourceName"))
        require(sourceName.matches(Regex("[a-zA-Z0-9._-]{1,120}")))
        val expectedBytes = requireNotNull(args.getString("expectedBytes")).toLong()
        require(expectedBytes in 1..20L * 1024 * 1024 * 1024)
        val expectedSha256 = requireNotNull(args.getString("expectedSha256"))
        require(expectedSha256.matches(Regex("[a-f0-9]{64}")))
        val deleteSourceAfterCopy = args.getString("deleteSourceAfterCopy") == "true"

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceRoot = requireNotNull(context.getExternalFilesDir("eval")).canonicalFile
        check(sourceRoot.mkdirs() || sourceRoot.isDirectory)
        val source = File(sourceRoot, sourceName).canonicalFile
        check(source.parentFile == sourceRoot && source.isFile)
        check(source.length() == expectedBytes)

        val destinationRoot = File(context.filesDir, "evaluation/$runId")
        check(destinationRoot.mkdirs() || destinationRoot.isDirectory)
        val destination = File(destinationRoot, sourceName)
        check(!destination.exists())
        val availableBytes = StatFs(destinationRoot.path).availableBytes
        check(availableBytes > expectedBytes + 64L * 1024 * 1024) {
            "Need the file plus 64 MiB of free app storage"
        }

        val atomic = AtomicFile(destination)
        val output = atomic.startWrite()
        try {
            val command = "cat ${source.absolutePath}"
            ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand(command)
            ).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(256 * 1024)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    check(copied <= expectedBytes) { "Staged file exceeds its locked size" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                check(copied == expectedBytes) { "Staged file is incomplete" }
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                check(actualSha256 == expectedSha256) { "Staged file differs from its lock" }
            }
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        if (deleteSourceAfterCopy) check(source.delete())
        println("Staged evaluation file: ${destination.absolutePath}")
    }

    @Test
    fun prepareLibraryForAnswerEvaluation() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "prepared-library-$runId.json")
        check(!output.exists())
        val container = (context.applicationContext as BlobsApplication).container
        withTimeout(15_000) { container.work.state.first { it.ready } }
        val report = JSONObject().put("format", 1).put("complete", false)
            .put("scope", "Normal library import before staging a separate model test fixture")
        saveReport(output, report)
        try {
            val uri = Uri.fromFile(File(requireNotNull(args.getString("libraryPackage"))))
            val manifest = container.installer.inspect(uri)
            require(manifest.kind == "library")
            val catalogId = args.getString("catalogLibraryId")
            val catalogPinned = catalogId != null
            if (catalogPinned) {
                val asset = container.catalog.recoverableDownloads.single {
                    it.manifest.id == catalogId && it.manifest.kind == "library"
                }
                check(manifest == asset.manifest) { "Library differs from the trusted catalog" }
            }
            report.put("manifest", JSONObject(manifest.json()))
            report.put("catalog_digest_pinned", catalogPinned)
            saveReport(output, report)
            val installed = container.installer.install(uri, manifest, catalogPinned) {}
            report.put("installed_key", installed.key).put("complete", true)
        } catch (error: Exception) {
            report.put("error", error.toString())
            throw error
        } finally {
            saveReport(output, report)
        }
    }

    @Test
    fun installStagedCatalogModelForEvaluation() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as BlobsApplication).container
        withTimeout(15_000) { container.work.state.first { it.ready } }
        check(!container.work.state.value.busy)
        val asset = container.catalog.recoverableDownloads.single {
            it.manifest.id == args.getString("catalogModelId") && it.manifest.kind == "model"
        }
        check(container.database.packages().all().none { it.key == asset.manifest.key }) {
            "This check requires a model that is not already installed"
        }
        val source = File(requireNotNull(args.getString("modelFile"))).canonicalFile
        check(source.parentFile == File(context.filesDir, "package-downloads").canonicalFile)
        check(source.isFile && source.extension == "gguf")
        val output = File(context.filesDir, "installed-catalog-$runId.json")
        check(!output.exists())
        val report = JSONObject().put("format", 1).put("complete", false)
            .put("scope", "Normal catalog-pinned raw GGUF installer; locally staged bytes")
            .put("manifest", JSONObject(asset.manifest.json()))
            .put("fingerprint", Build.FINGERPRINT)
        saveReport(output, report)
        val start = SystemClock.elapsedRealtime()
        try {
            val installed = container.installer.install(Uri.fromFile(source), asset.manifest) {}
            report.put("installed_key", installed.key).put("installed_path", installed.path)
                .put("complete", true)
            if (args.getString("deleteSourceAfterInstall") == "true") {
                check(source.delete())
                report.put("staged_source_deleted", true)
            }
        } catch (error: Exception) {
            report.put("error", error.toString())
            throw error
        } finally {
            report.put("elapsed_ms", SystemClock.elapsedRealtime() - start)
            saveReport(output, report)
        }
    }

    @Test
    fun installedLibraryAndModelCompleteOfflineChatFlow() = recordOfflineChatFlow(false)

    @Test
    fun verifiedModelFileCompletesOfflineChatFlow() = recordOfflineChatFlow(true)

    @Test
    fun publicFixtureAnswerEvaluation() = recordOfflineChatFlow(true, capturePublicFixture = true)

    @Test
    fun emptyLibraryStillAllowsOfflineChat() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as BlobsApplication).container
        withTimeout(15_000) { container.work.state.first { it.ready } }
        val model = container.database.packages().all().firstOrNull {
            it.kind == "model" && File(it.path).isFile
        }
        assumeTrue("Install a local model before running this check", model != null)
        val runId = args.getString("runId") ?: UUID.randomUUID().toString()
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val id = "chat-no-sources-$runId"
        val question = "Hi, what can you help me with?"
        val reportFile = File(context.filesDir, "chat-no-sources-$runId.json")
        check(!reportFile.exists())
        container.database.conversations().save(
            ConversationEntity(
                id,
                "",
                question,
                System.currentTimeMillis(),
                System.currentTimeMillis()
            )
        )
        val startedAt = SystemClock.elapsedRealtime()
        assertTrue(
            container.work.answer(
                id,
                question,
                requireNotNull(model),
                emptyList(),
                null
            )
        )
        withTimeout(600_000) { container.work.state.first { it.threadId == id && !it.busy } }
        val turn = container.database.conversations().turns(id).single()
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        assertEquals("COMPLETE", turn.status)
        assertTrue(turn.answer.isNotBlank())
        assertEquals("[]", turn.evidence)
        saveReport(
            reportFile,
            JSONObject().apply {
                put("format", 1)
                put("run_id", runId)
                put("complete", true)
                put(
                    "scope",
                    "Casual chat with an installed offline model and no available library sources."
                )
                put("application_id", context.packageName)
                put("fingerprint", Build.FINGERPRINT)
                put("api", Build.VERSION.SDK_INT)
                put("question", question)
                put("status", turn.status)
                put("answer", turn.answer)
                put("evidence", turn.evidence)
                put("elapsed_ms", elapsedMs)
            }
        )
    }

    private fun recordOfflineChatFlow(
        verifiedFile: Boolean,
        capturePublicFixture: Boolean = false
    ) = runBlocking {
        val foreground = ActivityScenario.launch(MainActivity::class.java)
        val args = InstrumentationRegistry.getArguments()
        val installedModelKey = args.getString("installedModel")
        val modelPath = args.getString(if (verifiedFile) "modelFile" else "modelPackage")
        require(
            if (verifiedFile) {
                modelPath != null && installedModelKey == null
            } else {
                (modelPath != null) xor (installedModelKey != null)
            }
        ) { "Provide exactly one of modelFile, modelPackage, or installedModel" }
        val libraryPath = args.getString("libraryPackage")
        val installedLibraryKey = args.getString("installedLibrary")
        require((libraryPath != null) xor (installedLibraryKey != null)) {
            "Provide exactly one of libraryPackage or installedLibrary"
        }
        val runId = args.getString("runId") ?: UUID.randomUUID().toString()
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val question = decodedArgument(args, "question")
            ?: "How do trees reduce urban heat? Explain with sources."
        if (capturePublicFixture) {
            require(decodedArgument(args, "question") != null) {
                "Provide an explicit public evaluation question; this check exports its answer and sources"
            }
        }
        require(question.length in 1..2000)
        val timeoutMs = (args.getString("timeoutSeconds") ?: "240").toLong() * 1000
        require(timeoutMs in 60_000..600_000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val airplaneModeEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) == 1
        if (args.getString("requireAirplaneMode") == "true") {
            assertTrue("Airplane mode must be enabled for this offline run", airplaneModeEnabled)
        }
        val container = (context.applicationContext as BlobsApplication).container
        withTimeout(15_000) { container.work.state.first { it.ready } }
        val output = File(context.filesDir, "answer-$runId.json")
        check(!output.exists())
        val id = "answer-$runId"
        val report = JSONObject().apply {
            put("format", 1)
            put("run_id", runId)
            put("complete", false)
            put("question", question)
            put("application_id", context.packageName)
            put("fingerprint", Build.FINGERPRINT)
            put("api", Build.VERSION.SDK_INT)
            put("airplane_mode_enabled", airplaneModeEnabled)
            put("generation_started", false)
            put(
                "model_preparation",
                if (verifiedFile) {
                    "Verified GGUF file: checksum and native importer validator, without model installation. Library import and WorkController persistence/generation are exercised; normal model-package import is not proved."
                } else if (installedModelKey != null) {
                    "Catalog-installed model package; package identity and GGUF are verified before the offline chat path."
                } else {
                    "Normal model-package import"
                }
            )
            put("timeout_ms", timeoutMs)
            put(
                "timing_scope",
                "End-to-end app observation on this device. First nonblank WorkState text is not native token latency. No phone-performance extrapolation."
            )
        }
        fun save() = saveReport(output, report)
        save()
        val installed = try {
            listOf(modelPath.orEmpty(), libraryPath.orEmpty()).mapIndexed { index, path ->
                val kind = if (index == 0) "model" else "library"
                if (index == 0 && verifiedFile) {
                    val expected = requireNotNull(args.getString("modelSha256"))
                    require(expected.matches(Regex("[a-f0-9]{64}")))
                    val file = File(path)
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val length = input.read(buffer)
                            if (length < 0) break
                            digest.update(buffer, 0, length)
                        }
                    }
                    check(digest.digest().joinToString("") { "%02x".format(it) } == expected)
                    GgufValidator.validate(file)
                    report.put("model_file_bytes", file.length())
                    save()
                    return@mapIndexed PackageEntity(
                        "fixture-${expected.take(16)}",
                        args.getString("modelId") ?: "fixture-model",
                        expected.take(8),
                        "model", file.name, file.path, file.length(), expected,
                        "Evaluation fixture; see its pinned model license",
                        "local:verified-fixture", 0
                    )
                }
                if (index == 0 && installedModelKey != null) {
                    val pack = container.database.packages().all().single {
                        it.key == installedModelKey
                    }
                    require(pack.kind == "model")
                    val packageRoot = File(context.filesDir, "packages").canonicalFile
                    val file = File(pack.path).canonicalFile
                    check(file.path.startsWith(packageRoot.path + File.separator))
                    check(file.isFile && file.length() == pack.bytes)
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
                    GgufValidator.validate(file)
                    report.put(
                        "model_manifest",
                        JSONObject(
                            PackageManifest(
                                pack.packageId,
                                pack.version,
                                pack.kind,
                                pack.title,
                                pack.bytes,
                                pack.sha256,
                                pack.license,
                                pack.origin
                            ).json()
                        )
                    )
                    save()
                    return@mapIndexed pack
                }
                if (index == 1 && installedLibraryKey != null) {
                    val pack = container.database.packages().all().single {
                        it.key == installedLibraryKey
                    }
                    require(pack.kind == "library")
                    val packageRoot = File(context.filesDir, "packages").canonicalFile
                    val file = File(pack.path).canonicalFile
                    check(file.path.startsWith(packageRoot.path + File.separator))
                    check(file.isFile && file.length() == pack.bytes)
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
                    report.put(
                        "library_manifest",
                        JSONObject(
                            PackageManifest(
                                pack.packageId,
                                pack.version,
                                pack.kind,
                                pack.title,
                                pack.bytes,
                                pack.sha256,
                                pack.license,
                                pack.origin
                            ).json()
                        )
                    )
                    save()
                    return@mapIndexed pack
                }
                val uri = Uri.fromFile(File(path))
                val manifest = container.installer.inspect(uri)
                require(manifest.kind == kind)
                report.put("${kind}_manifest", JSONObject(manifest.json()))
                save()
                container.installer.install(uri, manifest) {}
            }
        } catch (error: Exception) {
            report.put("status", if (verifiedFile) "PREPARATION_FAILED" else "IMPORT_FAILED")
            report.put("error", error.toString())
            save()
            throw error
        }
        val model = installed[0]
        val library = installed[1]
        report.put("model_sha256", model.sha256).put("model_key", model.key)
        report.put("library_sha256", library.sha256).put("library_key", library.key)
        container.database.conversations().save(
            ConversationEntity(
                id,
                "",
                question,
                System.currentTimeMillis(),
                System.currentTimeMillis()
            )
        )
        val start = SystemClock.elapsedRealtime()
        var firstVisibleMs: Long? = null
        var evidenceReadyMs: Long? = null
        var lastPublicFixtureText = ""
        val fixtureObserver = if (capturePublicFixture) {
            launch {
                container.work.state.collect {
                    if (it.threadId == id && it.text.isNotBlank()) lastPublicFixtureText = it.text
                }
            }
        } else {
            null
        }
        val evidenceObserver = launch {
            container.work.state.first { it.threadId == id && it.evidence != "[]" }
            evidenceReadyMs = SystemClock.elapsedRealtime() - start
        }
        val observer = launch {
            container.work.state.first { it.threadId == id && it.text.isNotBlank() }
            firstVisibleMs = SystemClock.elapsedRealtime() - start
        }
        var finished = false
        try {
            assertTrue(container.work.answer(id, question, model, installed, null))
            report.put("generation_started", true)
            save()
            container.database.conversations().saveDraft(
                id,
                "My next question stays here",
                System.currentTimeMillis()
            )
            finished = withTimeoutOrNull(timeoutMs) {
                container.work.state.first { !it.busy }
            } != null
        } catch (error: Exception) {
            report.put("test_error", error.toString())
            throw error
        } finally {
            if (container.work.state.value.busy) {
                container.work.stop()
                withTimeoutOrNull(60_000) { container.work.state.first { !it.busy } }
            }
            observer.cancel()
            evidenceObserver.cancel()
            fixtureObserver?.cancel()
            val turn = container.database.conversations().turns(id).lastOrNull()
            report.put("complete", !container.work.state.value.busy && turn != null)
            report.put("finished_within_timeout", finished)
            report.put("wall_elapsed_ms", SystemClock.elapsedRealtime() - start)
            report.put("first_visible_state_ms", firstVisibleMs ?: JSONObject.NULL)
            report.put("evidence_ready_state_ms", evidenceReadyMs ?: JSONObject.NULL)
            report.put("status", turn?.status ?: JSONObject.NULL)
            report.put("grounding", turn?.grounding ?: JSONObject.NULL)
            report.put("finish_reason", turn?.finishReason ?: JSONObject.NULL)
            report.put("model_support", turn?.modelSupport ?: JSONObject.NULL)
            report.put(
                "follow_up_count",
                turn?.suggestions?.let { FollowUpSuggestions.decodeOrNull(it)?.size }
                    ?: JSONObject.NULL
            )
            val evidence = turn?.evidence?.let(Evidence::decode).orEmpty()
            report.put("evidence_count", evidence.size)
            if (capturePublicFixture) {
                report.put("answer", turn?.answer ?: JSONObject.NULL)
                report.put("last_observed_streamed_text", lastPublicFixtureText)
                report.put("evidence", org.json.JSONArray(turn?.evidence ?: "[]"))
                report.put(
                    "content_scope",
                    "Explicit public evaluation fixture; saved user-visible answer"
                )
            }
            report.put("saved_elapsed_ms", turn?.elapsedMs ?: JSONObject.NULL)
            report.put("error", turn?.error ?: JSONObject.NULL)
            val performance = container.work.lastPerformanceSnapshot
            report.put(
                "performance",
                performance?.let {
                    JSONObject()
                        .put("retrieval_elapsed_ms", it.retrievalElapsedMs)
                        .put(
                            "first_visible_text_elapsed_ms",
                            it.firstVisibleTextElapsedMs ?: JSONObject.NULL
                        )
                        .put("end_to_end_elapsed_ms", it.endToEndElapsedMs)
                } ?: JSONObject.NULL
            )
            save()
            println("Inference evidence: ${output.absolutePath}")
        }
        assertTrue(
            "Generation did not finish within $timeoutMs ms; partial evidence saved",
            finished
        )
        val turn = container.database.conversations().turns(id).last()
        assertTrue(
            "Unexpected completed app state: ${turn.status}",
            turn.status == "COMPLETE"
        )
        val evidence = Evidence.decode(turn.evidence)
        assertTrue(
            "Chat context exceeded its evidence source budget",
            evidence.size <= ResearchPrompt.MAX_ANSWER_SOURCES
        )
        requireNotNull(ModelSupport.fromStored(turn.modelSupport)) {
            "The completion did not return a valid response document: ${turn.error}"
        }
        assertTrue(
            "Response document punctuation leaked into the durable answer",
            turn.answer.none { it == '{' || it == '}' }
        )
        val performance = requireNotNull(container.work.lastPerformanceSnapshot)
        assertTrue(performance.retrievalElapsedMs >= 0)
        assertTrue(performance.endToEndElapsedMs > 0)
        assertEquals(
            "My next question stays here",
            container.database.conversations().find(id)?.draft
        )
        foreground.close()
    }

    private fun decodedArgument(args: android.os.Bundle, name: String): String? =
        args.getString("${name}Base64")?.let { encoded ->
            String(
                Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8
            )
        } ?: args.getString(name)

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun saveReport(output: File, report: JSONObject) {
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
