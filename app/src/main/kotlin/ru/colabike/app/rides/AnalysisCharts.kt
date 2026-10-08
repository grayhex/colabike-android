package ru.colabike.app.rides

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import java.text.NumberFormat
import java.util.Locale
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ChartPoint
import ru.colabike.core.designsystem.component.ChartSeries
import ru.colabike.core.designsystem.component.SeriesChart
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AnalysisChannel
import ru.colabike.core.model.RideAnalysis

/** Metres per second in kilometres per hour: speed is told the way cyclists say it. */
private const val KMH_PER_MPS = 3.6

/**
 * The continuous runs of one channel against the distance in kilometres. A run ends where the track
 * is cut (the end of a segment), where the server lost this channel's values before a point (its
 * mask of gaps), and where a point has no value or no distance: no line is drawn across any of
 * them, because a line would invent what nobody measured.
 */
internal fun RideAnalysis.runs(channel: AnalysisChannel): List<List<ChartPoint>> {
    val scale = if (channel == AnalysisChannel.Speed) KMH_PER_MPS else 1.0
    val result = mutableListOf<List<ChartPoint>>()
    for (segment in segments) {
        var current = mutableListOf<ChartPoint>()
        fun close() {
            if (current.isNotEmpty()) result += current
            current = mutableListOf()
        }
        for (point in segment) {
            if (point.gapBefore(channel)) close()
            val x = point.distanceM
            val y = point.values[channel]
            if (x != null && y != null) current += ChartPoint(x / 1000.0, y * scale) else close()
        }
        close()
    }
    return result
}

@StringRes
private fun AnalysisChannel.title(): Int =
    when (this) {
        AnalysisChannel.Elevation -> R.string.analysis_elevation
        AnalysisChannel.Speed -> R.string.analysis_speed
        AnalysisChannel.Grade -> R.string.analysis_grade
        AnalysisChannel.HeartRate -> R.string.analysis_heart_rate
        AnalysisChannel.Cadence -> R.string.analysis_cadence
        AnalysisChannel.Power -> R.string.analysis_power
    }

@StringRes
private fun AnalysisChannel.unit(): Int =
    when (this) {
        AnalysisChannel.Elevation -> R.string.analysis_unit_m
        AnalysisChannel.Speed -> R.string.analysis_unit_kmh
        AnalysisChannel.Grade -> R.string.analysis_unit_percent
        AnalysisChannel.HeartRate -> R.string.analysis_unit_bpm
        AnalysisChannel.Cadence -> R.string.analysis_unit_rpm
        AnalysisChannel.Power -> R.string.analysis_unit_w
    }

/** Whole numbers for what is counted in whole numbers, one decimal for speed and grade. */
private fun AnalysisChannel.fractionDigits(): Int =
    when (this) {
        AnalysisChannel.Speed,
        AnalysisChannel.Grade -> 1
        else -> 0
    }

/** The section of the page: the charts of the ride's series, in their own request's state. */
@Composable
internal fun AnalysisSection(
    state: AnalysisUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AnalysisUiState.NotAsked,
        AnalysisUiState.Absent -> Unit
        AnalysisUiState.Loading ->
            Section(modifier) {
                Row(
                    Modifier.heightIn(min = Spacing.touch),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.padding(Spacing.xs))
                    Text(
                        stringResource(R.string.analysis_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        is AnalysisUiState.Failed ->
            Section(modifier) {
                Text(
                    stringResource(R.string.analysis_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    state.message.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Spacing.touch)) {
                    Text(stringResource(R.string.analysis_retry))
                }
            }
        is AnalysisUiState.Loaded -> {
            val analysis = state.analysis
            if (analysis.channels.isEmpty()) return
            val locale = LocalConfiguration.current.locales[0]
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelStyle = MaterialTheme.typography.labelSmall
            val labels =
                analysis.channels.flatMap { channel ->
                    val values = analysis.runs(channel).flatten()
                    val unit = stringResource(channel.unit(), "%s")
                    val numbers = channel.format(locale)
                    if (values.isEmpty()) emptyList()
                    else
                        listOf(values.minOf { it.y }, values.maxOf { it.y }).map {
                            unit.replace("%s", numbers.format(it))
                        }
                }
            val axisWidth =
                with(density) {
                    (labels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0).toDp()
                }
            val distances =
                remember(analysis) {
                    analysis.segments.flatten().mapNotNull { it.distanceM?.div(1000.0) }
                }
            val domain = (distances.minOrNull() ?: 0.0)..(distances.maxOrNull() ?: 0.0)
            Section(modifier) {
                analysis.channels.forEach { channel ->
                    key(channel) { Chart(analysis, channel, locale, axisWidth, domain) }
                }
                if (analysis.downsampled) {
                    Text(
                        stringResource(R.string.analysis_downsampled, analysis.pointCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Text(
            stringResource(R.string.analysis_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Chart(
    analysis: RideAnalysis,
    channel: AnalysisChannel,
    locale: Locale,
    axisWidth: Dp,
    domain: ClosedFloatingPointRange<Double>,
) {
    val title = stringResource(channel.title())
    val unit = stringResource(channel.unit(), "%s")
    val numbers = remember(locale, channel) { channel.format(locale) }
    val kilometres = remember(locale) { NumberFormat.getNumberInstance(locale) }
    val runs = remember(analysis, channel) { analysis.runs(channel) }
    val points = remember(runs) { runs.flatten() }
    if (points.isEmpty()) return
    val low = points.minOf { it.y }
    val high = points.maxOf { it.y }
    val first = domain.start
    val last = domain.endInclusive
    val withUnit = { value: Double -> unit.replace("%s", numbers.format(value)) }
    val kmPattern = stringResource(R.string.analysis_km, "%s")
    val km = { value: Double -> kmPattern.replace("%s", kilometres.format(value.roundTo1())) }
    val summary = stringResource(R.string.analysis_summary, withUnit(low), withUnit(high), km(last))
    val spoken =
        if (runs.size > 1) stringResource(R.string.analysis_summary_breaks, summary) else summary
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selected = selectedIndex.coerceIn(points.indices)
    val measurement = points[selected]
    SeriesChart(
        ChartSeries(
            title = title,
            runs = runs,
            minLabel = withUnit(low),
            maxLabel = withUnit(high),
            startLabel = km(first),
            endLabel = km(last),
            summary = spoken,
        ),
        modifier = Modifier.testTag("analysis:chart:${channel.name}"),
        axisWidth = axisWidth,
        xRange = domain,
        selectedIndex = selected,
        selectionLabel =
            stringResource(R.string.analysis_selected, withUnit(measurement.y), km(measurement.x)),
        onSelect = { selectedIndex = it },
    )
}

private fun Double.roundTo1(): Double = Math.round(this * 10) / 10.0

private fun AnalysisChannel.format(locale: Locale): NumberFormat =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = fractionDigits()
    }
