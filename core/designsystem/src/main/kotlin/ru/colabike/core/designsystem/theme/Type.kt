package ru.colabike.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.colabike.core.designsystem.R

// Source Sans 3, the site's family (Latin and Cyrillic), bundled: no font provider, which
// devices without Google services would not have. One variable file, one entry per weight.
private fun sourceSans(weight: FontWeight) =
    Font(
        resId = R.font.source_sans_3,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

val SourceSans3 =
    FontFamily(
        sourceSans(FontWeight.Normal),
        sourceSans(FontWeight.Medium),
        sourceSans(FontWeight.SemiBold),
        sourceSans(FontWeight.Bold),
        sourceSans(FontWeight.Black),
    )

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = SourceSans3,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

/**
 * The Material scale in the site's sizes (14 px body, 16 px reading text). All in sp: it scales.
 */
internal val ColaTypography =
    Typography(
        displayLarge = style(48, 56, FontWeight.Black, -0.5),
        displayMedium = style(36, 44, FontWeight.Black, -0.25),
        displaySmall = style(30, 38, FontWeight.Bold),
        headlineLarge = style(30, 38, FontWeight.Bold),
        headlineMedium = style(24, 32, FontWeight.Bold),
        headlineSmall = style(20, 28, FontWeight.SemiBold),
        titleLarge = style(20, 28, FontWeight.SemiBold),
        titleMedium = style(16, 24, FontWeight.SemiBold),
        titleSmall = style(14, 20, FontWeight.SemiBold),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 16, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.SemiBold),
        labelMedium = style(12, 16, FontWeight.SemiBold),
        labelSmall = style(11, 16, FontWeight.Medium, 0.2),
    )
