package ru.colabike.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.designsystem.theme.LocalReducedMotion

@RunWith(RobolectricTestRunner::class)
class ReducedMotionTest {
    @get:Rule val compose = createComposeRule()
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private val initial =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    @After
    fun restore() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, initial)
    }

    @Test
    fun `changing the system setting updates the current composition`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.setContent {
            ColaBikeTheme { Text(if (LocalReducedMotion.current) "reduced" else "normal") }
        }
        compose.onNodeWithText("normal").assertExists()
        fun set(scale: Float) {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
            resolver.notifyChange(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                null,
            )
            compose.waitForIdle()
        }
        set(0f)
        compose.onNodeWithText("reduced").assertExists()
        set(1f)
        compose.onNodeWithText("normal").assertExists()
    }
}
