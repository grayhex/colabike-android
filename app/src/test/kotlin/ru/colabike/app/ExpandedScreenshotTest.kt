package ru.colabike.app

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tablet or unfolded foldable: the rail and the two-pane list-detail, both themes. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w1280dp-h800dp-mdpi")
class ExpandedScreenshotTest(private val screen: Screen, private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @Test fun capture() = compose.captureScreen(screen, "expanded", look)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> =
            Screen.entries.flatMap { screen ->
                listOf(Look.Light, Look.Dark).map { arrayOf(screen, it) }
            }
    }
}
