package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.links.LinkOpener
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.rides.map.BasemapCaption
import ru.colabike.app.rides.map.BasemapStyle
import ru.colabike.app.rides.map.MapLibreRouteMaps
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** A build with no property shows a real map, with its sources named (ADR 0024). */
class BasemapStyleTest {
    @Test
    fun `with nothing given the map is the built-in OpenFreeMap style, light or dark`() {
        listOf(null, "", "   ", "http://tiles.example/style.json", "https://", "ftp://x/style")
            .forEach { given ->
                val light = BasemapStyle.choose(given, dark = false)
                val dark = BasemapStyle.choose(given, dark = true)

                assertThat(light).isEqualTo(BasemapStyle(BasemapStyle.LIGHT_URL, builtIn = true))
                assertThat(dark).isEqualTo(BasemapStyle(BasemapStyle.DARK_URL, builtIn = true))
            }
    }

    @Test
    fun `the style of the owner replaces the built-in one, in both themes`() {
        val given = " https://tiles.example/style.json "

        listOf(false, true).forEach { dark ->
            assertThat(BasemapStyle.choose(given, dark))
                .isEqualTo(BasemapStyle("https://tiles.example/style.json", builtIn = false))
        }
    }

    @Test
    fun `the built-in styles are https, need no key and are two different pictures`() {
        listOf(BasemapStyle.LIGHT_URL, BasemapStyle.DARK_URL).forEach {
            assertThat(it).startsWith("https://")
            assertThat(it).doesNotContain("key")
            assertThat(it).doesNotContain("?")
        }
        assertThat(BasemapStyle.LIGHT_URL).isNotEqualTo(BasemapStyle.DARK_URL)
    }

    @Test
    fun `the real map has a basemap in a build without any property`() {
        assertThat(MapLibreRouteMaps(styleOverride = null).hasBasemap).isTrue()
        assertThat(MapLibreRouteMaps(styleOverride = "").hasBasemap).isTrue()
    }
}

/** What stands on the map: the sources, or the honest word that the tiles did not come. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class BasemapCaptionTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show(attributionShown: Boolean, failed: Boolean, onRetry: () -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalLinkOpener provides LinkOpener { opened += it }) {
                ColaBikeTheme {
                    BasemapCaption(
                        attributionShown = attributionShown,
                        failed = failed,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }

    @Test
    fun `the sources of the built-in map are written on it and their terms open on a tap`() {
        show(attributionShown = true, failed = false)

        compose
            .onNodeWithText("OpenFreeMap © OpenMapTiles · данные © OpenStreetMap")
            .assertIsDisplayed()
        compose.onNodeWithTag("map:attribution").performClick()

        assertThat(opened).containsExactly("https://www.openstreetmap.org/copyright")
    }

    @Test
    fun `a map whose tiles did not come says so and offers to try again`() {
        var retried = 0
        show(attributionShown = false, failed = true, onRetry = { retried++ })

        compose
            .onNodeWithText("Подложка не загрузилась: показан только маршрут.")
            .assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()

        assertThat(retried).isEqualTo(1)
        assertThat(opened).isEmpty()
    }

    @Test
    fun `an override style brings its own attribution, so the caption is empty`() {
        show(attributionShown = false, failed = false)

        compose.onNodeWithTag("map:attribution").assertDoesNotExist()
        compose.onNodeWithTag("map:failed").assertDoesNotExist()
    }
}
