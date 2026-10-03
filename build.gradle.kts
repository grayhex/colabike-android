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

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", ".claude/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", ".claude/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}

// The one command for people, agents and CI (AGENTS.md): formatting, the API
// contract snapshot, Android Lint, unit and screenshot tests, the debug build.
tasks.register("verify") {
    group = "verification"
    description = "Format check, contract checksum, lint, unit and screenshot tests, debug APK."
    dependsOn(
        "spotlessCheck",
        ":core:model:check",
        ":core:network:check",
        ":core:auth:lintDebug",
        ":core:auth:testDebugUnitTest",
        ":core:designsystem:lintDebug",
        ":core:designsystem:verifyRoborazziDebug",
        ":app:lintDebug",
        ":app:verifyRoborazziDebug",
        ":app:assembleDebug",
    )
}
