plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.ktlint)
}

tasks.register("verify") {
    group = "verification"
    description = "Check formatting, lint, unit tests, and both application build types."
    dependsOn(
        "ktlintCheck",
        ":app:ktlintCheck",
        ":app:lintDebug",
        ":app:lintRelease",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
        ":app:assembleRelease",
        ":app:assembleDebugAndroidTest"
    )
}

dependencyLocking { lockAllConfigurations() }
