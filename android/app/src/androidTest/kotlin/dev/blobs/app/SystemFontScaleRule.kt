package dev.blobs.app

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.rules.ExternalResource

class SystemFontScaleRule : ExternalResource() {
    private var previous: Float? = null

    override fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = Settings.System.getString(context.contentResolver, Settings.System.FONT_SCALE)
            ?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    }

    override fun after() = apply(previous)

    fun set(scale: Float) {
        require(scale.isFinite() && scale > 0f)
        apply(scale)
    }

    private fun apply(scale: Float?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = scale?.let { "settings put system font_scale $it" }
            ?: "settings delete system font_scale"
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
        val expected = scale ?: 1f
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (abs(instrumentation.targetContext.resources.configuration.fontScale - expected) >
            .001f
        ) {
            check(SystemClock.uptimeMillis() < deadline) {
                "Android did not apply font scale $expected"
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(10)
        }
        instrumentation.waitForIdleSync()
    }
}
