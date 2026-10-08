package ru.colabike.app.rides.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import ru.colabike.app.R
import ru.colabike.core.designsystem.theme.Spacing

internal data class MapScale(val metres: Double, val widthDp: Float)

/** Use a 1/2/5 step that fits the available width, at the map's current centre latitude. */
internal fun mapScale(metresPerDp: Double): MapScale? {
    if (!metresPerDp.isFinite() || metresPerDp <= 0) return null
    val maximum = metresPerDp * 96
    val magnitude = 10.0.pow(floor(log10(maximum)))
    val step = listOf(5.0, 2.0, 1.0).first { it * magnitude <= maximum }
    val metres = step * magnitude
    return MapScale(metres, (metres / metresPerDp).toFloat())
}

@Composable
internal fun BoxScope.MapControls(
    metresPerDp: Double?,
    bottomInset: Dp = 60.dp,
    onFit: () -> Unit,
) {
    FilledTonalButton(
        onClick = onFit,
        modifier =
            Modifier.align(Alignment.TopEnd).padding(Spacing.m).heightIn(min = Spacing.touch),
    ) {
        Text(stringResource(R.string.map_fit_route))
    }
    val scale = metresPerDp?.let(::mapScale) ?: return
    val numbers =
        NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply {
            maximumFractionDigits = 2
        }
    val label =
        if (scale.metres >= 1000)
            stringResource(R.string.map_scale_km, numbers.format(scale.metres / 1000))
        else stringResource(R.string.map_scale_m, numbers.format(scale.metres))
    val spoken = stringResource(R.string.map_scale_description, label)
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.align(Alignment.BottomStart)
            .padding(start = Spacing.m, bottom = bottomInset)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                MaterialTheme.shapes.small,
            )
            .padding(Spacing.s)
            .clearAndSetSemantics { contentDescription = spoken }
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.width(scale.widthDp.dp).height(6.dp)) {
            val stroke = 1.5.dp.toPx()
            drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
            drawLine(ink, Offset.Zero, Offset(0f, size.height), stroke)
            drawLine(ink, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
        }
    }
}
