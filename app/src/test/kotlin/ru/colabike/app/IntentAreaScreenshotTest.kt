package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.intents.IntentAreaContent
import ru.colabike.app.intents.IntentAreaDraft
import ru.colabike.app.intents.IntentEditorActions
import ru.colabike.app.rides.map.SketchRouteMaps
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.RideAreaPoint

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntentAreaScreenshotTest(private val look: Look) {
    @get:Rule val compose = createComposeRule()

    private fun capture(size: String) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, look.fontScale)
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    IntentAreaContent(
                        IntentAreaDraft("Парк Горького", RideAreaPoint(37.6, 55.73, 5000)),
                        SketchRouteMaps,
                        IntentEditorActions(),
                    )
                }
            }
        }
        compose.settle()
        compose.captureWhenDrawn("src/test/screenshots/intent_area_${size}_${look.file}.png")
        compose.onNodeWithTag("intent-area:label").performScrollTo()
        compose.settle()
        compose.captureWhenDrawn(
            "src/test/screenshots/intent_area_confirm_${size}_${look.file}.png"
        )
    }

    @Test @Config(qualifiers = "ru-w360dp-h800dp-xhdpi") fun compact() = capture("compact")

    @Test @Config(qualifiers = "ru-w1280dp-h800dp-mdpi") fun expanded() = capture("expanded")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = Look.entries.map { arrayOf(it) }
    }
}
