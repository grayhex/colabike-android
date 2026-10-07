package ru.colabike.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.colabike.core.designsystem.R

// One voice: Inter, with Cyrillic, bundled in four weights (a subset of Latin and Cyrillic, about
// 57 KB each): no font provider, which devices without Google services would not have. Headings,
// numerals, labels and body form one system; the weight, not another family, carries the rank.
/** Regular for reading, Medium for labels and controls, Semibold and Bold for headings. */
val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

private fun inter(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) =
    TextStyle(
        fontFamily = Inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

/**
 * The Material scale on the approved sizes: a page title 28-32 sp bold, a section 18-20 sp
 * semibold, text 14-16 sp, metadata never under 12 sp. Everything in sp: it scales with the system
 * font.
 */
internal val ColaTypography =
    Typography(
        displayLarge = inter(34, 40, FontWeight.Bold),
        displayMedium = inter(30, 36, FontWeight.Bold),
        displaySmall = inter(28, 34, FontWeight.Bold),
        headlineLarge = inter(26, 32, FontWeight.Bold),
        headlineMedium = inter(20, 26, FontWeight.SemiBold),
        headlineSmall = inter(18, 24, FontWeight.SemiBold),
        titleLarge = inter(22, 28, FontWeight.SemiBold),
        titleMedium = inter(16, 22, FontWeight.SemiBold),
        titleSmall = inter(14, 20, FontWeight.SemiBold),
        bodyLarge = inter(16, 24),
        bodyMedium = inter(14, 20),
        bodySmall = inter(13, 18),
        labelLarge = inter(14, 20, FontWeight.Medium),
        labelMedium = inter(13, 18, FontWeight.Medium),
        labelSmall = inter(12, 16, FontWeight.Medium),
    )

/** Styles the Material scale has no slot for. */
@Immutable
data class ColaTextStyles(
    /**
     * Section captions ("КОГДА", "СОХРАНЁННОЕ"): small, wide-tracked, set in capitals by the caller
     * (`text.uppercase()`). The tracking carries the meaning, so it is part of the style. 12 sp is
     * the floor for readable small text.
     */
    val eyebrow: TextStyle,
    /** Large numerals on stat tiles: the figures of a ride. */
    val numeral: TextStyle,
    /** The name of the thing a detail page is about: 24 sp, so that it gives room to the facts. */
    val pageTitle: TextStyle,
    /** A fact on a compact tile: weight, mileage, size. */
    val figure: TextStyle,
)

internal val DefaultColaTextStyles =
    ColaTextStyles(
        eyebrow = inter(12, 16, FontWeight.Medium, 1.6),
        numeral = inter(30, 36, FontWeight.Bold),
        pageTitle = inter(24, 30, FontWeight.Bold),
        figure = inter(20, 26, FontWeight.SemiBold),
    )

val LocalColaTextStyles = staticCompositionLocalOf { DefaultColaTextStyles }
