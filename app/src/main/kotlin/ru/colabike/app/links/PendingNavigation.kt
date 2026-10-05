package ru.colabike.app.links

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.colabike.app.navigation.Destination

/**
 * Where the person meant to go when a link or a notification arrived while the app could not take
 * them there yet: before sign-in, while the session is read, across a cold start or a trip to the
 * browser. The shell takes it as soon as it is on screen. Only a destination the app can open from
 * a link or a notification is kept (an object by its id, a discussion at a comment, the devices,
 * the inbox, a conversation by its cid, the list of conversations), and only for a while: an old
 * intention is not carried out out of the blue.
 */
interface PendingNavigation {
    val destination: StateFlow<Destination?>

    fun offer(destination: Destination)

    fun clear()
}

/** [PendingNavigation] in SharedPreferences (no backup, nothing secret: an id of a public page). */
class PreferencesPendingNavigation(
    private val preferences: SharedPreferences,
    private val clock: Clock = Clock.systemUTC(),
    private val lifetime: Duration = Duration.ofMinutes(30),
) : PendingNavigation {
    private val mutable = MutableStateFlow(read())
    override val destination: StateFlow<Destination?> = mutable.asStateFlow()

    override fun offer(destination: Destination) {
        val encoded = encode(destination) ?: return
        preferences.edit {
            putString(KEY_VALUE, encoded)
            putLong(KEY_AT, clock.millis())
        }
        mutable.value = destination
    }

    override fun clear() {
        preferences.edit { remove(KEY_VALUE).remove(KEY_AT) }
        mutable.value = null
    }

    /** What is stored, if it is whole and still fresh; anything else is dropped. */
    private fun read(): Destination? {
        val value = preferences.getString(KEY_VALUE, null) ?: return null
        val at = preferences.getLong(KEY_AT, 0L)
        val fresh = Duration.ofMillis(clock.millis() - at) in Duration.ZERO..lifetime
        val parsed = if (fresh) decode(value) else null
        if (parsed == null) preferences.edit { remove(KEY_VALUE).remove(KEY_AT) }
        return parsed
    }

    private fun encode(destination: Destination): String? =
        when (destination) {
            is Destination.Bike -> uuid(destination.id)?.let { "$BIKE$it" }
            is Destination.Person ->
                if (isPersonRef(destination.ref)) "$PERSON${destination.ref}" else null
            is Destination.Ride -> uuid(destination.id)?.let { "$RIDE$it" }
            is Destination.Journal -> uuid(destination.id)?.let { "$JOURNAL$it" }
            is Destination.Listing -> uuid(destination.id)?.let { "$LISTING$it" }
            is Destination.Component -> uuid(destination.id)?.let { "$COMPONENT$it" }
            // The name of the object is not kept: the discussion has it from the page it opens.
            is Destination.Comments -> {
                val id = uuid(destination.id)
                val focus = destination.focus?.let { uuid(it) ?: return null }.orEmpty()
                if (destination.kind in COMMENT_KINDS && id != null) {
                    "$COMMENTS${destination.kind}:$id:$focus"
                } else {
                    null
                }
            }
            is Destination.Conversation ->
                if (CID.matches(destination.cid)) "$CHAT${destination.cid}" else null
            Destination.Messages -> MESSAGES
            Destination.Devices -> DEVICES
            Destination.Notifications -> NOTIFICATIONS
            else -> null
        }

    private fun decode(value: String): Destination? =
        when {
            value.startsWith(BIKE) -> uuid(value.removePrefix(BIKE))?.let { Destination.Bike(it) }
            value.startsWith(PERSON) ->
                value.removePrefix(PERSON).takeIf(::isPersonRef)?.let { Destination.Person(it) }
            value.startsWith(RIDE) -> uuid(value.removePrefix(RIDE))?.let { Destination.Ride(it) }
            value.startsWith(JOURNAL) ->
                uuid(value.removePrefix(JOURNAL))?.let { Destination.Journal(it) }
            value.startsWith(LISTING) ->
                uuid(value.removePrefix(LISTING))?.let { Destination.Listing(it) }
            value.startsWith(COMPONENT) ->
                uuid(value.removePrefix(COMPONENT))?.let { Destination.Component(it) }
            value.startsWith(COMMENTS) -> decodeComments(value.removePrefix(COMMENTS))
            value.startsWith(CHAT) ->
                value.removePrefix(CHAT).takeIf(CID::matches)?.let { Destination.Conversation(it) }
            value == MESSAGES -> Destination.Messages
            value == DEVICES -> Destination.Devices
            value == NOTIFICATIONS -> Destination.Notifications
            else -> null
        }

    private fun decodeComments(value: String): Destination? {
        val parts = value.split(':')
        if (parts.size != 3 || parts[0] !in COMMENT_KINDS) return null
        val id = uuid(parts[1]) ?: return null
        val focus = parts[2].takeIf { it.isNotEmpty() }?.let { uuid(it) ?: return null }
        return Destination.Comments(parts[0], id, "", focus)
    }

    private fun uuid(value: String): String? = value.takeIf { UUID.matches(it) }

    /** A person is named by id or by username, the two shapes the API takes. */
    private fun isPersonRef(ref: String) = UUID.matches(ref) || USERNAME.matches(ref)

    private companion object {
        const val KEY_VALUE = "pending_destination"
        const val KEY_AT = "pending_at"
        const val BIKE = "bike:"
        const val PERSON = "person:"
        const val RIDE = "ride:"
        const val JOURNAL = "journal:"
        const val LISTING = "listing:"
        const val COMPONENT = "component:"
        const val COMMENTS = "comments:"
        const val CHAT = "chat:"
        const val MESSAGES = "messages"
        const val DEVICES = "devices"
        const val NOTIFICATIONS = "notifications"
        val COMMENT_KINDS = setOf("bike", "ride", "journal", "component")
        val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val USERNAME = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{2,29}$")
        val CID = Regex("^[A-Za-z0-9_-]{1,32}:[A-Za-z0-9_!.@-]{1,87}$")
    }
}
