package ru.colabike.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Twilight Stillness (docs/design/twilight-stillness.md). The dark values are the reference
// DESIGN.md verbatim; the light ones are our adaptation, derived from the same hues and checked
// for contrast (4.5:1 text, 3:1 icons and field borders). Components never use the palette
// directly; they use MaterialTheme.colorScheme and ColaColors.
internal object Palette {
    // Reference, dark.
    val Charcoal = Color(0xFF151413) // background
    val CharcoalRaised = Color(0xFF1F1E1D) // surface (cards)
    val CharcoalHigh = Color(0xFF2A2927) // surface-alt, and the hairline border
    val Parchment = Color(0xFFEAE6DB) // on-surface: warm, never pure white
    val ParchmentMuted = Color(0xFF9A9893) // on-surface-variant
    val Sand = Color(0xFFC9BBA5) // primary: the single warm point of focus
    val Sage = Color(0xFF8C9A8D) // accent: a rare second categorical colour
    val Oxblood = Color(0xFF7F1D1D) // destructive

    // Dark, derived: tints of a point colour at 16-20 % over the canvas, as the reference
    // "tinted icon halo" recipe, and the steps between the reference surfaces.
    val CharcoalStep = Color(0xFF1A1918)
    val CharcoalHighest = Color(0xFF34322F)
    val SandHalo = Color(0xFF393530) // Sand 20 % over Charcoal
    val SageHalo = Color(0xFF2D2F2B) // Sage 20 % over Charcoal
    val BrandHaloDark = Color(0xFF392E14) // brand yellow 16 % over Charcoal
    val OutlineDark = Color(0xFF6B6964) // text field border, 3:1 on the canvas
    val RoseDark = Color(0xFFE5958B) // error and like: readable on the canvas
    val RoseOnOxblood = Color(0xFFF7DDD9)
    val PeachDark = Color(0xFFD9B89A) // reference illus-glow-peach: warnings

    // Light, our adaptation: warm paper instead of parchment on charcoal.
    val Paper = Color(0xFFF6F2EA)
    val PaperRaised = Color(0xFFFBF9F4)
    val PaperLowest = Color(0xFFFDFBF7)
    val PaperStep = Color(0xFFF9F6EF)
    val PaperHigh = Color(0xFFECE6DA)
    val PaperHighest = Color(0xFFE2DBCD)
    val Ink = Color(0xFF1F1E1D)
    val InkMuted = Color(0xFF605C55)
    val Umber = Color(0xFF5F5039) // Sand deepened until it reads as text and icons on paper
    val SandPale = Color(0xFFE5DBC6)
    val SandInk = Color(0xFF3B3122)
    val SageDeep = Color(0xFF566557)
    val SagePale = Color(0xFFDCE3DA)
    val SageInk = Color(0xFF2F3A31)
    val OutlineLight = Color(0xFF8C877C)
    val HairlineLight = Color(0xFFDDD6C8)
    val RoseLight = Color(0xFFA33B32)
    val RosePale = Color(0xFFF6DAD6)
    val RoseInk = Color(0xFF5B1A14)
    val LikeLight = Color(0xFFB3423A)
    val AmberLight = Color(0xFF8A5A1F)
    val OxbloodLight = Color(0xFF8E2B24)

    // The ColaBike yellow survives only as the brand mark (logo tile, launcher, splash).
    val Brand = Color(0xFFF3B51B)
    val BrandHaloLight = Color(0xFFFBEBC4)
    val BrandInkLight = Color(0xFF6B4E08)
    val BrandInkDark = Color(0xFFF5C03D)
}

/**
 * Dark is the reference as is. Sand is the only warm point (primary action, selection, focus),
 * never a surface; sage is secondary and rare.
 */
