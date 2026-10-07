package ru.colabike.app.about

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import ru.colabike.app.BuildConfig

/**
 * What the "About" screen says of the build: the version, the build number and the version of the
 * API contract. Taken from [BuildConfig]; the screenshots of the screen put fixed values here, so
 * that raising the version does not change a reference picture.
 */
@Immutable
data class BuildInfo(val versionName: String, val versionCode: Int, val contractVersion: String) {
    companion object {
        val Current =
            BuildInfo(
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.CONTRACT_VERSION,
            )
    }
}

val LocalBuildInfo = staticCompositionLocalOf { BuildInfo.Current }
