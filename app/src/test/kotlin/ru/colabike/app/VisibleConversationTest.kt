package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import ru.colabike.app.push.PushEnvelope
import ru.colabike.app.push.PushTarget
import ru.colabike.app.push.VisibleConversation

/** A message of the conversation in front is already on screen, so it makes no notification. */
class VisibleConversationTest {
    private val cid = "colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c"
    private val other = "colabike:dm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private fun message(ref: String?, type: String = "chat") =
        PushEnvelope(
            deliveryId = "00000000-0000-4000-8000-0000000000d6",
            eventId = "00000000-0000-4000-8000-000000000072",
            bindingGeneration = 4,
            category = "chat",
            type = "chat_message",
            createdAt = Instant.parse("2026-10-04T09:00:00Z"),
            expiresAt = Instant.parse("2026-10-04T21:00:00Z"),
            neutral = false,
            title = "Новое сообщение",
            body = null,
            group = "chat:$ref",
            target = PushTarget(type, null, null, null, null, ref),
        )

    @Test
    fun `nothing is in front at first`() {
        assertThat(VisibleConversation().isShowing(message(cid))).isFalse()
    }

    @Test
    fun `a message of the conversation in front is silent, one of another is not`() {
        val visible = VisibleConversation()
        visible.show(cid)

        assertThat(visible.isShowing(message(cid))).isTrue()
        assertThat(visible.isShowing(message(other))).isFalse()
    }

    @Test
    fun `a message without a conversation, or not of a chat, is never silenced`() {
        val visible = VisibleConversation()
        visible.show(cid)

        assertThat(visible.isShowing(message(null))).isFalse()
        assertThat(visible.isShowing(message(cid, type = "bike"))).isFalse()
    }

    @Test
    fun `leaving the screen makes the next message ring`() {
        val visible = VisibleConversation()
        visible.show(cid)

        visible.hide(cid)

        assertThat(visible.isShowing(message(cid))).isFalse()
    }

    @Test
    fun `a screen that leaves late does not hide the one that came after it`() {
        val visible = VisibleConversation()
        visible.show(cid)
        visible.show(other)

        visible.hide(cid)

        assertThat(visible.isShowing(message(other))).isTrue()
    }
}
