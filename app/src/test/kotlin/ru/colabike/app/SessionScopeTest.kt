package ru.colabike.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.UserId

/** Nothing of a signed-out account reaches the next one: its ViewModels go with its session. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class SessionScopeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theNextAccountSeesItsOwnProfile() {
        val dependencies = FakeDependencies()
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }

        compose.section("Профиль").performClick()
        compose.onNodeWithText("Тестовый Райдер").assertExists()

        dependencies.account.result = {
            account.copy(id = UserId("u2"), username = "second", name = "Вторая Райдерша")
        }
        runBlocking {
            dependencies.auth.signOut()
            compose.waitForIdle()
            dependencies.auth.signIn("second@example.test", "password")
        }
        compose.waitForIdle()
        compose.section("Профиль").performClick()

        compose.onNodeWithText("Вторая Райдерша").assertExists()
        compose.onNodeWithText("Тестовый Райдер").assertDoesNotExist()
    }
}
