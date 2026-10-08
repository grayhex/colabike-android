package ru.colabike.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.Spacing

@Immutable data class ChartPoint(val x: Double, val y: Double)

/**
 * One series to draw. Every entry of [runs] is a continuous part: a break between two runs (a
 * privacy cut, a lost sensor) is a break on the chart, and no line crosses it. [summary] is what a
 * screen reader says instead of the picture, and [minLabel], [maxLabel], [startLabel], [endLabel]
 * are the visible numbers on its edges, already formatted for the person's language.
 */
@Immutable
data class ChartSeries(
    val title: String,
    val runs: List<List<ChartPoint>>,
    val minLabel: String,
    val maxLabel: String,
    val startLabel: String,
    val endLabel: String,
    val summary: String,
)

private val ChartHeight = 112.dp

/**
 * A line chart in the quiet reference style: a hairline grid, one line in the primary colour, the
 * extremes written at the left edge and the ends of the way under it. The picture is not the only
 * carrier of the meaning: the numbers are on it and [ChartSeries.summary] is spoken.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesChart(
    series: ChartSeries,
    modifier: Modifier = Modifier,
    axisWidth: Dp = Dp.Unspecified,
    xRange: ClosedFloatingPointRange<Double>? = null,
    selectedIndex: Int? = null,
    selectionLabel: String? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bounds = remember(series, xRange) { series.bounds(xRange) }
    val path = remember(series, bounds) { series.runs.map { run -> run.toPath(bounds) } }
    val points = remember(series.runs) { series.runs.flatten() }
    val choose by rememberUpdatedState(onSelect)
    val selected = selectedIndex?.coerceIn(0, (points.size - 1).coerceAtLeast(0))
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Eyebrow(series.title)
        Row(
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${series.title}: ${series.summary}"
            },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Column(
                Modifier.height(ChartHeight)
                    .then(if (axisWidth != Dp.Unspecified) Modifier.width(axisWidth) else Modifier),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AxisLabel(series.maxLabel)
                AxisLabel(series.minLabel)
            }
            Column(Modifier.weight(1f)) {
                Canvas(
                    Modifier.fillMaxWidth()
                        .height(ChartHeight)
                        .testTag("chart:plot:${series.title}")
                        .clearAndSetSemantics {}
                        .pointerInput(points, bounds) {
                            if (choose != null)
                                detectTapGestures { position ->
                                    val x =
                                        bounds.minX +
                                            (position.x / size.width).coerceIn(0f, 1f) *
                                                (bounds.maxX - bounds.minX)
                                    nearestChartPoint(points, x)?.let { choose?.invoke(it) }
                                }
                        }
                ) {
                    val hairline = 1.dp.toPx()
                    for (fraction in listOf(0f, 0.5f, 1f)) {
                        val y = size.height * fraction
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), hairline)
                    }
                    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    series.runs.forEachIndexed { index, run ->
                        if (run.size == 1) {
                            // A run of one value has no line, but it was measured: a dot.
                            val point = run.single().at(bounds, size.width, size.height)
                            drawCircle(line, 2.5.dp.toPx(), point)
                        } else {
                            drawPath(
                                path[index].scaled(size.width, size.height),
                                line,
                                style = stroke,
                            )
                        }
                    }
                    selected?.let { index ->
                        points.getOrNull(index)?.let { value ->
                            val point = value.at(bounds, size.width, size.height)
                            drawLine(
                                line,
                                Offset(point.x, 0f),
                                Offset(point.x, size.height),
                                1.dp.toPx(),
                            )
                            drawCircle(line, 5.dp.toPx(), point)
                        }
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    maxItemsInEachRow = 2,
                ) {
                    AxisLabel(series.startLabel)
                    AxisLabel(series.endLabel)
                }
            }
        }
        if (onSelect != null && selected != null && points.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                IconButton(
                    onClick = { onSelect(selected - 1) },
                    enabled = selected > 0,
                    modifier = Modifier.size(Spacing.touch),
                ) {
                    Icon(
                        painterResource(ColaIcons.ArrowBack),
                        stringResource(R.string.cola_chart_previous),
                    )
                }
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(selectionLabel.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.cola_chart_position, selected + 1, points.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onSelect(selected + 1) },
                    enabled = selected < points.lastIndex,
                    modifier = Modifier.size(Spacing.touch),
                ) {
                    Icon(
                        painterResource(ColaIcons.ChevronRight),
                        stringResource(R.string.cola_chart_next),
                    )
                }
            }
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/** The box the series is drawn into, padded so that a flat line sits in the middle. */
private data class Bounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

private fun ChartSeries.bounds(xRange: ClosedFloatingPointRange<Double>?): Bounds {
    val all = runs.flatten()
    if (all.isEmpty()) return Bounds(0.0, 1.0, 0.0, 1.0)
    val minX = xRange?.start ?: all.minOf { it.x }
    val maxX = xRange?.endInclusive ?: all.maxOf { it.x }
    val minY = all.minOf { it.y }
    val maxY = all.maxOf { it.y }
    return Bounds(
        minX,
        if (maxX > minX) maxX else minX + 1.0,
        if (maxY > minY) minY else minY - 1.0,
        if (maxY > minY) maxY else maxY + 1.0,
    )
}

/** The unit square, y up: the path is stretched to the canvas at draw time. */
private fun List<ChartPoint>.toPath(bounds: Bounds): Path =
    Path().apply {
        forEachIndexed { index, point ->
            val x = ((point.x - bounds.minX) / (bounds.maxX - bounds.minX)).toFloat()
            val y = (1.0 - (point.y - bounds.minY) / (bounds.maxY - bounds.minY)).toFloat()
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }

private fun Path.scaled(width: Float, height: Float): Path {
    val matrix = androidx.compose.ui.graphics.Matrix()
    matrix.scale(width, height)
    return Path().also {
        it.addPath(this)
        it.transform(matrix)
    }
}

private fun ChartPoint.at(bounds: Bounds, width: Float, height: Float): Offset {
    val x = ((this.x - bounds.minX) / (bounds.maxX - bounds.minX)).toFloat() * width
    val y = (1.0 - (this.y - bounds.minY) / (bounds.maxY - bounds.minY)).toFloat() * height
    return Offset(x, y)
}

/** A gap has no synthetic sample: choose the nearest measured point, with stable tie-breaking. */
internal fun nearestChartPoint(points: List<ChartPoint>, x: Double): Int? =
    points.indices.minByOrNull { kotlin.math.abs(points[it].x - x) }
