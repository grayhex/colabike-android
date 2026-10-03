package ru.colabike.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The ColaBike theme on Material 3: the site's palette, type and radii, Expressive motion from
 * [ColaMotion]. Branded colours by default; [dynamicColor] is an explicit opt-in (DESIGN.md).
 */
@Composable
fun ColaBikeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            // minSdk 31: dynamic colour always exists, no version check.
            dynamicColor ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> DarkColors
            else -> LightColors
        }
    val reducedMotion =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    CompositionLocalProvider(
        LocalColaColors provides if (darkTheme) DarkColaColors else LightColaColors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ColaShapes,
            typography = ColaTypography,
            content = content,
        )
    }
}

/** True when the system asks to remove animations: skeletons stop shimmering, nothing loops. */
val LocalReducedMotion = staticCompositionLocalOf { false }

object ColaTheme {
    val colors: ColaColors
        @Composable @ReadOnlyComposable get() = LocalColaColors.current
}
