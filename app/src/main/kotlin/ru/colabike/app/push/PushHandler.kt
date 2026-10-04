package ru.colabike.app.push

import java.time.Clock
import java.time.Instant

/** The account a phone is bound to for push, and the generation of that binding. */
data class PushBound(val accountId: String, val generation: Int)

/**
 * The phone's binding to the account for push (the device registry, cola#342). Without one nothing
 * may be shown: a message that arrives for a phone that is not bound is not for this person.
 */
fun interface PushBinding {
    fun current(): PushBound?
}

/** No registry yet: no binding, and so no push is ever shown. */
object NoPushBinding : PushBinding {
    override fun current(): PushBound? = null
}

/** The person's own limits on what is shown (quiet hours, a muted author or object). */
fun interface PushPolicy {
    fun allows(envelope: PushEnvelope, now: Instant): Boolean

    companion object {
        val AllowAll = PushPolicy { _, _ -> true }
    }
}

/** What is put on screen. */
interface PushSurface {
    /** Shows [envelope] for [account]; false when the system will not show notifications. */
    fun show(envelope: PushEnvelope, account: String): Boolean

    /** Takes down everything the app has shown. */
    fun cancelAll()
}

/** What came of a message. */
sealed interface PushOutcome {
    data object Shown : PushOutcome

    data class Dropped(val reason: DropReason) : PushOutcome

    /** The same delivery or event again. */
    data object Duplicate : PushOutcome

    /** Older than what is already shown for the same stack. */
    data object Stale : PushOutcome

    /** The person's own limits say no. */
    data object Muted : PushOutcome

    /** The system does not allow notifications (permission or the app's switch). */
    data object NotificationsOff : PushOutcome
}

/**
 * One message from the transport to one notification, or to nothing. It reads and checks the
 * envelope against the current binding and the time, remembers it so that it is not shown twice,
 * asks the person's limits, and only then shows it. It does no work that waits: no download, no
 * session, no network; what the notification opens is loaded after the tap, with the person's own
 * session.
 */
class PushHandler(
    private val binding: PushBinding,
    private val ledger: DeliveryLedger,
    private val surface: PushSurface,
    private val policy: PushPolicy = PushPolicy.AllowAll,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun onMessage(raw: String): PushOutcome {
        val now = Instant.now(clock)
        val bound = binding.current()
        val envelope =
            when (val parsed = PushEnvelopes.parse(raw, now, bound?.generation)) {
                is ParsedPush.Dropped -> return PushOutcome.Dropped(parsed.reason)
                is ParsedPush.Valid -> parsed.envelope
            }
        when (ledger.admit(envelope)) {
            Admission.Duplicate -> return PushOutcome.Duplicate
            Admission.Stale -> return PushOutcome.Stale
            Admission.New -> Unit
        }
        if (!policy.allows(envelope, now)) return PushOutcome.Muted
        // `bound` is not null here: a message without a binding is dropped as another binding.
        return if (surface.show(envelope, bound!!.accountId)) PushOutcome.Shown
        else PushOutcome.NotificationsOff
    }

    /**
     * A sign-out, a switch of account: nothing that was shown for the one who left stays in the
     * tray, and nothing of what was delivered is remembered. Works without a network.
     */
    fun onSignedOut() {
        surface.cancelAll()
        ledger.clear()
    }
}
