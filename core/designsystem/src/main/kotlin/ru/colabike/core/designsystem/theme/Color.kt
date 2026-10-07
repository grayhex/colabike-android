package ru.colabike.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Graphite and lime (docs/design/graphite-lime.md, ADR 0023). The dark values are the approved
// tokens of 06.10.2026; the light ones are the same structure in daylight, checked for contrast
// (4.5:1 text, 3:1 icons). Components never use the palette directly; they use
// MaterialTheme.colorScheme and ColaColors.
internal object Palette {
    // Approved, dark.
    val Graphite = Color(0xFF0B0E11) // background
    val GraphiteRaised = Color(0xFF171B20) // cards and fields
    val Hairline = Color(0xFF2A3037) // dividers, card edges
    val Snow = Color(0xFFF3F5F7) // text
    val SnowMuted = Color(0xFFA1ABB7) // secondary text
    val Lime = Color(0xFFD2FF00) // the one accent: main action, choice, active tab

    // Dark, derived: steps between the approved surfaces and a lime wash for the active tab.
    val GraphiteStep = Color(0xFF11151A)
    val GraphiteHigh = Color(0xFF1E2329)
    val GraphiteHighest = Color(0xFF252B32)
    val LimeHalo = Color(0xFF2B350E) // lime 16 % over graphite: the soft plate under an active icon
    val OutlineDark = Color(0xFF566170) // text field border, 3:1 on the canvas
    val CoralDark = Color(0xFFFF8A80) // error: readable on graphite
    val CoralContainerDark = Color(0xFF3A1614)
    val CoralOnContainerDark = Color(0xFFFFD9D5)
    val LikeDark = Color(0xFFFF6F7D)
    val AmberDark = Color(0xFFFFC857)
    val OxbloodDark = Color(0xFF7F1D1D) // filled destructive actions

    // Light: the same structure on a cool paper.
    val Paper = Color(0xFFF4F6F8)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperStep = Color(0xFFF8F9FB)
    val PaperHigh = Color(0xFFE9EDF1)
    val PaperHighest = Color(0xFFDDE2E8)
    val Ink = Color(0xFF0B0E11)
    val InkMuted = Color(0xFF566170)
    val Olive =
        Color(0xFF486500) // lime deepened until it reads as text and as a fill for white text
    val OutlineLight = Color(0xFF8A94A1)
    val HairlineLight = Color(0xFFDDE2E8)
    val CoralLight = Color(0xFFB3261E)
    val CoralContainerLight = Color(0xFFFBDAD6)
    val CoralOnContainerLight = Color(0xFF5B130F)
    val LikeLight = Color(0xFFD6334A)
    val AmberLight = Color(0xFF8A5A00)
    val GreenLight = Color(0xFF3F6B00)

    // The ColaBike yellow survives only as the brand mark (logo tile, launcher, splash).
    val Brand = Color(0xFFF3B51B)
    val BrandHaloLight = Color(0xFFFBEBC4)
    val BrandInkLight = Color(0xFF6B4E08)
    val BrandHaloDark = Color(0xFF392E14)
    val BrandInkDark = Color(0xFFF5C03D)
}

/**
 * Dark is the approved design as is. Lime is the only point colour (main action, choice, active
 * tab), never a surface of a page; cards are one step above the canvas and edged by a hairline.
 */
