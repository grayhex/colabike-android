package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaUrlsTest {
    private val media = MediaUrls("https://colabike.ru")

    @Test
    fun `site paths become absolute https addresses`() {
        assertThat(media.resolve("/api/photos/abc?width=640"))
            .isEqualTo("https://colabike.ru/api/photos/abc?width=640")
    }

    @Test
    fun `absolute http addresses pass, anything else is dropped`() {
        assertThat(media.resolve("https://cdn.example/x.jpg"))
            .isEqualTo("https://cdn.example/x.jpg")
        assertThat(media.resolve("javascript:alert(1)")).isNull()
        assertThat(media.resolve("file:///data/data/ru.colabike.app/x")).isNull()
        assertThat(media.resolve("  ")).isNull()
        assertThat(media.resolve(null)).isNull()
    }
}