internal val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Palette.Sand,
        onPrimary = Palette.Charcoal,
        primaryContainer = Palette.SandHalo,
        onPrimaryContainer = Palette.Sand,
        inversePrimary = Palette.Umber,
        secondary = Palette.Sage,
        onSecondary = Palette.Charcoal,
        secondaryContainer = Palette.SageHalo,
        onSecondaryContainer = Palette.Sage,
        tertiary = Palette.Brand,
        onTertiary = Palette.Charcoal,
        tertiaryContainer = Palette.BrandHaloDark,
        onTertiaryContainer = Palette.BrandInkDark,
        background = Palette.Charcoal,
        onBackground = Palette.Parchment,
        surface = Palette.Charcoal,
        onSurface = Palette.Parchment,
        surfaceVariant = Palette.CharcoalHigh,
        onSurfaceVariant = Palette.ParchmentMuted,
        surfaceTint = Palette.Sand,
        inverseSurface = Palette.Parchment,
        inverseOnSurface = Palette.Charcoal,
        error = Palette.RoseDark,
        onError = Palette.Charcoal,
        errorContainer = Palette.Oxblood,
        onErrorContainer = Palette.RoseOnOxblood,
        outline = Palette.OutlineDark,
        outlineVariant = Palette.CharcoalHigh,
        scrim = Color.Black,
        surfaceBright = Palette.CharcoalHighest,
        surfaceDim = Palette.Charcoal,
        surfaceContainerLowest = Palette.Charcoal,
        surfaceContainerLow = Palette.CharcoalStep,
        surfaceContainer = Palette.CharcoalRaised,
        surfaceContainerHigh = Palette.CharcoalHigh,
        surfaceContainerHighest = Palette.CharcoalHighest,
    )

/**
 * The reference has no light theme. Same roles, same restraint: umber (sand deepened to be readable
 * on paper) is the point colour, cards are one step lighter than the canvas, hairlines instead of
 * shadows.
 */
internal val LightColors: ColorScheme =
    lightColorScheme(
        primary = Palette.Umber,
        onPrimary = Palette.Paper,
        primaryContainer = Palette.SandPale,
        onPrimaryContainer = Palette.SandInk,
        inversePrimary = Palette.Sand,
        secondary = Palette.SageDeep,
        onSecondary = Palette.Paper,
        secondaryContainer = Palette.SagePale,
        onSecondaryContainer = Palette.SageInk,
        tertiary = Palette.Brand,
        onTertiary = Palette.Charcoal,
        tertiaryContainer = Palette.BrandHaloLight,
        onTertiaryContainer = Palette.BrandInkLight,
        background = Palette.Paper,
        onBackground = Palette.Ink,
        surface = Palette.Paper,
        onSurface = Palette.Ink,
        surfaceVariant = Palette.PaperHigh,
        onSurfaceVariant = Palette.InkMuted,
        surfaceTint = Palette.Umber,
        inverseSurface = Palette.Ink,
        inverseOnSurface = Palette.Parchment,
        error = Palette.RoseLight,
        onError = Palette.PaperRaised,
        errorContainer = Palette.RosePale,
        onErrorContainer = Palette.RoseInk,
        outline = Palette.OutlineLight,
        outlineVariant = Palette.HairlineLight,
        scrim = Color.Black,
        surfaceBright = Palette.PaperRaised,
        surfaceDim = Palette.PaperHighest,
        surfaceContainerLowest = Palette.PaperLowest,
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
    /** Filled destructive actions (the reference "deep oxblood"). */
    val destructive: Color,
    val onDestructive: Color,
)

internal val LightColaColors =
    ColaColors(
        like = Palette.LikeLight,
        success = Palette.SageDeep,
        warning = Palette.AmberLight,
        brand = Palette.Brand,
        onBrand = Palette.Charcoal,
        destructive = Palette.OxbloodLight,
        onDestructive = Palette.PaperRaised,
    )

internal val DarkColaColors =
    ColaColors(
        like = Palette.RoseDark,
        success = Palette.Sage,
        warning = Palette.PeachDark,
        brand = Palette.Brand,
        onBrand = Palette.Charcoal,
        destructive = Palette.Oxblood,
        onDestructive = Palette.RoseOnOxblood,
    )

val LocalColaColors = staticCompositionLocalOf { LightColaColors }
