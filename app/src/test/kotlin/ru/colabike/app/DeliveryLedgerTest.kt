package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import ru.colabike.app.push.Admission
import ru.colabike.app.push.DeliveryLedger
import ru.colabike.app.push.LedgerStore
import ru.colabike.app.push.PushEnvelope
import ru.colabike.app.push.PushTarget

/** The short memory that keeps a repeat from ringing twice and an old message from winning. */
class DeliveryLedgerTest {
    private class Memory(var text: String? = null) : LedgerStore {
        override fun read() = text

        override fun write(value: String) {
            text = value
        }
    }

    private fun envelope(
        n: Int,
        event: Int = n,
        group: String = "g$n",
        at: String = "2026-10-04T09:00:00Z",
    ) =
        PushEnvelope(
            deliveryId = "00000000-0000-4000-8000-%012d".format(n),
            eventId = "00000000-0000-4000-9000-%012d".format(event),
            bindingGeneration = 4,
            category = "discussions",
            type = "reply",
            createdAt = Instant.parse(at),
            expiresAt = Instant.parse("2026-10-11T09:00:00Z"),
            neutral = false,
            title = "Ответ",
            body = null,
            group = group,
            target = PushTarget("bike", "00000000-0000-4000-8000-00000000000a", null, null, null),
        )

    @Test
    fun `a message is new once, a repeat of the delivery is not`() {
        val ledger = DeliveryLedger(Memory())

        assertThat(ledger.admit(envelope(1))).isEqualTo(Admission.New)
        assertThat(ledger.admit(envelope(1))).isEqualTo(Admission.Duplicate)
    }

    @Test
    fun `the same event sent again as another delivery is a repeat too`() {
        val ledger = DeliveryLedger(Memory())
        ledger.admit(envelope(1, event = 7))

        assertThat(ledger.admit(envelope(2, event = 7))).isEqualTo(Admission.Duplicate)
    }

    @Test
    fun `news of a stack replaces the earlier, and an old message that comes late does not`() {
        val ledger = DeliveryLedger(Memory())

        assertThat(ledger.admit(envelope(1, group = "ride:a", at = "2026-10-04T09:00:00Z")))
            .isEqualTo(Admission.New)
        assertThat(ledger.admit(envelope(2, group = "ride:a", at = "2026-10-04T10:00:00Z")))
            .isEqualTo(Admission.New)
        // The earlier change arrives after the later one.
        assertThat(ledger.admit(envelope(3, group = "ride:a", at = "2026-10-04T09:30:00Z")))
            .isEqualTo(Admission.Stale)
        // Another ride is its own stack.
        assertThat(ledger.admit(envelope(4, group = "ride:b", at = "2026-10-04T08:00:00Z")))
            .isEqualTo(Admission.New)
    }

    @Test
    fun `it is bounded, and forgets the oldest first`() {
        val ledger = DeliveryLedger(Memory(), capacity = 3)
        (1..4).forEach { ledger.admit(envelope(it)) }

        // 1 was forgotten (it could be shown again); 4 is still remembered.
        assertThat(ledger.admit(envelope(4))).isEqualTo(Admission.Duplicate)
        assertThat(ledger.admit(envelope(1))).isEqualTo(Admission.New)
    }

    @Test
    fun `it survives a restart, and a damaged memory is a fresh start`() {
        val store = Memory()
        DeliveryLedger(store).admit(envelope(1))

        assertThat(DeliveryLedger(store).admit(envelope(1))).isEqualTo(Admission.Duplicate)

        store.text = "{ this is not what was written"
        assertThat(DeliveryLedger(store).admit(envelope(1))).isEqualTo(Admission.New)
    }

    @Test
    fun `a sign-out forgets everything`() {
        val store = Memory()
        val ledger = DeliveryLedger(store)
        ledger.admit(envelope(1))

        ledger.clear()

        assertThat(ledger.admit(envelope(1))).isEqualTo(Admission.New)
    }

    @Test
    fun `what is kept holds ids and times, never text`() {
        val store = Memory()
        DeliveryLedger(store).admit(envelope(1).copy(title = "Секретное название", body = "Тело"))

        assertThat(store.text).doesNotContain("Секретное")
        assertThat(store.text).doesNotContain("Тело")
    }
}
