package ru.colabike.core.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** How sure the author is: ready to go, or still thinking. */
enum class IntentReadiness {
    Ready,
    Considering,
}

/** Who can see an intention: only its author, or everyone signed in (needs a confirmed e-mail). */
enum class IntentVisibility {
    Private,
    Community,
}

/** An intention is open, closed by its author, or over because every window has passed. */
enum class IntentStatus {
    Active,
    Cancelled,
    Expired,

    /** A state this version of the app does not know yet. */
    Unknown,
}

/** One stretch of time the author could ride in, as the server holds it (an instant). */
data class IntentWindow(val startsAt: Instant, val endsAt: Instant)

/**
 * "I want to ride": an intention, not an event. Nobody answers "going" to it; one opens the author
 * and agrees there. [version] is the server's tag of what was read (the `ETag`): an edit names it,
 * so that two phones do not overwrite each other. Someone else's intention has no
 * [allowSuggestions] (that is the author's own choice, not for others to see).
 */
data class RideIntent(
    val id: String,
    val own: Boolean,
    val readiness: IntentReadiness,
    val timeZone: ZoneId,
    val passport: RidePassport,
    val windows: List<IntentWindow>,
    val meetNewPeople: Boolean?,
    val visibility: IntentVisibility,
    val status: IntentStatus,
    val allowSuggestions: Boolean?,
    val author: Person,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: String? = null,
) {
    /** Open for a change: the server edits only an intention that has not been cancelled. */
    val editable: Boolean
        get() = own && status != IntentStatus.Cancelled
}

/** Which of two equal clock times of the night the clocks go back is meant. */
enum class IntentFold {
    Earlier,
    Later,
}

/**
 * A window as the person types it: local time in the intention's zone, no offset. The server reads
 * it in that zone (a time the clocks skip is refused, a repeated one needs a [startFold] /
 * [endFold]), so that "Saturday 10:00" stays Saturday 10:00 whatever the server's own zone.
 */
data class IntentWindowDraft(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val startFold: IntentFold? = null,
    val endFold: IntentFold? = null,
)

/**
 * What the form sends: a whole intention, since the server replaces and does not patch. The
 * [passport] carries every field the author set, also those this app has no field for, so that an
 * edit never takes away what the site showed.
 */
data class IntentDraft(
    val readiness: IntentReadiness,
    val timeZone: ZoneId,
    val windows: List<IntentWindowDraft>,
    val passport: RidePassport,
    val meetNewPeople: Boolean?,
    val visibility: IntentVisibility,
    val allowSuggestions: Boolean,
) {
    companion object {
        /** The server's limits: windows per intention and open intentions per person. */
        const val MAX_WINDOWS = 4
        const val MAX_OPEN = 5
        const val MAX_AREA_LABEL = 100

        /** The words of the planner the server accepts for `purpose`; the rest are open sets. */
        val purposes = listOf("leisure", "social", "training", "exploration", "adventure")
        val paces = listOf("relaxed", "moderate", "sporty")
        val surfaces = listOf("asphalt", "gravel", "trail", "mixed")
    }
}

/**
 * The intentions of the community and the person's own (cola#343). A create names a key that is the
 * intention's identity: asking twice with the same key gives the same intention, not a second.
 */
interface IntentsRepository {
    /** The person's own, in any state but deleted, newest first. */
    suspend fun own(cursor: String?, limit: Int = 24): Page<RideIntent>

    /** Open intentions published to the community (the person's own among them), newest first. */
    suspend fun community(cursor: String?, limit: Int = 24): Page<RideIntent>

    /** One intention, with the version a change must name; someone else's only if still open. */
    suspend fun get(id: String): RideIntent

    /** A new intention. [key] is a UUID the caller keeps until the answer is known. */
    suspend fun create(draft: IntentDraft, key: String): RideIntent

    /** Replaces an intention of one's own; [version] makes it apply only to what was seen. */
    suspend fun replace(id: String, draft: IntentDraft, version: String?): RideIntent

    /** Takes it off publication and closes it; it stays in the person's list as cancelled. */
    suspend fun cancel(id: String): RideIntent

    /** Removes it altogether (a repeat is fine). */
    suspend fun delete(id: String)
}

/**
 * The window of a draft, from an instant held by the server, in the intention's own zone. In the
 * hour the clocks go back, the two instants that read the same carry a fold so that the draft says
 * which one it means.
 */
fun IntentWindow.toDraft(zone: ZoneId): IntentWindowDraft =
    IntentWindowDraft(
        start = startsAt.atZone(zone).toLocalDateTime(),
        end = endsAt.atZone(zone).toLocalDateTime(),
        startFold = foldOf(startsAt, zone),
        endFold = foldOf(endsAt, zone),
    )

private fun foldOf(moment: Instant, zone: ZoneId): IntentFold? {
    val local = moment.atZone(zone).toLocalDateTime()
    val offsets = zone.rules.getValidOffsets(local)
    if (offsets.size < 2) return null
    // The earlier of the two is the one with the larger offset (clocks go back by the difference).
    val offset = zone.rules.getOffset(moment)
    return if (offset == offsets.maxByOrNull { it.totalSeconds }) IntentFold.Earlier
    else IntentFold.Later
}

