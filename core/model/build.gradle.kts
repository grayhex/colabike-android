import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// App and domain models: plain Kotlin, no Android and no API DTOs.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get())) } }

lint {
    warningsAsErrors = true
    abortOnError = true
}

dependencies {
    // Flow types are part of the repository interfaces.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
