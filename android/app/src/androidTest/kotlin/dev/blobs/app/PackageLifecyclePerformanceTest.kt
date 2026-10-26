package dev.blobs.app

import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.packages.CatalogRefreshOutcome
import dev.blobs.app.data.packages.CatalogSource
import dev.blobs.app.ui.AppViewModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@ManualCheck
class PackageLifecyclePerformanceTest {
    @Test
    fun refreshesPublishedLibrariesWithoutDownloadingPackages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as BlobsApplication).container
        withTimeout(15_000) { container.work.state.first { it.ready } }
        val installed = container.database.packages().all()
        val downloads = File(context.filesDir, "package-downloads")
            .listFiles().orEmpty().map { it.name to it.length() }.toSet()
        val outcome = container.catalog.refreshNow()
        assertTrue(
            outcome in setOf(CatalogRefreshOutcome.UPDATED, CatalogRefreshOutcome.UNCHANGED)
        )
        assertTrue(
            container.catalog.state.value.source in setOf(CatalogSource.REMOTE, CatalogSource.CACHE)
        )
        val ids = container.catalog.additionalLibraries.map { it.manifest.id }.toSet()
        assertTrue(
            ids.containsAll(
                setOf(
                    "howto-repair",
                    "howto-practical",
                    "wikipedia-top1m-mini",
                    "wikisource-en-history-p000",
                    "wikisource-en-history-p003",
                    "nairobi-health-places"
                )
            )
        )
        assertEquals(installed, container.database.packages().all())
        assertEquals(
            downloads,
            File(context.filesDir, "package-downloads")
                .listFiles().orEmpty().map { it.name to it.length() }.toSet()
        )
        assertFalse(container.work.state.value.busy)
    }

    @Test
    fun downloadsAndInstallsPinnedCatalogPackage() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val packageId = requireNotNull(args.getString("packageId"))
        require(packageId.matches(Regex("[a-z0-9-]{1,100}")))
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val container = (context.applicationContext as BlobsApplication).container
            withTimeout(15_000) { container.work.state.first { it.ready } }
            container.preferences.welcome()

            val catalogStart = SystemClock.elapsedRealtime()
            val refresh = container.catalog.refreshNow()
            val catalogRefreshMs = SystemClock.elapsedRealtime() - catalogStart
            assertTrue(
                refresh in setOf(CatalogRefreshOutcome.UPDATED, CatalogRefreshOutcome.UNCHANGED)
            )
            assertTrue(
                container.catalog.state.value.source in
                    setOf(CatalogSource.REMOTE, CatalogSource.CACHE)
            )
            val asset = container.catalog.state.value.packages.single {
                it.manifest.id == packageId
            }
            assertFalse(
                container.database.packages().all().any {
                    it.packageId == asset.manifest.id && it.version == asset.manifest.version
                }
            )

            val reportFile = File(context.filesDir, "package-lifecycle-$runId.json")
            check(!reportFile.exists())
            val report = JSONObject().apply {
                put("format", 1)
                put("complete", false)
                put(
                    "scope",
                    "Blobs in-app package download, verification, install, and activation; no model answer generation."
                )
                put("run_id", runId)
                put("package_id", asset.manifest.id)
                put("version", asset.manifest.version)
                put("kind", asset.manifest.kind)
                put("role", asset.role.name.lowercase())
                put("catalog_source", container.catalog.state.value.source.name.lowercase())
                put("catalog_refresh_ms", catalogRefreshMs)
                put("download_url", asset.url)
                put("download_bytes", asset.downloadBytes)
                put("download_sha256", asset.downloadSha256)
                put("installed_bytes", asset.manifest.bytes)
                put("installed_sha256", asset.manifest.sha256)
                put("app_package", context.packageName)
                put(
                    "app_version",
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                )
                put("app_apk_sha256", sha256(File(context.applicationInfo.sourceDir)))
                put(
                    "test_apk_sha256",
                    sha256(
                        File(
                            InstrumentationRegistry.getInstrumentation()
                                .context.applicationInfo.sourceDir
                        )
                    )
                )
                put("device_fingerprint", Build.FINGERPRINT)
                put("device_model", Build.MODEL)
                put("api", Build.VERSION.SDK_INT)
            }

            var viewModel: AppViewModel? = null
            scenario.onActivity { activity ->
                viewModel = ViewModelProvider(activity)[AppViewModel::class.java]
            }
            val totalStart = SystemClock.elapsedRealtime()
            requireNotNull(viewModel).downloadPackage(asset)
            withTimeout(15 * 60_000L) {
                container.work.state.first {
                    it.busy &&
                        it.inAppDownload &&
                        !it.downloading &&
                        it.packageKind == asset.manifest.kind
                }
            }
            val installStartMs = SystemClock.elapsedRealtime()
            val finished = withTimeout(15 * 60_000L) {
                container.work.state.first { !it.busy && (it.importComplete || it.notice != null) }
            }
            val totalMs = SystemClock.elapsedRealtime() - totalStart
            assertTrue(
                "package operation failed: notice=${finished.notice}",
                finished.importComplete
            )

            val installed = container.database.packages().all().single {
                it.packageId == asset.manifest.id && it.version == asset.manifest.version
            }
            assertEquals(asset.manifest.sha256, installed.sha256)
            assertEquals(asset.manifest.bytes, installed.bytes)
            report.put("download_and_inspect_ms", installStartMs - totalStart)
            report.put(
                "install_validate_activate_ms",
                SystemClock.elapsedRealtime() - installStartMs
            )
            report.put("end_to_end_ms", totalMs)
            report.put("complete", true)
            reportFile.writeText(report.toString(2), Charsets.UTF_8)
            println("Package performance evidence: ${reportFile.absolutePath}")
        } finally {
            scenario.close()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
