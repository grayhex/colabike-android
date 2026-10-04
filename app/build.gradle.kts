// The shell: one activity, Navigation 3, adaptive layouts, screens of the vertical slices.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// The site the app talks to. There is no staging yet: debug and release both use production,
// tests and previews use fakes (docs/architecture.md, "Environments").
val siteUrl = "https://colabike.ru"

// The style of the map under a route (an `https` MapLibre style URL), chosen by the owner and given
// as -Pcolabike.mapStyleUrl=... or in gradle.properties. Empty: the route is drawn on a plain
// background and the app makes no tile request at all (docs/adr/0009-route-map-and-analysis.md).
val mapStyleUrl = providers.gradleProperty("colabike.mapStyleUrl").orElse("").get().trim()

check(
    mapStyleUrl.isEmpty() ||
        (mapStyleUrl.startsWith("https://") && '"' !in mapStyleUrl && '\\' !in mapStyleUrl)
) {
    "colabike.mapStyleUrl must be an https URL without quotes"
}

android {
    namespace = "ru.colabike.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ru.colabike.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SITE_URL", "\"$siteUrl\"")
        buildConfigField("String", "NATIVE_AUTH_RETURN_URL", "\"$siteUrl/app/auth\"")
        buildConfigField("String", "MAP_STYLE_URL", "\"$mapStyleUrl\"")
        // The Yandex ID button waits for cola#324 (App Link and NATIVE_AUTH_RETURN_URL on the
        // site): ./gradlew assembleDebug -Pcolabike.yandexSignIn=true to try it earlier.
        buildConfigField(
            "boolean",
            "YANDEX_SIGN_IN",
            providers.gradleProperty("colabike.yandexSignIn").orElse("false").get(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Release signing lives outside Git (docs/architecture.md, "Release and RuStore").
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:designsystem"))
    implementation(libs.maplibre.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
