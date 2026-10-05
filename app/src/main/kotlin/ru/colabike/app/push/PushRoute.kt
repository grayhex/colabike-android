package ru.colabike.app.push

import android.content.Context
import android.content.Intent
import ru.colabike.app.navigation.Destination

/**
 * What a tap on a push carries into the app: the notification (to mark it read) and where it leads,
 * all as ids. It travels in an Intent that only the app's own notification can send (to an activity
 * that is not exported), is read back with every field checked, and is never trusted further than
 * the screens it opens: they load what they show with the person's own session, so a push is not a
 * permission.
 */
data class PushTap(val eventId: String, val eventType: String, val target: PushTarget) {
    /**
     * An explicit Intent to the tap activity; nothing in it is an address, an action or a class.
     */
    fun intent(context: Context): Intent =
        Intent(context, NotificationTapActivity::class.java).apply {
            action = ACTION_OPEN
            putExtra(EVENT, eventId)
            putExtra(EVENT_TYPE, eventType)
            putExtra(TARGET_TYPE, target.type)
            target.id?.let { putExtra(TARGET_ID, it) }
            target.commentId?.let { putExtra(COMMENT, it) }
            target.occurrenceAt?.let { putExtra(OCCURRENCE, it.toString()) }
            target.agreementRevision?.let { putExtra(REVISION, it) }
            target.ref?.let { putExtra(REF, it) }
        }

    /**
     * The screen of the app this leads to: the object, its discussion at a comment, or the inbox.
     */
    fun destination(): Destination = target.destination(eventType)

    companion object {
        const val ACTION_OPEN = "ru.colabike.app.action.OPEN_NOTIFICATION"
        private const val EVENT = "event"
        private const val EVENT_TYPE = "event_type"
        private const val TARGET_TYPE = "target_type"
        private const val TARGET_ID = "target_id"
        private const val COMMENT = "comment"
        private const val OCCURRENCE = "occurrence"
        private const val REVISION = "revision"
        private const val REF = "ref"
        private val UUID =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** The tap in [intent], if it is whole and ours; anything else is null. */
        fun from(intent: Intent?): PushTap? {
            if (intent?.action != ACTION_OPEN) return null
            val event = intent.getStringExtra(EVENT)?.takeIf { UUID.matches(it) } ?: return null
            val eventType = intent.getStringExtra(EVENT_TYPE)?.takeIf { it.length in 1..40 }
            val type = intent.getStringExtra(TARGET_TYPE)?.takeIf { it.length in 1..20 }
            if (eventType == null || type == null) return null
            val id = intent.getStringExtra(TARGET_ID)?.also { if (!UUID.matches(it)) return null }
            val comment =
                intent.getStringExtra(COMMENT)?.also { if (!UUID.matches(it)) return null }
            val occurrence =
                intent.getStringExtra(OCCURRENCE)?.let {
                    try {
                        java.time.Instant.parse(it)
                    } catch (_: java.time.format.DateTimeParseException) {
                        return null
                    }
                }
            val revision =
                if (intent.hasExtra(REVISION)) intent.getIntExtra(REVISION, 0).takeIf { it >= 1 }
                else null
            val ref =
                intent.getStringExtra(REF)?.also { if (!PushEnvelopes.CID.matches(it)) return null }
            return PushTap(
                event.lowercase(),
                eventType,
                PushTarget(type, id?.lowercase(), comment?.lowercase(), occurrence, revision, ref),
            )
        }
    }
}

/** The events about one date of a plan: they open the person's part in that date. */
private val DATED_RIDE_EVENTS =
    setOf(
        "ride_invite",
        "ride_changed",
        "ride_cancelled",
        "ride_response",
        "ride_reminder",
        "plan_published",
        "plan_nearby",
    )

/**
 * Where a target leads. An object the app has a screen for opens it (a comment's notification opens
 * the discussion at that comment, a message its conversation); a sign-in used again opens the
 * devices; anything else, a type the app does not know included, opens the inbox, where the
 * notification is listed with its text. The name of an object is not in a push, so the discussion
 * has none to show in its bar.
 */
fun PushTarget.destination(eventType: String): Destination {
    // A message has no object, only a conversation; the list of conversations if it is not named.
    if (type == "chat") return ref?.let { Destination.Conversation(it) } ?: Destination.Messages
    val id = id ?: return Destination.Notifications
    return when (type) {
        "bike" ->
            if (commentId != null) Destination.Comments("bike", id, "", commentId)
            else Destination.Bike(id)
        "ride" ->
            when {
                commentId != null -> Destination.Comments("ride", id, "", commentId)
                // The date the push is about, not "the nearest Saturday".
                occurrenceAt != null && eventType in DATED_RIDE_EVENTS ->
                    Destination.Participation(id, occurrenceAt.toString())
                else -> Destination.Ride(id)
            }
        "journal" -> Destination.Journal(id)
        "component" ->
            if (commentId != null) Destination.Comments("component", id, "", commentId)
            else Destination.Component(id)
        "profile" -> Destination.Person(id)
        "market" -> Destination.Listing(id)
        // A friend's intention: its page, which says plainly if it is no longer there.
        "intent" -> Destination.Intent(id)
        "account" ->
            if (eventType == "session_reuse") Destination.Devices else Destination.Notifications
        else -> Destination.Notifications
    }
}
