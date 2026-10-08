package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.colabike.app.rides.map.mapScale

class MapScaleTest {
    @Test
    fun `scale uses actual projection and never exceeds its reserved width`() {
        listOf(0.003, 0.1, 1.0, 15.0, 1234.0, 100_000.0).forEach { metresPerDp ->
            val scale = checkNotNull(mapScale(metresPerDp))
            assertThat(scale.widthDp).isAtMost(96f)
            assertThat(scale.widthDp).isAtLeast(38f)
            assertThat(scale.widthDp * metresPerDp).isWithin(0.5).of(scale.metres)
        }
        assertThat(mapScale(10.0)?.metres).isEqualTo(500.0)
        assertThat(mapScale(0.0)).isNull()
        assertThat(mapScale(Double.NaN)).isNull()
    }
}
