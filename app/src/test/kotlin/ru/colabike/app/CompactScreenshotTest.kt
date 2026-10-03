package ru.colabike.app

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phone width: every screen in both themes and with 200 % text. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class CompactScreenshotTest(private val screen: Screen, private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @Test fun capture() = compose.captureScreen(screen, "compact", look)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> =
            Screen.entries.flatMap { screen -> Look.entries.map { arrayOf(screen, it) } }
    }
}
