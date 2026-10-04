plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.openapi.generator) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

// Targets name the source folders instead of "**": targetExclude only subtracts files after the
// walk, so "**/*.kt" still read build/generated while openApiGenerate rewrote it ("Could not read
// path"). Modules live at <module>/src or <group>/<module>/src.
spotless {
    kotlin {
        target("*/src/**/*.kt", "*/*/src/**/*.kt")
        targetExclude(".claude/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "*/*/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}

// The one command for people, agents and CI (AGENTS.md): formatting, the API
// contract snapshot, Android Lint, no Play Services on the classpath, unit and screenshot tests,
// the debug build.
tasks.register("verify") {
    group = "verification"
    description =
        "Format check, contract checksum, lint, no Play Services, unit and screenshot tests, debug APK."
    dependsOn(
        "spotlessCheck",
        ":core:model:check",
        ":core:network:check",
        ":core:auth:lintDebug",
        ":core:auth:testDebugUnitTest",
        ":core:designsystem:lintDebug",
        ":core:designsystem:verifyRoborazziDebug",
        ":app:lintDebug",
        ":app:checkNoPlayServices",
        ":app:verifyRoborazziDebug",
        ":app:assembleDebug",
    )
}
