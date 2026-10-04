package ru.colabike.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.app.messages.colaChatColors
import ru.colabike.app.messages.colaChatConfig
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** The chat SDK's palette comes from this app's theme and keeps text readable in both. */
@RunWith(RobolectricTestRunner::class)
class ColaChatThemeTest {
    @get:Rule val compose = createComposeRule()

    private val darkTheme = mutableStateOf(false)
    private lateinit var captured: ColorScheme

    @Before
    fun setUp() {
        compose.setContent {
            ColaBikeTheme(darkTheme = darkTheme.value) { captured = MaterialTheme.colorScheme }
        }
    }

    private fun scheme(dark: Boolean): ColorScheme {
        darkTheme.value = dark
        compose.waitForIdle()
        return captured
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val front = foreground.compositeOver(background)
        val a = front.luminance() + 0.05f
        val b = background.luminance() + 0.05f
        return maxOf(a, b) / minOf(a, b)
    }

    private fun assertReadable(dark: Boolean) {
        val scheme = scheme(dark)
        val colors = colaChatColors(scheme, dark)

        // The SDK draws text on the page, on cards, and on its accent (the bubbles of one's own).
        listOf(
                "primary text on the page" to (colors.textPrimary to colors.backgroundCoreApp),
                "primary text on a card" to
                    (colors.textPrimary to colors.backgroundCoreSurfaceCard),
                "secondary text on the page" to (colors.textSecondary to colors.backgroundCoreApp),
                "tertiary text on the page" to (colors.textTertiary to colors.backgroundCoreApp),
                "text on the accent" to (colors.textOnAccent to colors.accentPrimary),
                "link on the page" to (colors.textLink to colors.backgroundCoreApp),
                "error on the page" to (colors.accentError to colors.backgroundCoreApp),
            )
            .forEach { (what, pair) ->
                assertWithMessage("$what, dark=$dark")
                    .that(contrast(pair.first, pair.second))
                    .isAtLeast(MIN_CONTRAST)
            }
    }

    @Test fun `the light palette keeps text readable`() = assertReadable(dark = false)

    @Test fun `the dark palette keeps text readable`() = assertReadable(dark = true)

    @Test
    fun `the page of the chat is the page of the app, in both themes`() {
        listOf(false, true).forEach { dark ->
            val scheme = scheme(dark)

            assertThat(colaChatColors(scheme, dark).backgroundCoreApp).isEqualTo(scheme.background)
            assertThat(colaChatColors(scheme, dark).accentPrimary.luminance())
                .isNotEqualTo(scheme.background.luminance())
        }
    }

    @Test
    fun `what the server does not allow is not offered`() {
        assertThat(colaChatConfig.composer.audioRecordingEnabled).isFalse()
        assertThat(colaChatConfig.composer.linkPreviewEnabled).isFalse()
        assertThat(colaChatConfig.translation.enabled).isFalse()
        assertThat(colaChatConfig.attachmentPicker.useSystemPicker).isTrue()
        assertThat(colaChatConfig.messageActions.optionsVisibility.isFlagMessageVisible).isFalse()
        assertThat(colaChatConfig.messageActions.optionsVisibility.isBlockUserVisible).isFalse()
    }

    private companion object {
        /** WCAG AA for normal text. */
        const val MIN_CONTRAST = 4.5f
    }
}
