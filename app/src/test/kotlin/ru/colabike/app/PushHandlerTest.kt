package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test
import ru.colabike.app.push.DeliveryLedger
import ru.colabike.app.push.DropReason
import ru.colabike.app.push.LedgerStore
import ru.colabike.app.push.NoPushBinding
import ru.colabike.app.push.PushBinding
import ru.colabike.app.push.PushBound
import ru.colabike.app.push.PushEnvelope
import ru.colabike.app.push.PushHandler
import ru.colabike.app.push.PushOutcome
import ru.colabike.app.push.PushPolicy
import ru.colabike.app.push.PushSurface

/** One message from the transport to one notification, or to nothing. */
class PushHandlerTest {
    private class Memory : LedgerStore {
        var text: String? = null

        override fun read() = text

        override fun write(value: String) {
            text = value
        }
    }

    private class Surface(var allowed: Boolean = true) : PushSurface {
        val shown = mutableListOf<Pair<PushEnvelope, String>>()
        var cancelled = 0

        override fun show(envelope: PushEnvelope, account: String): Boolean {
            if (allowed) shown += envelope to account
            return allowed
        }

        override fun cancelAll() {
            cancelled++
        }
    }

    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val surface = Surface()
    private var binding: PushBinding = PushBinding { PushBound("account-1", 4) }
    private var policy = PushPolicy.AllowAll

    private fun handler() =
        PushHandler(
            binding = { binding.current() },
            ledger = DeliveryLedger(Memory()),
            surface = surface,
            policy = { envelope, at -> policy.allows(envelope, at) },
            clock = clock,
        )

    private fun message(
        delivery: Int = 1,
        event: Int = 1,
        generation: Int = 4,
        expires: String = "2026-10-11T09:00:00.000Z",
        group: String = "bike:a",
        created: String = "2026-10-04T09:00:00.000Z",
    ) =
        """{"v":1,"deliveryId":"00000000-0000-4000-8000-%012d","eventId":"00000000-0000-4000-9000-%012d","bindingGeneration":%d,"category":"discussions","type":"reply","createdAt":"%s","expiresAt":"%s","neutral":false,"title":"Ответ на ваш комментарий","body":null,"group":"%s","target":{"type":"bike","id":"00000000-0000-4000-8000-00000000000a","commentId":null,"occurrenceAt":null,"agreementRevision":null}}"""
            .format(delivery, event, generation, created, expires, group)

    @Test
    fun `a good message for the bound account is shown for that account`() {
        val outcome = handler().onMessage(message())

        assertThat(outcome).isEqualTo(PushOutcome.Shown)
        assertThat(surface.shown.single().second).isEqualTo("account-1")
        assertThat(surface.shown.single().first.title).isEqualTo("Ответ на ваш комментарий")
    }

    @Test
    fun `without a binding nothing is shown, whoever it is for`() {
        binding = NoPushBinding

        assertThat(handler().onMessage(message()))
            .isEqualTo(PushOutcome.Dropped(DropReason.OtherBinding))
        assertThat(surface.shown).isEmpty()
    }

    @Test
    fun `a message of another binding or past its time is dropped before anything is shown`() {
        val handler = handler()

        assertThat(handler.onMessage(message(generation = 3)))
            .isEqualTo(PushOutcome.Dropped(DropReason.OtherBinding))
        assertThat(handler.onMessage(message(delivery = 2, expires = "2026-10-04T10:00:00.000Z")))
            .isEqualTo(PushOutcome.Dropped(DropReason.Expired))
        assertThat(handler.onMessage("rubbish"))
            .isEqualTo(PushOutcome.Dropped(DropReason.Malformed))
        assertThat(surface.shown).isEmpty()
    }

    @Test
    fun `a repeated delivery or event is shown once, a late old one does not win`() {
        val handler = handler()

        assertThat(handler.onMessage(message(delivery = 1, event = 1))).isEqualTo(PushOutcome.Shown)
        assertThat(handler.onMessage(message(delivery = 1, event = 1)))
            .isEqualTo(PushOutcome.Duplicate)
        assertThat(handler.onMessage(message(delivery = 2, event = 1)))
            .isEqualTo(PushOutcome.Duplicate)
        // A later event of the same stack replaces; an earlier one arriving after it is stale.
        assertThat(
                handler.onMessage(
                    message(delivery = 3, event = 3, created = "2026-10-04T11:00:00.000Z")
                )
            )
            .isEqualTo(PushOutcome.Shown)
        assertThat(
                handler.onMessage(
                    message(delivery = 4, event = 4, created = "2026-10-04T10:00:00.000Z")
                )
            )
            .isEqualTo(PushOutcome.Stale)
        assertThat(surface.shown).hasSize(2)
    }

    @Test
    fun `the person's own limits keep it out of the tray`() {
        policy = PushPolicy { _, _ -> false }

        assertThat(handler().onMessage(message())).isEqualTo(PushOutcome.Muted)
        assertThat(surface.shown).isEmpty()
    }

    @Test
    fun `a system that allows no notifications is told apart`() {
        surface.allowed = false

        assertThat(handler().onMessage(message())).isEqualTo(PushOutcome.NotificationsOff)
    }

    @Test
    fun `a sign-out takes down the tray and forgets, without a network`() {
        val handler = handler()
        handler.onMessage(message())

        handler.onSignedOut()

        assertThat(surface.cancelled).isEqualTo(1)
        // The same delivery is new again: the memory of the one who left is gone.
        assertThat(handler.onMessage(message())).isEqualTo(PushOutcome.Shown)
    }
}
