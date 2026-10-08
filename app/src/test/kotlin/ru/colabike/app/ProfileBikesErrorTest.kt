package ru.colabike.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.profile.ProfileBikesRoute
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.DataError

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class ProfileBikesErrorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `preview failure retains retry time and support reference then recovers`() {
        val people = FakePeople().apply { nextError = DataError.RateLimited(120) }
        val bikes = FakeBikes()
        compose.setContent { ColaBikeTheme { ProfileBikesRoute(people, bikes, "viewer", {}, {}) } }
        compose.onNodeWithText("Попробуйте через 2 мин.", substring = true).assertExists()
        people.nextError = DataError.Server(503, "visual-request-reference")
        compose.onNodeWithText("Повторить").performClick()
        compose.onNodeWithText("visual-request-reference", substring = true).assertExists()
        compose.onNodeWithText("Повторить").performClick()
        compose.onNodeWithText("visual-request-reference", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Велосипед 0").assertExists()
    }
}
