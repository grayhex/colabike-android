package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.messages.WriteState
import ru.colabike.app.messages.WriteViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.DataError
import ru.colabike.core.model.UserId

/** "Write" on a person's page. */
class WriteViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val chat = FakeChat()

    @Test
    fun `a tap opens the dialogue with that person and hands the channel over`() = runTest {
        chat.cid = ChannelCid("messaging:dm-7")
        val viewModel = WriteViewModel(chat)

        viewModel.write(UserId("u-7"))

        assertThat(chat.opened)
            .containsExactly(FakeChat.Opened(ChatChannelKind.Dm, listOf(UserId("u-7")), null, null))
        assertThat(viewModel.opened.value).isEqualTo(ChannelCid("messaging:dm-7"))
        assertThat(viewModel.state.value).isEqualTo(WriteState.Idle)

        viewModel.consumed()
        assertThat(viewModel.opened.value).isNull()
    }

    @Test
    fun `a person who cannot be written to is told so in words`() = runTest {
        chat.openError = DataError.NotFound()
        val viewModel = WriteViewModel(chat)

        viewModel.write(UserId("u-7"))

        val failed = viewModel.state.value as WriteState.Failed
        assertThat((failed.message as UiText.Res).id).isEqualTo(R.string.chat_cannot_write)
        assertThat(viewModel.opened.value).isNull()
    }

    @Test
    fun `an unconfirmed e-mail is told, not hidden behind a generic error`() = runTest {
        chat.openError = DataError.Rejected(403, "email_verification_required", "")
        val viewModel = WriteViewModel(chat)

        viewModel.write(UserId("u-7"))

        val failed = viewModel.state.value as WriteState.Failed
        assertThat((failed.message as UiText.Res).id).isEqualTo(R.string.chat_email_unconfirmed)
    }

    @Test
    fun `after a failure the next tap tries again`() = runTest {
        chat.openError = DataError.Server(500, null)
        val viewModel = WriteViewModel(chat)
        viewModel.write(UserId("u-7"))

        viewModel.write(UserId("u-7"))

        assertThat(chat.opened).hasSize(2)
        assertThat(viewModel.state.value).isEqualTo(WriteState.Idle)
        assertThat(viewModel.opened.value).isNotNull()
    }
}
