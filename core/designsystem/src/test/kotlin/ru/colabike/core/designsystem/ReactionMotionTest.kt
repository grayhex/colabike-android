package ru.colabike.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import ru.colabike.core.designsystem.component.LikeButton
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.designsystem.theme.LocalReducedMotion

/** Pending requests and failed optimistic updates remain operable with or without animation. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class ReactionMotionTest(private val reduced: Boolean) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `pending request blocks repeat and rollback restores action without haptic`() {
        var liked by mutableStateOf(false)
        var pending by mutableStateOf(false)
        var requests = 0
        val feedback = mutableListOf<HapticFeedbackType>()
        val haptics =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    feedback += hapticFeedbackType
                }
            }
        compose.setContent {
            ColaBikeTheme {
                CompositionLocalProvider(
                    LocalReducedMotion provides reduced,
                    LocalHapticFeedback provides haptics,
                ) {
                    LikeButton(
                        liked,
                        if (liked) 10 else 9,
                        busy = pending,
                        onToggle = {
                            requests++
                            liked = !liked
                            pending = true
                        },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Нравится, 9").assertIsOff().performClick()
        compose
            .onNodeWithContentDescription("Нравится, 10, Сохраняем")
            .assertIsOn()
            .assertIsNotEnabled()
            .performClick()
        assertThat(requests).isEqualTo(1)
        assertThat(feedback).containsExactly(HapticFeedbackType.ToggleOn)
        compose.runOnIdle {
            liked = false
            pending = false
        }
        compose.onNodeWithContentDescription("Нравится, 9").assertIsOff().assertIsEnabled()
        assertThat(feedback).hasSize(1)
        compose.onNodeWithContentDescription("Нравится, 9").performClick()
        compose.runOnIdle { pending = false }
        compose.onNodeWithContentDescription("Нравится, 10").performClick()
        assertThat(requests).isEqualTo(3)
        assertThat(feedback.last()).isEqualTo(HapticFeedbackType.ToggleOff)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "reduced motion {0}")
        fun cases(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
