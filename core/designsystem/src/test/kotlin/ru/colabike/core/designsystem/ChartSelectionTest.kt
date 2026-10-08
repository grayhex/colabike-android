package ru.colabike.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.core.designsystem.component.ChartPoint
import ru.colabike.core.designsystem.component.ChartSeries
import ru.colabike.core.designsystem.component.SeriesChart
import ru.colabike.core.designsystem.component.nearestChartPoint
import ru.colabike.core.designsystem.theme.ColaBikeTheme

@RunWith(RobolectricTestRunner::class)
class ChartSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a gap selects a real sample and ties keep the earlier sample`() {
        val points = listOf(ChartPoint(0.0, 10.0), ChartPoint(1.0, 12.0), ChartPoint(5.0, 70.0))
        assertThat(nearestChartPoint(points, 3.0)).isEqualTo(1)
        assertThat(nearestChartPoint(points, 3.1)).isEqualTo(2)
        assertThat(nearestChartPoint(emptyList(), 0.0)).isNull()
    }

    @Test
    fun `buttons reach exact samples and remain accessible independently of the chart summary`() {
        val points = listOf(ChartPoint(0.0, 10.0), ChartPoint(5.0, 70.0))
        compose.setContent {
            var index by remember { mutableIntStateOf(0) }
            ColaBikeTheme {
                SeriesChart(
                    ChartSeries(
                        "Высота",
                        points.map { listOf(it) },
                        "10 м",
                        "70 м",
                        "0 км",
                        "5 км",
                        "От 10 до 70 м. Есть разрыв",
                    ),
                    selectedIndex = index,
                    selectionLabel = "${points[index].y} м, ${points[index].x} км",
                    onSelect = { index = it },
                )
            }
        }
        compose
            .onNodeWithContentDescription("Предыдущее измерение")
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Следующее измерение").performClick()
        compose.onNodeWithText("70.0 м, 5.0 км").assertExists()
        compose.onNodeWithContentDescription("Следующее измерение").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Высота: От 10 до 70 м. Есть разрыв").assertExists()
        compose.onNodeWithTag("chart:plot:Высота", useUnmergedTree = true).performTouchInput {
            click(Offset(width * .1f, height * .5f))
        }
        compose.onNodeWithText("10.0 м, 0.0 км").assertExists()
    }

    @Test
    fun `a singleton remains selectable and has no imaginary neighbours`() {
        compose.setContent {
            ColaBikeTheme {
                SeriesChart(
                    ChartSeries(
                        "Мощность",
                        listOf(listOf(ChartPoint(3.0, 200.0))),
                        "200 Вт",
                        "200 Вт",
                        "3 км",
                        "3 км",
                        "200 Вт",
                    ),
                    selectedIndex = 0,
                    selectionLabel = "200 Вт · 3 км",
                    onSelect = {},
                )
            }
        }
        compose.onNodeWithText("200 Вт · 3 км").assertExists()
        compose.onNodeWithContentDescription("Предыдущее измерение").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Следующее измерение").assertIsNotEnabled()
    }
}
