package ru.colabike.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.colabike.app.bikes.photoPanLimits

class PhotoPanLimitsTest {
    @Test
    fun `portrait stays horizontally centered until its real image exceeds the viewport`() {
        val view = IntSize(1200, 900)
        val portrait = IntSize(300, 900)
        assertThat(photoPanLimits(view, portrait, 2f)).isEqualTo(Offset(0f, 450f))
        assertThat(photoPanLimits(view, portrait, 4f)).isEqualTo(Offset(0f, 1350f))
    }

    @Test
    fun `a panorama has no vertical pan into its letterbox`() {
        assertThat(photoPanLimits(IntSize(360, 800), IntSize(2000, 500), 4f))
            .isEqualTo(Offset(540f, 0f))
    }

    @Test
    fun `fit and unknown image geometry never permit an empty pan`() {
        assertThat(photoPanLimits(IntSize(360, 800), IntSize(600, 400), 1f)).isEqualTo(Offset.Zero)
        assertThat(photoPanLimits(IntSize(360, 800), IntSize.Zero, 4f)).isEqualTo(Offset.Zero)
    }
}
