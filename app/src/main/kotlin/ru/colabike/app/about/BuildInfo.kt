package ru.colabike.app.about

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import ru.colabike.app.BuildConfig

/**
 * What the "About" screen says of the build: the version and the build number, in one short line.
 * The version of the API contract the build was made for is kept for diagnostics, and is not shown
 * to people. Taken from [BuildConfig]; the screenshots of the screen put fixed values here, so that
 * raising the version does not change a reference picture.
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
