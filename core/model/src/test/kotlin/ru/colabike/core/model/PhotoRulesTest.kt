package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoRulesTest {
    @Test
    fun `600 by 400 is the least the server takes, whichever way the phone was held`() {
        assertThat(PhotoRules.isTooSmall(600, 400)).isFalse()
        assertThat(PhotoRules.isTooSmall(400, 600)).isFalse()
        assertThat(PhotoRules.isTooSmall(599, 400)).isTrue()
        assertThat(PhotoRules.isTooSmall(600, 399)).isTrue()
        assertThat(PhotoRules.isTooSmall(0, 0)).isTrue()
    }

    @Test
    fun `a picture of more than forty million pixels is not read`() {
        assertThat(PhotoRules.fitsPixels(8000, 5000)).isTrue()
        assertThat(PhotoRules.fitsPixels(8000, 5001)).isFalse()
        // A product that does not fit an Int is still compared as it is.
        assertThat(PhotoRules.fitsPixels(100_000, 100_000)).isFalse()
    }

    @Test
    fun `a bike holds twelve pictures of ten megabytes at most`() {
        assertThat(PhotoRules.MAX_PER_BIKE).isEqualTo(12)
        assertThat(PhotoRules.MAX_BYTES).isEqualTo(10_485_760L)
    }
}
