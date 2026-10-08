package ru.colabike.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.rides.AnalysisSection
import ru.colabike.app.rides.AnalysisUiState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.AnalysisChannel
import ru.colabike.core.model.AnalysisPoint
import ru.colabike.core.model.RideAnalysis

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h500dp-mdpi")
class AnalysisInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a single sample is shown with exact values and no imaginary next sample`() {
        val data =
            RideAnalysis(
                1,
                false,
                listOf(
                    listOf(
                        AnalysisPoint(
                            null,
                            3000.0,
                            null,
                            mapOf(AnalysisChannel.Power to 200.0),
                            0,
                        )
                    )
                ),
            )
        compose.setContent { ColaBikeTheme { AnalysisSection(AnalysisUiState.Loaded(data), {}) } }
        compose.onNodeWithText("200 Вт · 3 км").assertExists()
        compose.onNodeWithContentDescription("Предыдущее измерение").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Следующее измерение").assertIsNotEnabled()
    }

    @Test
    fun `channels share the axis gutter and vertical swipes keep scrolling the page`() {
        val data = analysisEdgeFixture()
        lateinit var scroll: androidx.compose.foundation.ScrollState
        compose.setContent {
            ColaBikeTheme {
                scroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    AnalysisSection(AnalysisUiState.Loaded(data), {})
                }
            }
        }
        val elevation = compose.onNodeWithTag("chart:plot:Высота", useUnmergedTree = true)
        val speed = compose.onNodeWithTag("chart:plot:Скорость", useUnmergedTree = true)
        assertThat(elevation.getUnclippedBoundsInRoot().left)
            .isEqualTo(speed.getUnclippedBoundsInRoot().left)
        compose
            .onNode(
                hasContentDescription("Следующее измерение") and
                    hasAnyAncestor(hasTestTag("analysis:chart:Elevation"))
            )
            .performClick()
        compose.onNodeWithText("1 000 м · 1 234,6 км").assertExists()
        elevation.performTouchInput { swipeUp() }
        compose.runOnIdle { assertThat(scroll.value).isGreaterThan(0) }
    }
}

internal fun analysisEdgeFixture(): RideAnalysis =
    RideAnalysis(
        3,
        false,
        listOf(
            listOf(
                AnalysisPoint(
                    null,
                    0.0,
                    null,
                    mapOf(
                        AnalysisChannel.Elevation to 1000.0,
                        AnalysisChannel.Speed to 5.0,
                        AnalysisChannel.Power to 123456.0,
                    ),
                    0,
                ),
                AnalysisPoint(
                    null,
                    1234567.0,
                    null,
                    mapOf(AnalysisChannel.Elevation to 1000.0, AnalysisChannel.Power to 123456.0),
                    AnalysisChannel.Elevation.bit or AnalysisChannel.Power.bit,
                ),
                AnalysisPoint(null, null, null, mapOf(AnalysisChannel.Cadence to 80.0), 0),
            )
        ),
    )
