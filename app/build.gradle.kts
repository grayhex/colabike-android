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

// An override of the style of the map under a route (an `https` MapLibre style URL), given by the
// owner as -Pcolabike.mapStyleUrl=... or in gradle.properties. Empty (the usual case): the built-in
// OpenFreeMap style, so that a build without any property shows a map (docs/adr/0024).
val mapStyleUrl = providers.gradleProperty("colabike.mapStyleUrl").orElse("").get().trim()

// The key of the owner's mobile app in the Yandex Maps developer console
// (-Pcolabike.yandexMapsApiKey=...
// or gradle.properties; never committed). A key for the JavaScript API of the site is another
// product and does not work in the Android SDK. Empty: this build offers no Yandex map and the
// setting for it is not shown (docs/adr/0025-yandex-maps.md).
val yandexMapsApiKey = providers.gradleProperty("colabike.yandexMapsApiKey").orElse("").get().trim()

// The version of the API contract this build was generated from, for support: the version in the
// snapshot and the start of its checksum (api/openapi.json.sha256 pins the snapshot).
val contractVersion: String = run {
    val contract = rootProject.file("api/openapi.json")
    val version =
        (groovy.json.JsonSlurper().parse(contract) as Map<*, *>).let {
            (it["info"] as Map<*, *>)["version"]
        }
    val checksum = rootProject.file("api/openapi.json.sha256").readText().trim().take(8)
    "$version ($checksum)"
}

// The project of this app at RuStore Push, given by the owner when the app is registered there
// (-Pcolabike.rustore.projectId=... or gradle.properties; never committed). Empty: the build has
// no push provider and push is simply not offered (docs/adr/0017).
val rustoreProjectId =
    providers.gradleProperty("colabike.rustore.projectId").orElse("").get().trim()

check(rustoreProjectId.isEmpty() || Regex("[A-Za-z0-9._-]{1,100}").matches(rustoreProjectId)) {
    "colabike.rustore.projectId must be a project id from the RuStore console"
}

check(yandexMapsApiKey.isEmpty() || Regex("[A-Za-z0-9-]{8,80}").matches(yandexMapsApiKey)) {
    "colabike.yandexMapsApiKey must be the key of a mobile app from the Yandex Maps console"
}

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
        // A new build over an installed one needs a bigger versionCode and the same signing key
        // (docs/install.md). The owner sets -Pcolabike.versionCode=N for a build to hand out.
        versionCode =
            providers.gradleProperty("colabike.versionCode").map { it.toInt() }.orElse(1).get()
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SITE_URL", "\"$siteUrl\"")
        buildConfigField("String", "NATIVE_AUTH_RETURN_URL", "\"$siteUrl/app/auth\"")
        buildConfigField("String", "MAP_STYLE_URL", "\"$mapStyleUrl\"")
        buildConfigField("String", "YANDEX_MAPS_API_KEY", "\"$yandexMapsApiKey\"")
        buildConfigField("String", "RUSTORE_PROJECT_ID", "\"$rustoreProjectId\"")
        buildConfigField("String", "CONTRACT_VERSION", "\"$contractVersion\"")
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

    // The app is in Russian only; the libraries bring a dozen translations of their own that
    // nobody here can read. The messenger's SDK has no Russian, so values-ru carries it.
    androidResources { localeFilters += listOf("ru") }

    // The backend's shared notification examples are pinned once, in core:network; the push tests
    // of the app read the same files (docs/adr/0010).
    sourceSets
        .getByName("test")
        .resources
        .srcDir(rootProject.file("core/network/src/test/resources/contracts"))

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
    // The Yandex map, offered when the owner gives a key. Its POM names Google Play Services
    // (location, Play Integrity) which the app must not carry (AGENTS.md): the SDK reaches them
    // only to locate the phone and to attest, neither of which a map of a route asks for.
    implementation(libs.yandex.mapkit) {
        exclude(group = "com.google.android.gms")
        exclude(group = "com.google.android.play")
    }
    implementation(libs.stream.compose)
    // The SDK reports its own crashes through Tracer when the library is there and falls back to a
    // stub when it is not (it looks the class up by name): without it nothing of the SDK's crashes
    // leaves the phone (docs/adr/0017).
    implementation(libs.rustore.push) { exclude(group = "ru.ok.tracer") }

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

// The app must run where Google Play Services are absent (RuStore, AOSP images; AGENTS.md): no
// Play Services, Firebase, Play Billing, Play Integrity or in-app updates may arrive through any
// library, directly or as a transitive dependency. Checked on what ships (the release runtime
// classpath) and on what the tests run (debug).
val checkNoPlayServices by tasks.registering {
    group = "verification"
    description = "Fails when a Google Play / Firebase / push-vendor library is on the classpath."
    val classpaths =
        listOf("releaseRuntimeClasspath", "debugRuntimeClasspath").map {
            configurations.named(it)
        }
    val banned =
        listOf(
            Regex("""com\.google\.android\.gms:.*"""),
            Regex("""com\.google\.firebase:.*"""),
            Regex("""com\.android\.billingclient:.*"""),
            Regex("""com\.google\.android\.play:.*"""),
            Regex("""com\.google\.android\.datatransport:.*"""),
            Regex("""io\.getstream:stream-android-push-(firebase|huawei|xiaomi)\b.*"""),
        )
    doLast {
        val found =
            classpaths
                .flatMap { configuration ->
                    configuration.get().incoming.resolutionResult.allComponents.mapNotNull {
                        val id = it.moduleVersion?.let { m -> "${m.group}:${m.name}:${m.version}" }
                        id?.takeIf { _ -> banned.any { rule -> rule.matches(id) } }
                    }
                }
                .toSortedSet()
        check(found.isEmpty()) {
            "Libraries that need Google Play Services or a push vendor are on the classpath " +
                "(AGENTS.md forbids them): $found"
        }
    }
}
