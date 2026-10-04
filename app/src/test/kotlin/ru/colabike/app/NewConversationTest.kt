package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.messages.GROUP_MAX
import ru.colabike.app.messages.NewConversationViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.ChatPeople
import ru.colabike.core.model.DataError
import ru.colabike.core.model.UserId

/** Who to write to: the people, the search, one dialogue or a group. */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationTest {
    @get:Rule val main = MainDispatcherRule()

    private val chat = FakeChat()
    private var keys = 0

    private fun viewModel() = NewConversationViewModel(chat, debounceMs = 400) { "key-${++keys}" }

    private fun person(n: Int) = people(n, 1).single().person

    @Test
    fun `it opens on the people the person follows`() = runTest {
        val viewModel = viewModel()

        val state = viewModel.state.value

        assertThat(chat.queries).containsExactly("")
        assertThat(state.loading).isFalse()
        assertThat(state.searching).isFalse()
        assertThat(state.people).hasSize(3)
    }

    @Test
    fun `typing waits for a pause and then searches once`() = runTest {
        chat.peopleAnswer = { ChatPeople(listOf(person(9)), searching = true) }
        val viewModel = viewModel()

        viewModel.onQuery("Рай")
        viewModel.onQuery("Райд")
        assertThat(chat.queries).containsExactly("")
        advanceTimeBy(399)
        assertThat(chat.queries).containsExactly("")
        advanceTimeBy(2)
        runCurrent()

        assertThat(chat.queries).containsExactly("", "Райд").inOrder()
        assertThat(viewModel.state.value.searching).isTrue()
        assertThat(viewModel.state.value.people.map { it.id.value }).containsExactly("u9")
    }

    @Test
    fun `a failed search is shown in words and a retry searches again`() = runTest {
        chat.peopleError = DataError.Offline(java.io.IOException("x"))
        val viewModel = viewModel()
        assertThat(viewModel.state.value.error).isEqualTo(UiText.Res(R.string.error_offline))

        viewModel.retry()

        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.people).isNotEmpty()
    }

    @Test
    fun `a tap on a person opens the dialogue and hands the channel over`() = runTest {
        chat.cid = ChannelCid("messaging:dm-3")
        val viewModel = viewModel()

        viewModel.onPerson(person(1))

        assertThat(chat.opened)
            .containsExactly(FakeChat.Opened(ChatChannelKind.Dm, listOf(UserId("u1")), null, null))
        assertThat(viewModel.opened.value).isEqualTo(ChannelCid("messaging:dm-3"))
        viewModel.consumed()
        assertThat(viewModel.opened.value).isNull()
    }

    @Test
    fun `a person who cannot be written to stays on the list with the reason`() = runTest {
        chat.openError = DataError.NotFound()
        val viewModel = viewModel()

        viewModel.onPerson(person(1))

        assertThat(viewModel.state.value.openError)
            .isEqualTo(UiText.Res(R.string.chat_cannot_write))
        assertThat(viewModel.state.value.opening).isFalse()
        assertThat(viewModel.opened.value).isNull()
        // The next tap goes through.
        viewModel.onPerson(person(2))
        assertThat(viewModel.state.value.openError).isNull()
        assertThat(viewModel.opened.value).isNotNull()
    }

    @Test
    fun `in group mode a tap selects and a second tap unselects`() = runTest {
        val viewModel = viewModel()
        viewModel.setGroup(true)

        viewModel.onPerson(person(1))
        viewModel.onPerson(person(2))
        viewModel.onPerson(person(1))

        assertThat(viewModel.state.value.selected.map { it.id.value }).containsExactly("u2")
        assertThat(chat.opened).isEmpty()
    }

    @Test
    fun `a group takes at most seven people`() = runTest {
        val viewModel = viewModel()
        viewModel.setGroup(true)

        (1..GROUP_MAX + 3).forEach { viewModel.onPerson(person(it)) }

        assertThat(viewModel.state.value.selected).hasSize(GROUP_MAX)
    }

    @Test
    fun `a group needs two people and a name`() = runTest {
        val viewModel = viewModel()
        viewModel.setGroup(true)
        viewModel.onPerson(person(1))
        viewModel.onGroupName("Субботний заезд")
        assertThat(viewModel.state.value.canCreateGroup).isFalse()

        viewModel.createGroup()
        assertThat(chat.opened).isEmpty()

        viewModel.onPerson(person(2))
        assertThat(viewModel.state.value.canCreateGroup).isTrue()
        viewModel.onGroupName("   ")
        assertThat(viewModel.state.value.canCreateGroup).isFalse()
    }

    @Test
    fun `a name is cut at the server's limit`() = runTest {
        val viewModel = viewModel()
        viewModel.setGroup(true)

        viewModel.onGroupName("я".repeat(200))

        assertThat(viewModel.state.value.groupName).hasLength(80)
    }

    @Test
    fun `a group is made with its members, its trimmed name and a key`() = runTest {
        val viewModel = viewModel()
        viewModel.setGroup(true)
        viewModel.onPerson(person(1))
        viewModel.onPerson(person(2))
        viewModel.onGroupName("  Субботний заезд ")

        viewModel.createGroup()

        assertThat(chat.opened)
            .containsExactly(
                FakeChat.Opened(
                    ChatChannelKind.Group,
                    listOf(UserId("u1"), UserId("u2")),
                    "Субботний заезд",
                    "key-1",
                )
            )
        assertThat(viewModel.opened.value).isNotNull()
    }

    @Test
    fun `a repeat of the same group carries the same key, a changed group a new one`() = runTest {
        chat.openError = DataError.Offline(java.io.IOException("lost answer"))
        val viewModel = viewModel()
        viewModel.setGroup(true)
        viewModel.onPerson(person(1))
        viewModel.onPerson(person(2))
        viewModel.onGroupName("Заезд")

        viewModel.createGroup()
        viewModel.createGroup()
        viewModel.onGroupName("Заезд 2")
        viewModel.createGroup()

        assertThat(chat.opened.map { it.key }).containsExactly("key-1", "key-1", "key-2").inOrder()
    }

    @Test
    fun `switching the mode clears the choice`() = runTest {
        val viewModel = viewModel()
        viewModel.setGroup(true)
        viewModel.onPerson(person(1))

        viewModel.setGroup(false)

        assertThat(viewModel.state.value.selected).isEmpty()
    }
}