/** A draft of an intention that is already there, to be changed and sent back whole. */
fun RideIntent.toDraft(): IntentDraft =
    IntentDraft(
        readiness = readiness,
        timeZone = timeZone,
        windows = windows.map { it.toDraft(timeZone) },
        passport = passport,
        meetNewPeople = meetNewPeople,
        visibility = visibility,
        allowSuggestions = allowSuggestions ?: false,
    )

/** What is wrong with a form before it is sent; the server holds to the same rules (400). */
sealed interface IntentProblem {
    data object NoWindows : IntentProblem

    data object NoArea : IntentProblem

    data object AreaTooLong : IntentProblem

    /** The window at [index] (from 0) ends before it begins, or at the same minute. */
    data class EndsBeforeStart(val index: Int) : IntentProblem

    /** Longer than a day. */
    data class TooLong(val index: Int) : IntentProblem

    /** Already over. */
    data class InThePast(val index: Int) : IntentProblem

    /** Further ahead than the server looks. */
    data class TooFarAhead(val index: Int) : IntentProblem

    /** The clocks skip that time (the night they go forward). */
    data class TimeDoesNotExist(val index: Int) : IntentProblem

    /** The clocks repeat that time (the night they go back) and the form has not said which. */
    data class TimeIsRepeated(val index: Int) : IntentProblem

    data object Overlap : IntentProblem
}

/** The rules of the server for windows and the area, checked before a request is made. */
object IntentRules {
    const val MAX_WINDOW_HOURS = 24L
    const val MAX_AHEAD_DAYS = 90L

    /** A window in instants, or why its local times cannot be read in [zone]. */
    fun resolve(window: IntentWindowDraft, zone: ZoneId, index: Int): Resolved {
        val start = resolveTime(window.start, window.startFold, zone, index)
        val end = resolveTime(window.end, window.endFold, zone, index)
        val problem = (start as? Resolved.Failed) ?: (end as? Resolved.Failed)
        if (problem != null) return problem
        return Resolved.Window(IntentWindow((start as Resolved.Time).at, (end as Resolved.Time).at))
    }

    sealed interface Resolved {
        data class Time(val at: Instant) : Resolved

        data class Window(val window: IntentWindow) : Resolved

        data class Failed(val problem: IntentProblem) : Resolved
    }

    private fun resolveTime(
        local: LocalDateTime,
        fold: IntentFold?,
        zone: ZoneId,
        index: Int,
    ): Resolved {
        val offsets = zone.rules.getValidOffsets(local)
        return when (offsets.size) {
            0 -> Resolved.Failed(IntentProblem.TimeDoesNotExist(index))
            1 -> Resolved.Time(local.toInstant(offsets[0]))
            else -> {
                // Two offsets: the earlier moment has the larger one (before the clocks go back).
                val earlier = offsets.maxByOrNull { it.totalSeconds }!!
                val later = offsets.minByOrNull { it.totalSeconds }!!
                when (fold) {
                    IntentFold.Earlier -> Resolved.Time(local.toInstant(earlier))
                    IntentFold.Later -> Resolved.Time(local.toInstant(later))
                    null -> Resolved.Failed(IntentProblem.TimeIsRepeated(index))
                }
            }
        }
    }

    /** Every problem of [draft] as of [now]; empty means the form can be sent. */
    fun check(draft: IntentDraft, now: Instant): List<IntentProblem> {
        val problems = mutableListOf<IntentProblem>()
        val label = draft.passport.areaLabel?.trim().orEmpty()
        if (label.isEmpty()) problems += IntentProblem.NoArea
        if (label.length > IntentDraft.MAX_AREA_LABEL) problems += IntentProblem.AreaTooLong
        if (draft.windows.isEmpty()) problems += IntentProblem.NoWindows
        val resolved = mutableListOf<IntentWindow>()
        draft.windows.forEachIndexed { index, window ->
            when (val one = resolve(window, draft.timeZone, index)) {
                is Resolved.Failed -> problems += one.problem
                is Resolved.Window -> {
                    val w = one.window
                    when {
                        !w.endsAt.isAfter(w.startsAt) ->
                            problems += IntentProblem.EndsBeforeStart(index)
                        java.time.Duration.between(w.startsAt, w.endsAt) >
                            java.time.Duration.ofHours(MAX_WINDOW_HOURS) ->
                            problems += IntentProblem.TooLong(index)
                        !w.endsAt.isAfter(now) -> problems += IntentProblem.InThePast(index)
                        w.startsAt.isAfter(now.plus(java.time.Duration.ofDays(MAX_AHEAD_DAYS))) ->
                            problems += IntentProblem.TooFarAhead(index)
                    }
                    resolved += w
                }
                is Resolved.Time -> Unit
            }
        }
        val sorted = resolved.sortedBy { it.startsAt }
        if (sorted.zipWithNext().any { (a, b) -> b.startsAt.isBefore(a.endsAt) }) {
            problems += IntentProblem.Overlap
        }
        return problems
    }
}
