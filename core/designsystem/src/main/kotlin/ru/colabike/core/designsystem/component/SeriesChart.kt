package ru.colabike.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
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
@Composable
fun SeriesChart(series: ChartSeries, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bounds = remember(series) { series.bounds() }
    val path = remember(series, bounds) { series.runs.map { run -> run.toPath(bounds) } }
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "${series.title}: ${series.summary}"
        },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Eyebrow(series.title)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(Modifier.height(ChartHeight), verticalArrangement = Arrangement.SpaceBetween) {
                AxisLabel(series.maxLabel)
                AxisLabel(series.minLabel)
            }
            Column(Modifier.weight(1f)) {
                Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
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
                }
                Row(Modifier.fillMaxWidth()) {
                    AxisLabel(series.startLabel)
                    Box(Modifier.weight(1f))
                    AxisLabel(series.endLabel)
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
        maxLines = 1,
    )
}

/** The box the series is drawn into, padded so that a flat line sits in the middle. */
private data class Bounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

private fun ChartSeries.bounds(): Bounds {
    val all = runs.flatten()
    if (all.isEmpty()) return Bounds(0.0, 1.0, 0.0, 1.0)
    val minX = all.minOf { it.x }
    val maxX = all.maxOf { it.x }
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
