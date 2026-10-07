package ru.colabike.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The approved radii, moderate: 12 for fields and small tiles, 16 for cards and photos, 20 for the
 * few large frames. Nothing rounder than a card except the pill.
 */
internal val ColaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )

/** Chips, badges, the plate of the active tab: anything holding a single line of label. */
val PillShape: Shape = CircleShape

/**
 * The 4 dp grid. Screens use these, not ad-hoc numbers. A 16 dp gutter and 12 dp inside cards: the
 * approved screens are compact, a catalog shows two full cards at once.
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
    val screen = 16.dp

    /** Vertical gap between the sections of a screen. */
    val section = 24.dp

    /** Padding inside a card or a stat tile. */
    val card = 16.dp

    /** The smallest touch target (48 dp), also the smallest height of a button or a row. */
    val touch = 48.dp
}
