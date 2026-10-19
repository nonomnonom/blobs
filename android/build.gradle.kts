buildscript {
    val catalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        constraints {
            listOf(
                "tooling-commons-lang",
                "tooling-jose",
                "tooling-jdom",
                "tooling-bouncycastle-pkix",
                "bouncycastle-prov",
                "bouncycastle-util"
            ).forEach { alias -> add("classpath", catalog.findLibrary(alias).get()) }
        }
    }
}

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

ktlint { version.set(libs.versions.ktlint.engine.get()) }

dependencies {
    constraints {
        add("ktlint", libs.tooling.logback.classic)
        add("ktlint", libs.tooling.logback.core)
    }
}