internal val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Palette.Lime,
        onPrimary = Palette.Graphite,
        primaryContainer = Palette.LimeHalo,
        onPrimaryContainer = Palette.Lime,
        inversePrimary = Palette.Olive,
        secondary = Palette.SnowMuted,
        onSecondary = Palette.Graphite,
        secondaryContainer = Palette.GraphiteHigh,
        onSecondaryContainer = Palette.Snow,
        tertiary = Palette.Brand,
        onTertiary = Palette.Graphite,
        tertiaryContainer = Palette.BrandHaloDark,
        onTertiaryContainer = Palette.BrandInkDark,
        background = Palette.Graphite,
        onBackground = Palette.Snow,
        surface = Palette.Graphite,
        onSurface = Palette.Snow,
        surfaceVariant = Palette.GraphiteHigh,
        onSurfaceVariant = Palette.SnowMuted,
        surfaceTint = Palette.Lime,
        inverseSurface = Palette.Snow,
        inverseOnSurface = Palette.Graphite,
        error = Palette.CoralDark,
        onError = Palette.Graphite,
        errorContainer = Palette.CoralContainerDark,
        onErrorContainer = Palette.CoralOnContainerDark,
        outline = Palette.OutlineDark,
        outlineVariant = Palette.Hairline,
        scrim = Color.Black,
        surfaceBright = Palette.GraphiteHighest,
        surfaceDim = Palette.Graphite,
        surfaceContainerLowest = Palette.Graphite,
        surfaceContainerLow = Palette.GraphiteStep,
        surfaceContainer = Palette.GraphiteRaised,
        surfaceContainerHigh = Palette.GraphiteHigh,
        surfaceContainerHighest = Palette.GraphiteHighest,
    )

/**
 * The approved design is dark; daylight keeps its structure. Lime does not read on white, so text
 * and fills that need weight use olive (lime deepened), and lime stays where it is a plate behind
 * dark ink: the soft plate of the active tab.
 */
internal val LightColors: ColorScheme =
    lightColorScheme(
        primary = Palette.Olive,
        onPrimary = Palette.PaperRaised,
        primaryContainer = Palette.Lime,
        onPrimaryContainer = Palette.Ink,
        inversePrimary = Palette.Lime,
        secondary = Palette.InkMuted,
        onSecondary = Palette.PaperRaised,
        secondaryContainer = Palette.PaperHigh,
        onSecondaryContainer = Palette.Ink,
        tertiary = Palette.Brand,
        onTertiary = Palette.Graphite,
        tertiaryContainer = Palette.BrandHaloLight,
        onTertiaryContainer = Palette.BrandInkLight,
        background = Palette.Paper,
        onBackground = Palette.Ink,
        surface = Palette.Paper,
        onSurface = Palette.Ink,
        surfaceVariant = Palette.PaperHigh,
        onSurfaceVariant = Palette.InkMuted,
        surfaceTint = Palette.Olive,
        inverseSurface = Palette.Ink,
        inverseOnSurface = Palette.Snow,
        error = Palette.CoralLight,
        onError = Palette.PaperRaised,
        errorContainer = Palette.CoralContainerLight,
        onErrorContainer = Palette.CoralOnContainerLight,
        outline = Palette.OutlineLight,
        outlineVariant = Palette.HairlineLight,
        scrim = Color.Black,
        surfaceBright = Palette.PaperRaised,
        surfaceDim = Palette.PaperHighest,
        surfaceContainerLowest = Palette.PaperRaised,
        surfaceContainerLow = Palette.PaperStep,
        surfaceContainer = Palette.PaperRaised,
        surfaceContainerHigh = Palette.PaperHigh,
        surfaceContainerHighest = Palette.PaperHighest,
    )

/** Brand and status roles Material has no slot for. */
@Immutable
data class ColaColors(
    /** A given like (filled heart). */
    val like: Color,
    val success: Color,
    val warning: Color,
    /** The ColaBike yellow: the logo tile only, never a surface or text. */
    val brand: Color,
    val onBrand: Color,
    /** Filled destructive actions. */
    val destructive: Color,
    val onDestructive: Color,
)

internal val LightColaColors =
    ColaColors(
        like = Palette.LikeLight,
        success = Palette.GreenLight,
        warning = Palette.AmberLight,
        brand = Palette.Brand,
        onBrand = Palette.Graphite,
        destructive = Palette.CoralLight,
        onDestructive = Palette.PaperRaised,
    )

internal val DarkColaColors =
    ColaColors(
        like = Palette.LikeDark,
        success = Palette.Lime,
        warning = Palette.AmberDark,
        brand = Palette.Brand,
        onBrand = Palette.Graphite,
        destructive = Palette.OxbloodDark,
        onDestructive = Palette.CoralOnContainerDark,
    )

val LocalColaColors = staticCompositionLocalOf { LightColaColors }
