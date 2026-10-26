package dev.blobs.app

import android.content.Context
import android.os.Build
import dev.blobs.app.inference.inferenceThreads
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * The identity every capture has to carry, written into the report being built. `goal.md` bars a speed
 * claim that does not name the built artifact, the device, and the configuration, so these keys come
 * from one writer instead of per-test copies that can silently disagree. Android's `JSONObject` has no
 * put-all, so this is an extension on the target rather than a merged value.
 */
internal fun JSONObject.putReportHeader(context: Context, runId: String): JSONObject = apply {
    put("format", 1)
    put("run_id", runId)
    put("application_id", context.packageName)
    put("installed_apk_sha256", installedApkSha256(context))
    put("fingerprint", Build.FINGERPRINT)
    put("api", Build.VERSION.SDK_INT)
    put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
    put("device_acceptance", DEVICE_ACCEPTANCE_UNVERIFIED)
    put("inference_threads", inferenceThreads(Runtime.getRuntime().availableProcessors()))
}

/** Hashed from the APK the instrumentation actually runs, which is not the file on the build host. */
internal fun installedApkSha256(context: Context): String {
    val bytes = File(context.applicationInfo.sourceDir).readBytes()
    return MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

/**
 * No phone-class device has run these suites yet, so a capture never claims device acceptance.
 * A device change rewrites this value in the same commit that records the run.
 */
internal const val DEVICE_ACCEPTANCE_UNVERIFIED = "unverified"
