package ru.colabike.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The raw palette of the site (cola app/styles/tokens.css): Tailwind grays, the ColaBike
// yellow. Components never use it directly; they use MaterialTheme.colorScheme and ColaColors.
internal object Palette {
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
    val Gray50 = Color(0xFFF9FAFB)
    val Gray100 = Color(0xFFF3F4F6)
    val Gray200 = Color(0xFFE5E7EB)
    val Gray300 = Color(0xFFD1D5DB)
    val Gray400 = Color(0xFF9CA3AF)
    val Gray500 = Color(0xFF6B7280)
    val Gray600 = Color(0xFF4B5563)
    val Gray700 = Color(0xFF374151)
    val Gray800 = Color(0xFF1F2937)
    val Gray850 = Color(0xFF18212F)
    val Gray900 = Color(0xFF111827)
    val Gray925 = Color(0xFF0E1320)
    val Gray950 = Color(0xFF0B0F19)
    val Gray975 = Color(0xFF070A12)
    val Accent = Color(0xFFF3B51B)
    val AccentSoftLight = Color(0xFFFDF3DB) // 16% accent on white
    val AccentTextLight = Color(0xFF86640F) // 55% accent toward black: readable on light
    val AccentSoftDark = Color(0xFF302A19) // 16% accent on gray-950
    val AccentTextDark = Color(0xFFF5C03D) // 85% accent toward white: readable on dark
    val Red50 = Color(0xFFFEF2F2)
    val Red400 = Color(0xFFF87171)
    val Red600 = Color(0xFFDC2626)
    val Red700 = Color(0xFFB91C1C)
    val RedSoftDark = Color(0xFF3A1A20)
    val Green400 = Color(0xFF4ADE80)
    val Green700 = Color(0xFF15803D)
    val Amber400 = Color(0xFFFBBF24)
    val Amber700 = Color(0xFFB45309)
}

/**
 * The main action is black on light and white on dark, as on the site; the ColaBike yellow is the
 * tertiary accent (highlights, selection), never body text on a light surface.
 */
internal val LightColors: ColorScheme =
    lightColorScheme(
        primary = Palette.Black,
        onPrimary = Palette.White,
        primaryContainer = Palette.Gray100,
        onPrimaryContainer = Palette.Gray900,
        inversePrimary = Palette.White,
        secondary = Palette.Gray700,
        onSecondary = Palette.White,
        secondaryContainer = Palette.Gray100,
        onSecondaryContainer = Palette.Gray900,
        tertiary = Palette.Accent,
        onTertiary = Palette.Black,
        tertiaryContainer = Palette.AccentSoftLight,
        onTertiaryContainer = Palette.AccentTextLight,
        background = Palette.White,
        onBackground = Palette.Gray900,
        surface = Palette.White,
        onSurface = Palette.Gray900,
        surfaceVariant = Palette.Gray100,
        onSurfaceVariant = Palette.Gray600,
        surfaceTint = Palette.Black,
        inverseSurface = Palette.Gray950,
        inverseOnSurface = Palette.White,
        error = Palette.Red600,
        onError = Palette.White,
        errorContainer = Palette.Red50,
        onErrorContainer = Palette.Red700,
        outline = Palette.Gray300,
        outlineVariant = Palette.Gray200,
        scrim = Palette.Black,
        surfaceBright = Palette.White,
        surfaceDim = Palette.Gray100,
        surfaceContainerLowest = Palette.White,
        surfaceContainerLow = Palette.Gray50,
        surfaceContainer = Palette.Gray50,
        surfaceContainerHigh = Palette.Gray100,
        surfaceContainerHighest = Palette.Gray200,
    )

internal val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Palette.White,
        onPrimary = Palette.Black,
        primaryContainer = Palette.Gray800,
        onPrimaryContainer = Palette.White,
        inversePrimary = Palette.Black,
        secondary = Palette.Gray300,
        onSecondary = Palette.Gray900,
        secondaryContainer = Palette.Gray800,
        onSecondaryContainer = Palette.Gray100,
        tertiary = Palette.Accent,
        onTertiary = Palette.Black,
        tertiaryContainer = Palette.AccentSoftDark,
        onTertiaryContainer = Palette.AccentTextDark,
        background = Palette.Gray950,
        onBackground = Palette.White,
        surface = Palette.Gray950,
        onSurface = Palette.White,
        surfaceVariant = Palette.Gray900,
        onSurfaceVariant = Palette.Gray400,
        surfaceTint = Palette.White,
        inverseSurface = Palette.White,
        inverseOnSurface = Palette.Gray950,
        error = Palette.Red400,
        onError = Palette.Black,
        errorContainer = Palette.RedSoftDark,
        onErrorContainer = Palette.Red400,
        outline = Palette.Gray700,
        outlineVariant = Palette.Gray800,
        scrim = Palette.Black,
        surfaceBright = Palette.Gray850,
        surfaceDim = Palette.Gray950,
        surfaceContainerLowest = Palette.Gray975,
        surfaceContainerLow = Palette.Gray925,
        surfaceContainer = Palette.Gray900,
        surfaceContainerHigh = Palette.Gray850,
        surfaceContainerHighest = Palette.Gray800,
    )

/** Brand roles Material has no slot for. */
@Immutable
data class ColaColors(
    /** A given like (filled heart). */
    val like: Color,
    /** The yellow as readable text or icon on the page background. */
    val accentText: Color,
    val success: Color,
    val warning: Color,
)

internal val LightColaColors =
    ColaColors(
        like = Palette.Red600,
        accentText = Palette.AccentTextLight,
        success = Palette.Green700,
        warning = Palette.Amber700,
    )

internal val DarkColaColors =
    ColaColors(
        like = Palette.Red400,
        accentText = Palette.AccentTextDark,
        success = Palette.Green400,
        warning = Palette.Amber400,
    )

val LocalColaColors = staticCompositionLocalOf { LightColaColors }
