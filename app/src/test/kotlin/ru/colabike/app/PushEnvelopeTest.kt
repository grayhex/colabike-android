package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import ru.colabike.app.push.DropReason
import ru.colabike.app.push.ParsedPush
import ru.colabike.app.push.PushEnvelopes

/**
 * The envelope a transport carries, read against the backend's own examples (pinned, see
 * `core/network/src/test/resources/contracts/notifications/v1/SOURCE.txt`): what is shown, what is
 * shown in spite of oddities, and what is dropped, with the reason.
 */
class PushEnvelopeTest {
    private val payload: JsonObject =
        Json.parseToJsonElement(
                requireNotNull(javaClass.getResource("/notifications/v1/payload.json")).readText()
            )
            .jsonObject

    /**
     * After the creation and before the expiry of every example (a message of a chat lasts 12
     * hours).
     */
    private val now = Instant.parse("2026-10-04T10:00:00Z")
    private val binding = 4

    private fun cases(kind: String) = payload[kind]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.name() = this["name"]!!.jsonPrimitive.content

    private fun JsonObject.envelope() = this["envelope"]!!.toString()

    @Test
    fun `every valid example of the backend is shown, with its target in ids`() {
        cases("valid").forEach { case ->
            val parsed = PushEnvelopes.parse(case.envelope(), now, binding)

            assertWithMessage(case.name()).that(parsed).isInstanceOf(ParsedPush.Valid::class.java)
            val envelope = (parsed as ParsedPush.Valid).envelope
            val wire = case["envelope"]!!.jsonObject
            assertThat(envelope.eventId).isEqualTo(wire["eventId"]!!.jsonPrimitive.content)
            assertThat(envelope.title).isEqualTo(wire["title"]!!.jsonPrimitive.content)
            assertThat(envelope.group).isEqualTo(wire["group"]!!.jsonPrimitive.content)
            assertThat(envelope.bindingGeneration).isEqualTo(binding)
        }
    }

    @Test
    fun `a ride's notification carries its date and the version of its agreements`() {
        val reminder = cases("valid").first { it.name() == "ride_reminder" }

        val envelope =
            (PushEnvelopes.parse(reminder.envelope(), now, binding) as ParsedPush.Valid).envelope

        assertThat(envelope.target.type).isEqualTo("ride")
        assertThat(envelope.target.occurrenceAt).isNotNull()
        assertThat(envelope.target.agreementRevision).isAtLeast(1)
        assertThat(envelope.expiresAt).isLessThan(envelope.target.occurrenceAt)
    }

    @Test
    fun `a closed plan's text is the general one and says so`() {
        val closed = cases("valid").first { it.name() == "invitation_to_a_closed_plan" }

        val envelope =
            (PushEnvelopes.parse(closed.envelope(), now, binding) as ParsedPush.Valid).envelope

        assertThat(envelope.neutral).isTrue()
    }

    @Test
    fun `an extra field, an unknown category and an unknown target type are tolerated`() {
        cases("tolerated").forEach { case ->
            val parsed = PushEnvelopes.parse(case.envelope(), now, binding)

            assertWithMessage(case.name()).that(parsed).isInstanceOf(ParsedPush.Valid::class.java)
        }
        val category = cases("tolerated").first { it.name() == "unknown_category" }
        val envelope =
            (PushEnvelopes.parse(category.envelope(), now, binding) as ParsedPush.Valid).envelope
        // Kept as words: the renderer files it under the general channel.
        assertThat(envelope.category).isNotEmpty()
    }

    @Test
    fun `what the backend says to drop is dropped, for the reason it gives`() {
        val expected =
            mapOf(
                "unsupported_version" to DropReason.UnsupportedVersion,
                "expired" to DropReason.Expired,
                "other_binding" to DropReason.OtherBinding,
                "text_too_long" to DropReason.Malformed,
                "no_target" to DropReason.Malformed,
                "not_a_uuid" to DropReason.Malformed,
            )
        val dropped = cases("dropped")
        assertThat(dropped.map { it.name() }).containsExactlyElementsIn(expected.keys)
        dropped.forEach { case ->
            val at = case["now"]?.jsonPrimitive?.content?.let(Instant::parse) ?: now
            val held = case["clientBindingGeneration"]?.jsonPrimitive?.content?.toInt() ?: binding

            val parsed = PushEnvelopes.parse(case.envelope(), at, held)

            assertWithMessage(case.name())
                .that(parsed)
                .isEqualTo(ParsedPush.Dropped(expected.getValue(case.name())))
        }
    }

