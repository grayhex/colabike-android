package ru.colabike.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.colabike.core.designsystem.R

// Two voices, both with Cyrillic and both bundled: no font provider, which devices without Google
// services would not have. The reference pairs Lora with Outfit; Outfit has no Cyrillic glyphs
// (checked: 0 of 64 letters), so the sans voice is Source Sans 3 in Light, the reference's
// dominant body weight. One variable file per family, one entry per weight.
private fun lora(weight: FontWeight) =
    Font(
        resId = R.font.lora,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

private fun sourceSans(weight: FontWeight) =
    Font(
        resId = R.font.source_sans_3,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/** Headings and numerals. Regular only (the file spans 400-700): the reference forbids bold. */
val Lora = FontFamily(lora(FontWeight.Normal))

/** Body, labels, metadata. Light for reading, Regular for small text and controls. */
val SourceSans3 =
    FontFamily(
        sourceSans(FontWeight.Light),
        sourceSans(FontWeight.Normal),
        sourceSans(FontWeight.Medium),
    )

private fun serif(size: Int, line: Int, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = Lora,
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

private fun sans(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) =
    TextStyle(
        fontFamily = SourceSans3,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

/**
 * The Material scale on the reference's type tokens (display-md, headline-lg/md/sm, body, label).
 * Everything in sp: it scales with the system font. Weights stop at Medium (docs/design).
 */
internal val ColaTypography =
    Typography(
        displayLarge = serif(36, 44),
        displayMedium = serif(30, 36),
        displaySmall = serif(26, 32),
        headlineLarge = serif(24, 30, 0.24),
        headlineMedium = serif(20, 26, 0.2),
        headlineSmall = serif(18, 24, 0.18),
        titleLarge = serif(20, 26, 0.2),
        titleMedium = sans(16, 22),
        titleSmall = sans(14, 20),
        bodyLarge = sans(16, 24, FontWeight.Light, 0.16),
        bodyMedium = sans(14, 22, FontWeight.Light, 0.14),
        bodySmall = sans(13, 20, FontWeight.Normal, 0.13),
        labelLarge = sans(14, 20, FontWeight.Normal, 0.28),
        labelMedium = sans(13, 18, FontWeight.Normal, 0.26),
        labelSmall = sans(12, 16, FontWeight.Medium, 0.24),
    )

/** Styles the Material scale has no slot for. */
@Immutable
data class ColaTextStyles(
    /**
     * Eyebrows, kickers and badge text: small, wide-tracked, set in capitals by the caller
     * (`text.uppercase()`). The tracking carries the meaning, so it is part of the style. 11 sp,
     * not the reference's 10: this is the floor for readable small text.
     */
    val eyebrow: TextStyle,
    /** Large numerals on stat tiles, in the serif voice. */
    val numeral: TextStyle,
)

internal val DefaultColaTextStyles =
    ColaTextStyles(
        eyebrow = sans(11, 16, FontWeight.Normal, 1.8),
        numeral = serif(30, 34),
    )

val LocalColaTextStyles = staticCompositionLocalOf { DefaultColaTextStyles }
