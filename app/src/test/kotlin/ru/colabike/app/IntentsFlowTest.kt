package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.DataError
import ru.colabike.core.model.IntentStatus
import ru.colabike.core.model.IntentVisibility

/** "I want to ride" as a person uses it, on a phone: the lists, the form, the page. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class IntentsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val own = sampleIntent(3, own = true, visibility = IntentVisibility.Private)
    private val intents = FakeIntents()

    private fun start(intents: FakeIntents = this.intents) {
        compose.setContent {
            ColaBikeTheme { ColaBikeApp(FakeDependencies(intents = intents)) }
        }
        compose.waitForIdle()
        compose.section("Покатушки").performClick()
        compose.onNodeWithTag("rides:intents").performClick()
        compose.waitForIdle()
    }

    private fun openMine() {
        compose.onNodeWithTag("intents:segment:mine").performClick()
        compose.waitForIdle()
    }

    private fun scrolled(tag: String) = compose.onNodeWithTag(tag).performScrollTo()

    @Test
    fun `the Rides section leads to the intentions of the community`() {
        start()

        compose.onNodeWithTag("intents:list").assertIsDisplayed()
        compose.onNodeWithTag("intent:${intents.community.first().id}").assertIsDisplayed()
    }

    @Test
    fun `back returns to the Rides section`() {
        start()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag("rides:intents").assertIsDisplayed()
    }

    @Test
    fun `the person's own list starts empty and says how to begin`() {
        start()

        openMine()

        compose.onNodeWithTag("intents:empty").assertIsDisplayed()
        compose.onNodeWithText("У вас нет намерений").assertIsDisplayed()
    }

    @Test
    fun `a failed list says why and loading again shows it`() {
        intents.loadError = DataError.Server(503, null)
        start()
        compose.onNodeWithText("Повторить").assertIsDisplayed()

        intents.loadError = null
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("intents:list").assertIsDisplayed()
    }

    // --- creating --------------------------------------------------------------------------------

    @Test
    fun `an intention is made with a time already chosen and only an area typed, private first`() {
        start()

        compose.onNodeWithTag("intents:create").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("intent-editor").assertIsDisplayed()
        compose.onNodeWithTag("intent-editor:visibility:private").assertIsSelected()
        scrolled("intent-editor:area")
        compose.onNodeWithTag("intent-editor:area").performTextInput("Парк Горького")
        scrolled("intent-editor:save")
        compose.onNodeWithTag("intent-editor:save").performClick()
        compose.waitForIdle()

        assertThat(intents.created).hasSize(1)
        val draft = intents.created.single().first
        assertThat(draft.passport.areaLabel).isEqualTo("Парк Горького")
        assertThat(draft.visibility).isEqualTo(IntentVisibility.Private)
        // The person lands on the page of what was made.
        compose.onNodeWithTag("intent:page").assertIsDisplayed()
        compose.onNodeWithTag("intent:who").assertTextContains("Ваше намерение")
    }

    @Test
    fun `saving without an area says so and sends nothing`() {
        start()
        compose.onNodeWithTag("intents:create").performClick()
        compose.waitForIdle()

        scrolled("intent-editor:save")
        compose.onNodeWithTag("intent-editor:save").performClick()
        compose.waitForIdle()

        compose
            .onNodeWithTag("intent-editor:problem")
            .assertTextContains("Укажите район", substring = true)
        assertThat(intents.created).isEmpty()
    }

    @Test
    fun `publishing to the community is chosen, and said in words`() {
        start()
        compose.onNodeWithTag("intents:create").performClick()
        compose.waitForIdle()

        scrolled("intent-editor:visibility:community")
        compose.onNodeWithTag("intent-editor:visibility:community").performClick()
        compose
            .onNodeWithText("Друзья с включёнными уведомлениями", substring = true)
            .assertIsDisplayed()
        scrolled("intent-editor:area")
        compose.onNodeWithTag("intent-editor:area").performTextInput("Парк")
        scrolled("intent-editor:save")
        compose.onNodeWithTag("intent-editor:save").performClick()
        compose.waitForIdle()

        assertThat(intents.created.single().first.visibility).isEqualTo(IntentVisibility.Community)
    }

    @Test
    fun `an unconfirmed e-mail for a published intention is said, and the form stays`() {
        start()
        compose.onNodeWithTag("intents:create").performClick()
        compose.waitForIdle()
        scrolled("intent-editor:visibility:community")
        compose.onNodeWithTag("intent-editor:visibility:community").performClick()
        scrolled("intent-editor:area")
        compose.onNodeWithTag("intent-editor:area").performTextInput("Парк")
        intents.failNext = DataError.Rejected(403, "email_verification_required", "")
        scrolled("intent-editor:save")
        compose.onNodeWithTag("intent-editor:save").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("intent-editor:refused").assertExists()
        compose.onNodeWithTag("intent-editor").assertIsDisplayed()
    }

    // --- someone else's --------------------------------------------------------------------------

    @Test
    fun `someone else's intention can be written about but not answered going`() {
        start()

        compose.onNodeWithTag("intent:${intents.community.first().id}").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("intent:page").assertIsDisplayed()
        scrolled("intent:write")
        compose.onNodeWithTag("intent:write").assertIsDisplayed()
        compose.onNodeWithTag("intent:author").assertIsDisplayed()
        compose.onNodeWithTag("intent:edit").assertDoesNotExist()
        compose.onNodeWithText("Иду").assertDoesNotExist()
        compose.onNodeWithText("Это намерение, а не мероприятие", substring = true).assertExists()
    }

    @Test
    fun `an intention that is gone is shown as unavailable and leads back to the list`() {
        val intents = FakeIntents(community = listOf(sampleIntent(1)))
        start(intents)
        compose.onNodeWithTag("intent:${intents.community.first().id}").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("intent:page").assertIsDisplayed()

        intents.community = emptyList()
        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag("intents:empty").assertIsDisplayed()
    }

    // --- one's own -------------------------------------------------------------------------------

    @Test
    fun `an own intention can be cancelled after a question, and stays as cancelled`() {
        val intents = FakeIntents(mine = listOf(own))
        start(intents)
        openMine()
        compose.onNodeWithTag("intent:${own.id}").performClick()
        compose.waitForIdle()

        scrolled("intent:cancel")
        compose.onNodeWithTag("intent:cancel").performClick()
        compose.onNodeWithText("Отменить намерение?").assertIsDisplayed()
        assertThat(intents.cancelled).isEmpty()
        compose.onNodeWithTag("intent:confirm").performClick()
        compose.waitForIdle()

        assertThat(intents.cancelled).containsExactly(own.id)
        compose.onNodeWithTag("intent:status").assertTextContains("Отменено")
        compose.onNodeWithTag("intent:edit").assertDoesNotExist()
        assertThat(intents.mine.single().status).isEqualTo(IntentStatus.Cancelled)
    }

    @Test
    fun `deleting asks first and then leaves the page`() {
        val intents = FakeIntents(mine = listOf(own))
        start(intents)
        openMine()
        compose.onNodeWithTag("intent:${own.id}").performClick()
        compose.waitForIdle()

        scrolled("intent:delete")
        compose.onNodeWithTag("intent:delete").performClick()
        compose.onNodeWithText("Удалить намерение?").assertIsDisplayed()
        compose.onNodeWithTag("intent:confirm").performClick()
        compose.waitForIdle()

        assertThat(intents.deleted).containsExactly(own.id)
        compose.onNodeWithTag("intents:list").assertDoesNotExist()
        compose.onNodeWithTag("intents:empty").assertIsDisplayed()
    }

    @Test
    fun `an own intention is changed in the form and the page shows the change`() {
        val intents = FakeIntents(mine = listOf(own))
        start(intents)
        openMine()
        compose.onNodeWithTag("intent:${own.id}").performClick()
        compose.waitForIdle()

        scrolled("intent:edit")
        compose.onNodeWithTag("intent:edit").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("intent-editor").assertIsDisplayed()
        scrolled("intent-editor:visibility:community")
        compose.onNodeWithTag("intent-editor:visibility:community").performClick()
        scrolled("intent-editor:save")
        compose.onNodeWithTag("intent-editor:save").performClick()
        compose.waitForIdle()

        assertThat(intents.replaced.single().third).isEqualTo(own.version)
        assertThat(intents.replaced.single().second.visibility)
            .isEqualTo(IntentVisibility.Community)
        compose.onNodeWithTag("intent:page").assertIsDisplayed()
    }
}
