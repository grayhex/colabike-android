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
 * Where the person meant to go when a link arrived while the app could not take them there yet:
 * before sign-in, while the session is read, across a cold start or a trip to the browser. The
 * shell takes it as soon as it is on screen. Only a destination the app can open from a link is
 * kept, and only for a while: an old intention is not carried out out of the blue.
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
            is Destination.Bike ->
                if (UUID.matches(destination.id)) "$BIKE${destination.id}" else null
            is Destination.Person ->
                if (isPersonRef(destination.ref)) "$PERSON${destination.ref}" else null
            else -> null
        }

    private fun decode(value: String): Destination? =
        when {
            value.startsWith(BIKE) ->
                value.removePrefix(BIKE).takeIf(UUID::matches)?.let { Destination.Bike(it) }
            value.startsWith(PERSON) ->
                value.removePrefix(PERSON).takeIf(::isPersonRef)?.let { Destination.Person(it) }
            else -> null
        }

    /** A person is named by id or by username, the two shapes the API takes. */
    private fun isPersonRef(ref: String) = UUID.matches(ref) || USERNAME.matches(ref)

    private companion object {
        const val KEY_VALUE = "pending_destination"
        const val KEY_AT = "pending_at"
        const val BIKE = "bike:"
        const val PERSON = "person:"
        val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val USERNAME = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{2,29}$")
    }
}
