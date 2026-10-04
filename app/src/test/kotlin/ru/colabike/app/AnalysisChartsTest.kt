package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.colabike.app.rides.runs
import ru.colabike.core.model.AnalysisChannel
import ru.colabike.core.model.AnalysisPoint
import ru.colabike.core.model.RideAnalysis

class AnalysisChartsTest {
    private fun point(
        distanceM: Double?,
        gaps: Int = 0,
        elevation: Double? = 100.0,
        speed: Double? = 5.0,
    ) =
        AnalysisPoint(
            position = null,
            distanceM = distanceM,
            elapsedS = null,
            values =
                buildMap {
                    elevation?.let { put(AnalysisChannel.Elevation, it) }
                    speed?.let { put(AnalysisChannel.Speed, it) }
                },
            gaps = gaps,
        )

    private fun analysis(vararg segments: List<AnalysisPoint>) =
        RideAnalysis(
            pointCount = segments.sumOf { it.size },
            downsampled = false,
            segments.toList(),
        )

    @Test
    fun `the distance is in kilometres and speed in kilometres per hour`() {
        val runs = analysis(listOf(point(0.0, speed = 5.0), point(1500.0, speed = 10.0)))

        assertThat(runs.runs(AnalysisChannel.Speed).single().map { it.x to it.y })
            .containsExactly(0.0 to 18.0, 1.5 to 36.0)
            .inOrder()
        assertThat(runs.runs(AnalysisChannel.Elevation).single().map { it.y })
            .containsExactly(100.0, 100.0)
    }

    @Test
    fun `a break between segments is a break on the chart`() {
        val runs =
            analysis(
                    listOf(point(0.0), point(100.0)),
                    listOf(point(5000.0), point(5100.0), point(5200.0)),
                )
                .runs(AnalysisChannel.Elevation)

        assertThat(runs.map { it.size }).containsExactly(2, 3).inOrder()
    }

    @Test
    fun `a gap of this channel breaks its line and no other channel's`() {
        val data =
            analysis(
                listOf(
                    point(0.0),
                    point(100.0),
                    // The server lost elevation (bit 1) before this point, not speed.
                    point(200.0, gaps = AnalysisChannel.Elevation.bit),
                    point(300.0),
                )
            )

        assertThat(data.runs(AnalysisChannel.Elevation).map { it.size }).containsExactly(2, 2)
        assertThat(data.runs(AnalysisChannel.Speed).map { it.size }).containsExactly(4)
    }

    @Test
    fun `a point without the value ends the run, and one without a distance is not drawn`() {
        val data =
            analysis(
                listOf(
                    point(0.0),
                    point(100.0, elevation = null),
                    point(200.0),
                    point(null),
                    point(400.0),
                    point(500.0),
                )
            )

        assertThat(data.runs(AnalysisChannel.Elevation).map { it.size })
            .containsExactly(1, 1, 2)
            .inOrder()
    }

    @Test
    fun `a channel nobody measured has no runs`() {
        val data = analysis(listOf(point(0.0), point(100.0)))

        assertThat(data.runs(AnalysisChannel.Power)).isEmpty()
        assertThat(data.channels)
            .containsExactly(AnalysisChannel.Elevation, AnalysisChannel.Speed)
            .inOrder()
    }
}
