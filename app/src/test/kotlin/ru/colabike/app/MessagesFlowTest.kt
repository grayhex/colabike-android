package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import ru.colabike.app.links.LinkOpener
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.messages.ChatConnectException
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.UserId

/** The Messages section: from the tab to a conversation, a new one, and "Write" on a page. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class MessagesFlowTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val stranger =
        Relationship(isSelf = false, following = false, followedBy = false, friends = false)

    private fun dependencies(
        signedIn: Boolean = true,
        chat: FakeChat = FakeChat(),
        gateway: FakeChatGateway = FakeChatGateway(),
        screens: FakeChatScreens = FakeChatScreens(),
    ): FakeDependencies {
        val sessionScope =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        return FakeDependencies(
            bikes = FakeBikes(mapOf(null to Page(bikes(0, 3), null))),
            people =
                FakePeople(
                    profiles =
                        mapOf(PreviewData.rider.id.value to profileOf(PreviewData.rider, stranger))
                ),
            chat = chat,
            chatGateway = gateway,
            chatSession = ru.colabike.app.messages.ChatSession(chat, gateway, sessionScope),
            chatScreens = screens,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )
    }

    private fun start(dependencies: FakeDependencies) {
        compose.setContent {
            ColaBikeTheme {
                CompositionLocalProvider(LocalLinkOpener provides LinkOpener { opened += it }) {
                    ColaBikeApp(dependencies)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun openMessages() {
        compose.section("Сообщения").performClick()
        compose.waitForIdle()
    }

    private fun settle() {
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.waitForIdle()
    }

    @Test
    fun `the tab connects the chat and shows the conversations, and nothing connected before`() {
        val dependencies = dependencies()
        start(dependencies)
        assertThat(dependencies.chat.credentialCalls).isEqualTo(0)

        openMessages()

        assertThat(dependencies.chatGateway.connected).hasSize(1)
        compose.onNodeWithTag("chat:list").assertIsDisplayed()
        compose.onNodeWithText("Анна Шоссейная").assertIsDisplayed()
        compose.onNodeWithText("Субботний заезд").assertIsDisplayed()
    }

    @Test
    fun `a tap on a conversation opens it, and back returns to the list`() {
        start(dependencies())
        openMessages()

        compose.onNodeWithTag("chat:open:dm-1").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("chat:conversation").assertIsDisplayed()
        compose.onNodeWithText("Диалог colabike:dm-1").assertIsDisplayed()
        // The conversation takes the whole screen: the bar of the sections steps aside.
        compose.onNode(sectionTab("Лента")).assertDoesNotExist()

        compose.onNodeWithTag("chat:back").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("chat:list").assertIsDisplayed()
    }

    @Test
    fun `a guest is asked to sign in, and the chat is never asked for`() {
        val dependencies = dependencies(signedIn = false)
        start(dependencies)

        openMessages()

        compose.onNodeWithText("Сообщения — для участников").assertIsDisplayed()
        assertThat(dependencies.chat.credentialCalls).isEqualTo(0)
        assertThat(dependencies.chatGateway.connected).isEmpty()
        compose.onNodeWithTag("chat:list").assertDoesNotExist()
    }

    @Test
    fun `while the chat comes up the person sees it is loading`() {
        val gateway = FakeChatGateway().apply { hold = CompletableDeferred() }
        start(dependencies(gateway = gateway))

        openMessages()

        compose.onNodeWithTag("chat:list").assertDoesNotExist()
        compose
            .onNode(
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                    SemanticsProperties.ProgressBarRangeInfo
                )
            )
            .assertExists()
    }

    @Test
    fun `an unconfirmed e-mail is explained with a way to the site and a retry`() {
        val chat =
            FakeChat().apply {
                credentialsError = DataError.Rejected(403, "email_verification_required", "")
            }
        val dependencies = dependencies(chat = chat)
        start(dependencies)

        openMessages()

        compose.onNodeWithText("Сообщения закрыты").assertIsDisplayed()
        compose.onNodeWithTag("chat:list").assertDoesNotExist()
        compose.onNodeWithText("Подтвердить почту на сайте").performClick()
        assertThat(opened.single()).isEqualTo("https://colabike.test/account?tab=account")

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chat:list").assertIsDisplayed()
    }

    @Test
    fun `a chat that is off says so, and the retry brings it up`() {
        val chat = FakeChat().apply { credentialsError = DataError.Server(503, "req-1") }
        start(dependencies(chat = chat))

        openMessages()

        compose
            .onNodeWithText("Сообщения сейчас отключены или недоступны", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("chat:list").assertIsDisplayed()
    }

    @Test
    fun `no network and a provider that refuses are failures with a retry`() {
        val chat =
            FakeChat().apply { credentialsError = DataError.Offline(java.io.IOException("x")) }
        val gateway = FakeChatGateway()
        start(dependencies(chat = chat, gateway = gateway))
        openMessages()
        compose.onNodeWithText("Повторить").assertIsDisplayed()

        gateway.failure = ChatConnectException("refused")
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()
        compose
            .onNodeWithText("Не удалось подключиться к сообщениям", substring = true)
            .assertIsDisplayed()

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chat:list").assertIsDisplayed()
    }

    // --- a new conversation --------------------------------------------------------------

    private fun openNewConversation() {
        openMessages()
        compose.onNodeWithContentDescription("Новое сообщение").performClick()
        settle()
    }

    @Test
    fun `new conversation lists the people to write to and opens the dialogue with a tap`() {
        val chat = FakeChat().apply { cid = ChannelCid("messaging:dm-42") }
        start(dependencies(chat = chat))

        openNewConversation()
        compose.onNodeWithText("Райдер 0").assertIsDisplayed()
        compose.onNodeWithTag("chat:person:u0").performClick()
        settle()

        assertThat(chat.opened.single().kind).isEqualTo(ChatChannelKind.Dm)
        assertThat(chat.opened.single().members).containsExactly(UserId("u0"))
        compose.onNodeWithText("Диалог messaging:dm-42").assertIsDisplayed()
        // Back from the dialogue goes to the list, not to the form that made it.
        compose.onNodeWithTag("chat:back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chat:list").assertIsDisplayed()
    }

    @Test
    fun `a person who cannot be written to is explained and the list stays`() {
        val chat = FakeChat().apply { openError = DataError.NotFound() }
        start(dependencies(chat = chat))

        openNewConversation()
        compose.onNodeWithTag("chat:person:u0").performClick()
        settle()

        compose
            .onNodeWithText("Этому человеку нельзя написать", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("chat:people").assertIsDisplayed()
    }

    @Test
    fun `typing searches after a pause`() {
        val chat = FakeChat()
        start(dependencies(chat = chat))
        openNewConversation()

        compose.onNodeWithTag("chat:people:field").performTextInput("ива")
        settle()

        assertThat(chat.queries).containsExactly("", "ива").inOrder()
    }

    @Test
    fun `a group needs two people and a name before it can be made`() {
        val chat = FakeChat().apply { cid = ChannelCid("messaging:group-1") }
        start(dependencies(chat = chat))
        openNewConversation()

        compose.onNodeWithText("Группа").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chat:group:create").assertIsNotEnabled()
        compose.onNodeWithTag("chat:person:u0").performClick()
        compose.onNodeWithTag("chat:group:name").performTextInput("Субботний заезд")
        compose.onNodeWithTag("chat:group:create").assertIsNotEnabled()
        compose.onNodeWithTag("chat:person:u1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chat:group:create").assertIsEnabled()
        compose.onNodeWithTag("chat:group:create").performClick()
        settle()

        val made = chat.opened.single()
        assertThat(made.kind).isEqualTo(ChatChannelKind.Group)
        assertThat(made.members).containsExactly(UserId("u0"), UserId("u1")).inOrder()
        assertThat(made.name).isEqualTo("Субботний заезд")
        assertThat(made.key).isNotEmpty()
        compose.onNodeWithText("Диалог messaging:group-1").assertIsDisplayed()
    }

    // --- "Write" on a person's page -----------------------------------------------------------

    private fun openAuthor() {
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Тестовый Райдер").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `write on a page opens the dialogue with that person`() {
        val chat = FakeChat().apply { cid = ChannelCid("messaging:dm-9") }
        start(dependencies(chat = chat))
        openAuthor()

        compose.onNodeWithContentDescription("Написать: Тестовый Райдер").performClick()
        settle()

        assertThat(chat.opened.single().members).containsExactly(PreviewData.rider.id)
        compose.onNodeWithText("Диалог messaging:dm-9").assertIsDisplayed()
    }

    @Test
    fun `write on a page when the e-mail is unconfirmed says why in words`() {
        val chat =
            FakeChat().apply {
                openError = DataError.Rejected(403, "email_verification_required", "")
            }
        start(dependencies(chat = chat))
        openAuthor()

        compose.onNodeWithContentDescription("Написать: Тестовый Райдер").performClick()
        settle()

        compose.onNode(hasText("Подтвердите почту на сайте", substring = true)).assertExists()
    }

    @Test
    fun `a guest on a page is asked to sign in instead of writing`() {
        val chat = FakeChat()
        start(dependencies(signedIn = false, chat = chat))
        openAuthor()

        compose.onNodeWithContentDescription("Написать: Тестовый Райдер").performClick()
        compose.waitForIdle()

        assertThat(chat.opened).isEmpty()
        compose.onAllNodesWithText("Войти", substring = true).onFirst().assertExists()
    }
}
