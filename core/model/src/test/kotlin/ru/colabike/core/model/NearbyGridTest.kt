package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The phone rounds its place to the cell the server expects (cola `snapToCell`), before it says
 * anything.
 */
class NearbyGridTest {
    private val grid = NearbyGrid(latStep = 0.03, lngStep = 0.05)

    @Test
    fun `a point lands on the centre of its cell, as the server computes it`() {
        // The server's own arithmetic: floor(37.62 / 0.05) = 752 -> 37.625; floor(55.75 / 0.03) =
        // 1858 -> 55.755.
        assertThat(grid.centerOf(37.62, 55.75)).isEqualTo(37.625 to 55.755)
        assertThat(grid.centerOf(37.6173, 55.7558)).isEqualTo(37.625 to 55.755)
    }

    @Test
    fun `the centre of a cell is its own cell`() {
        val (lng, lat) = grid.centerOf(37.62, 55.75)

        assertThat(grid.centerOf(lng, lat)).isEqualTo(lng to lat)
    }

    @Test
    fun `neighbouring points of one cell give one centre, a step away gives the next`() {
        val (lng, lat) = grid.centerOf(37.62, 55.75)

        assertThat(grid.centerOf(37.61, 55.74)).isEqualTo(lng to lat)
        assertThat(grid.centerOf(37.62 + 0.05, 55.75)).isNotEqualTo(lng to lat)
    }

    @Test
    fun `south and west of the origin round the same way`() {
        val (lng, lat) = grid.centerOf(-70.123, -33.456)

        assertThat(lng).isWithin(0.025 + 1e-9).of(-70.123)
        assertThat(lat).isWithin(0.015 + 1e-9).of(-33.456)
        assertThat(grid.centerOf(lng, lat)).isEqualTo(lng to lat)
    }

    @Test
    fun `the centre keeps five decimals, as the server does`() {
        val (lng, lat) = grid.centerOf(37.62, 55.75)

        assertThat(lng.toString().substringAfter('.').length).isAtMost(5)
        assertThat(lat.toString().substringAfter('.').length).isAtMost(5)
    }
}
