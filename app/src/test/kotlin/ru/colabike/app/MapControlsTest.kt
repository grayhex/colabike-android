package ru.colabike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.rides.map.MapControls
import ru.colabike.core.designsystem.theme.ColaBikeTheme

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h640dp-xhdpi")
class MapControlsTest(private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fitAndScaleStayReadableAndFitIsAWholeTouchTarget() {
        var fits = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalRippleConfiguration provides null,
                LocalDensity provides Density(LocalDensity.current.density, look.fontScale),
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        MapControls(10.0) { fits++ }
                    }
                }
            }
        }
        val fit = compose.onNodeWithText("Весь маршрут")
        fit.assertIsDisplayed().performClick()
        assertThat(fits).isEqualTo(1)
        val bounds = fit.getUnclippedBoundsInRoot()
        assertThat((bounds.bottom - bounds.top).value).isAtLeast(48f)
        compose.onNodeWithContentDescription("Масштаб: 500 м").assertIsDisplayed()
        compose.captureWhenDrawn("src/test/screenshots/map_controls_${look.file}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = Look.entries.map { arrayOf(it) }
    }
}
