package ru.colabike.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.rides.AnalysisSection
import ru.colabike.app.rides.AnalysisUiState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.designsystem.theme.Spacing

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalysisVisualTest(private val look: Look) {
    @get:Rule val compose = createComposeRule()

    private fun capture(window: String) {
        val data = analysisEdgeFixture()
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, look.fontScale)
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    Surface {
                        Column(
                            Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(Spacing.screen)
                        ) {
                            AnalysisSection(AnalysisUiState.Loaded(data), {})
                        }
                    }
                }
            }
        }
        compose.settle()
        compose.captureWhenDrawn("src/test/screenshots/analysis_edges_${window}_${look.file}.png")
    }

    @Test @Config(qualifiers = "ru-w360dp-h900dp-xhdpi") fun compact() = capture("compact")

    @Test @Config(qualifiers = "ru-w1200dp-h900dp-mdpi") fun expanded() = capture("expanded")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = Look.entries.map { arrayOf(it) }
    }
}
