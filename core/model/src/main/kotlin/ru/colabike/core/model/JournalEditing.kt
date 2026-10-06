package ru.colabike.core.model

import java.time.LocalDate

/** The kinds and results the server accepts (cola `lib/journal.ts`). */
object JournalCatalog {
    val kinds: List<String> = listOf("build", "service", "review", "question", "story")
    val installationResults: List<String> = listOf("direct", "modified", "failed")

    /** Only an entry of this kind says how an installation went; for others it is dropped. */
    const val BUILD = "build"
}

/**
 * An entry as its author's form holds it and the server takes it. Texts are as typed; the server
 * trims them. [eventDate] and [mileageKm] are null when the author gave none. [componentIds] are
 * components of the bike; an entry keeps their snapshot as it was when the entry was saved.
 */
data class JournalDraft(
    val kind: String,
    val title: String,
    val body: String,
    val status: JournalStatus,
    /** Shown to everyone once published; a draft is the author's alone whatever this says. */
    val isPublic: Boolean,
    val eventDate: LocalDate?,
    val mileageKm: Int?,
    val installationResult: String?,
    val componentIds: List<String>,
)

/**
 * What changed between the entry the author read and the form: only the named fields go to the
 * server (`PATCH`). A date, a mileage and an installation result that were taken away are the
 * `clear…` flags, because `null` is a value of its own there. The ride an entry is tied to is not
 * here: the app does not change it, and what the server holds stays.
 */
data class JournalPatch(
    val kind: String? = null,
    val title: String? = null,
    val body: String? = null,
    val status: JournalStatus? = null,
    val isPublic: Boolean? = null,
    val eventDate: LocalDate? = null,
    val clearEventDate: Boolean = false,
    val mileageKm: Int? = null,
    val clearMileage: Boolean = false,
    val installationResult: String? = null,
    val clearInstallation: Boolean = false,
    /** The whole new list (the server replaces it); null when the selection did not change. */
    val componentIds: List<String>? = null,
) {
    val isEmpty: Boolean
        get() = this == JournalPatch()
}

/** The fields that differ from [original]; an empty patch means nothing needs to be sent. */
fun JournalDraft.diff(original: JournalDraft): JournalPatch =
    JournalPatch(
        kind = kind.takeIf { it != original.kind },
        title = title.takeIf { it != original.title },
        body = body.takeIf { it != original.body },
        status = status.takeIf { it != original.status },
        isPublic = isPublic.takeIf { it != original.isPublic },
        eventDate = eventDate.takeIf { it != original.eventDate },
        clearEventDate = eventDate == null && original.eventDate != null,
        mileageKm = mileageKm.takeIf { it != original.mileageKm },
        clearMileage = mileageKm == null && original.mileageKm != null,
        installationResult = installationResult.takeIf { it != original.installationResult },
        clearInstallation = installationResult == null && original.installationResult != null,
        // The order is the author's choice of nothing: the same set is the same selection.
        componentIds = componentIds.takeIf { it.toSet() != original.componentIds.toSet() },
    )

/** The entry as the form starts from it when its author changes it. */
fun JournalEntry.toDraft(): JournalDraft =
    JournalDraft(
        kind = summary.kind,
        title = summary.title,
        body = body,
        status = summary.status,
        isPublic = summary.isPublic,
        eventDate = summary.eventDate,
        mileageKm = summary.mileageKm,
        installationResult = installationResult,
        componentIds = components.map { it.id },
    )

/** What is wrong with an entry's form, in the server's own terms; checked before a request. */
sealed interface JournalProblem {
    data object TitleTooLong : JournalProblem

    data object BodyTooLong : JournalProblem

    /** A published entry has a title. */
    data object NoTitle : JournalProblem

    /** A published entry has a text. */
    data object NoBody : JournalProblem

    /** Not a whole number from 0 to 10 000 000. */
    data object MileageInvalid : JournalProblem

    /** Not a date as `ДД.ММ.ГГГГ`, or not a day that exists. */
    data object DateInvalid : JournalProblem

    data object TooManyComponents : JournalProblem
}

/** The limits of the server for one entry (cola `journalInput`). */
object JournalRules {
    const val MAX_TITLE = 160
    const val MAX_BODY = 20_000
    const val MAX_MILEAGE_KM = 10_000_000
    const val MAX_COMPONENTS = 50

    /** Everything the form can get wrong that the server would refuse. */
    fun check(draft: JournalDraft): List<JournalProblem> = buildList {
        val title = draft.title.trim()
        val body = draft.body.trim()
        if (title.length > MAX_TITLE) add(JournalProblem.TitleTooLong)
        if (body.length > MAX_BODY) add(JournalProblem.BodyTooLong)
        if (draft.status == JournalStatus.Published) {
            if (title.isEmpty()) add(JournalProblem.NoTitle)
            if (body.isEmpty()) add(JournalProblem.NoBody)
        }
        draft.mileageKm?.let { if (it !in 0..MAX_MILEAGE_KM) add(JournalProblem.MileageInvalid) }
        if (draft.componentIds.toSet().size > MAX_COMPONENTS) add(JournalProblem.TooManyComponents)
    }
}

/**
 * An entry that was written, changed or deleted here, for the screens that show it to agree without
 * a reload.
 */
sealed interface JournalChange {
    data class Saved(val entry: JournalEntry) : JournalChange

    data class Removed(val id: JournalId) : JournalChange
}
