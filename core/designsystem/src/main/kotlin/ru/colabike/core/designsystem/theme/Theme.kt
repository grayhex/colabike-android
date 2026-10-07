package ru.colabike.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * The ColaBike theme on Material 3, in the graphite and lime direction (docs/design, ADR 0023):
 * graphite or cool paper, one lime point colour, Inter in four weights, moderate radii, hairlines
 * instead of shadows, motion from [ColaMotion]. Branded colours by default; [dynamicColor] is an
 * explicit opt-in (DESIGN.md).
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
        LocalColaTextStyles provides DefaultColaTextStyles,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ColaShapes,
            typography = ColaTypography,
        ) {
            // Text and icons default to the page's ink even outside a Surface; without this they
            // would be black, invisible on the dark canvas.
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}

/** True when the system asks to remove animations: skeletons stop shimmering, nothing loops. */
val LocalReducedMotion = staticCompositionLocalOf { false }

object ColaTheme {
    val colors: ColaColors
        @Composable @ReadOnlyComposable get() = LocalColaColors.current

    val textStyles: ColaTextStyles
        @Composable @ReadOnlyComposable get() = LocalColaTextStyles.current
}

/**
 * The canvas behind a screen: the background colour, flat. The approved design has no ambient light
 * and no gradients: depth comes from the step between the canvas and the cards, and from hairlines.
 */
@Composable
fun ColaCanvas(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background), content = content)
}