    @Test
    fun `a phone with no binding shows nothing, whatever the message says`() {
        cases("valid").forEach { case ->
            assertWithMessage(case.name())
                .that(PushEnvelopes.parse(case.envelope(), now, binding = null))
                .isEqualTo(ParsedPush.Dropped(DropReason.OtherBinding))
        }
    }

    @Test
    fun `a message that is not an envelope is dropped without a trace`() {
        listOf("", "not json", "[]", "42", "{}", """{"v":1}""", """{"v":"1"}""").forEach { raw ->
            assertWithMessage(raw)
                .that(PushEnvelopes.parse(raw, now, binding))
                .isEqualTo(ParsedPush.Dropped(DropReason.Malformed))
        }
        val huge = """{"v":1,"pad":"${"x".repeat(PushEnvelopes.MAX_BYTES)}"}"""
        assertThat(PushEnvelopes.parse(huge, now, binding))
            .isEqualTo(ParsedPush.Dropped(DropReason.TooLarge))
    }

    @Test
    fun `an id or a date that is not one makes the message malformed`() {
        val valid = cases("valid").first().envelope()
        listOf(
                valid.replace("\"eventId\":\"", "\"eventId\":\"x"),
                valid.replace("\"expiresAt\":\"", "\"expiresAt\":\"not-a-date"),
                valid.replace("\"bindingGeneration\":4", "\"bindingGeneration\":0"),
                valid.replace("\"neutral\":false", "\"neutral\":\"no\""),
            )
            .forEach { raw ->
                assertThat(PushEnvelopes.parse(raw, now, binding))
                    .isEqualTo(ParsedPush.Dropped(DropReason.Malformed))
            }
    }

    private val chat =
        """
        {"v":1,"deliveryId":"00000000-0000-4000-8000-0000000000d6",
         "eventId":"00000000-0000-4000-8000-000000000072","bindingGeneration":4,
         "createdAt":"2026-10-04T09:00:00.000Z","category":"chat","type":"chat_message",
         "expiresAt":"2026-10-06T21:00:00.000Z","neutral":false,"title":"Новое сообщение",
         "body":"От: Анна Райдер",
         "group":"chat:colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c",
         "target":{"id":null,"commentId":null,"occurrenceAt":null,"agreementRevision":null,
         "type":"chat","ref":"colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c"}}
        """
            .trimIndent()

    @Test
    fun `a message of a chat carries its conversation, and no object`() {
        val parsed = PushEnvelopes.parse(chat, now, binding)

        assertThat(parsed).isInstanceOf(ParsedPush.Valid::class.java)
        val target = (parsed as ParsedPush.Valid).envelope.target
        assertThat(target.type).isEqualTo("chat")
        assertThat(target.id).isNull()
        assertThat(target.ref).isEqualTo("colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c")
        assertThat(parsed.envelope.category).isEqualTo("chat")
    }

    @Test
    fun `a conversation that is not named as the chat names them makes the message malformed`() {
        listOf(
                chat.replace(
                    "colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c\"}}",
                    "../me\"}}",
                ),
                chat.replace(
                    "\"ref\":\"colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c\"",
                    "\"ref\":7",
                ),
                chat.replace(
                    "\"ref\":\"colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c\"",
                    "\"ref\":\"a b:c\"",
                ),
                chat.replace(
                    "\"ref\":\"colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c\"",
                    "\"ref\":\"" + "x".repeat(130) + "\"",
                ),
            )
            .forEach { raw ->
                assertThat(PushEnvelopes.parse(raw, now, binding))
                    .isEqualTo(ParsedPush.Dropped(DropReason.Malformed))
            }
    }

    @Test
    fun `a target without a conversation is still a message, shown to the list`() {
        val raw =
            chat.replace(
                ",\"ref\":\"colabike:dm_3f1c0a9e7d5b4c2a8e6f1d0b9a7c5e3f2b4d6a8c\"",
                "",
            )

        val parsed = PushEnvelopes.parse(raw, now, binding)

        assertThat((parsed as ParsedPush.Valid).envelope.target.ref).isNull()
    }

    @Test
    fun `printing an envelope shows no text`() {
        val envelope =
            (PushEnvelopes.parse(cases("valid").first().envelope(), now, binding)
                    as ParsedPush.Valid)
                .envelope

        assertThat(envelope.toString()).doesNotContain(envelope.title)
        envelope.body?.let { assertThat(envelope.toString()).doesNotContain(it) }
    }
}
