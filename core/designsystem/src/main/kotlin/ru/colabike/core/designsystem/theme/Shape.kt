package ru.colabike.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The reference radii: 16 for thumbnails and small tiles, 20 for list rows (its 22), 24 for cards,
 * 32 for hero frames; nothing sharper than 16. The reference's CSS "squircle" is a browser
 * superellipse (rendered radius is its token times 2.5); Compose has no stable equivalent, so the
 * corners are circular at the token value.
 */
internal val ColaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(16.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** Chips, buttons, badges, the floating bar: anything holding a single line of label. */
val PillShape: Shape = CircleShape

/**
 * The 4 dp grid. Screens use these, not ad-hoc numbers. The reference is airy (24 gutter, 40
 * between sections, 24 inside cards); on a 360 dp phone that would spend a seventh of the width on
 * gutters, so the values are 20 / 32 / 20.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Side padding of a screen; wider windows get the pane gutters of Material adaptive. */
    val screen = 20.dp

    /** Vertical gap between the sections of a screen. */
    val section = 32.dp

    /** Padding inside a card or a stat tile. */
    val card = 20.dp

    /** The smallest touch target (48 dp), also the smallest height of a button or a row. */
    val touch = 48.dp
}
