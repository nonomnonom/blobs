package dev.blobs.app.data.packages

import org.junit.Assert.assertThrows
import org.junit.Test

class PackageManifestTest {
    @Test
    fun validationRejectsAnIdThatCouldEscapeThePackageDirectory() {
        val invalid = manifest().copy(id = "../outside")

        assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
    }

    @Test
    fun validationRejectsAPathSegmentInTheVersion() {
        val invalid = manifest().copy(version = "../outside")

        assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
    }

    private fun manifest() = PackageManifest(
        id = "starter-library",
        version = "2026.10",
        kind = "library",
        title = "Starter library",
        bytes = 1024,
        sha256 = "a".repeat(64),
        license = "CC0-1.0",
        origin = "https://example.test/starter"
    )
}
