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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The ColaBike theme on Material 3, in the Twilight Stillness direction (docs/design): warm
 * charcoal or paper, one sand point colour, a serif for headings, hairlines instead of shadows,
 * Expressive motion from [ColaMotion]. Branded colours by default; [dynamicColor] is an explicit
 * opt-in (DESIGN.md).
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

/** Opacity of an aura: the reference sets its blobs at 5 %, barely a colour. */
private const val AuraAlpha = 0.06f

/**
 * The canvas behind a screen: the background colour and the reference's signature, two ambient
 * "auras" bleeding in from opposite corners (sand top right, sage bottom left) so the room has
 * weather. Drawn as radial gradients, not blurred blobs: cheap, and identical on every device.
 * Purely decorative: no semantics, and the auras are far too faint to tint content.
 */
@Composable
fun ColaCanvas(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val secondary = scheme.secondary
    Box(
        modifier =
            modifier.background(scheme.background).drawWithCache {
                val radius = size.maxDimension * 0.7f
                val top =
                    Brush.radialGradient(
                        listOf(primary.copy(alpha = AuraAlpha), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = radius,
                    )
                val bottom =
                    Brush.radialGradient(
                        listOf(secondary.copy(alpha = AuraAlpha), Color.Transparent),
                        center = Offset(0f, size.height),
                        radius = radius,
                    )
                onDrawBehind {
                    drawRect(top)
                    drawRect(bottom)
                }
            },
        content = content,
    )
}
