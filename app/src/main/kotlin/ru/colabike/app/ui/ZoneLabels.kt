package ru.colabike.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import ru.colabike.core.model.IntentFold
import ru.colabike.core.model.IntentWindowDraft

/** Label the offset at the meeting, never today's offset for a future or historic meeting. */
internal fun zoneLabel(zone: ZoneId, moment: Instant, locale: Locale): String {
    val name = DateTimeFormatter.ofPattern("zzzz", locale).withZone(zone).format(moment)
    val offset = offsetLabel(zone.rules.getOffset(moment))
    return if (
        zone.normalized() is ZoneOffset ||
            name == offset ||
            name == "Z" ||
            name == "UTC" ||
            name.startsWith("GMT")
    )
        offset
    else "$name · $offset"
}

private fun offsetLabel(offset: ZoneOffset): String =
    "UTC" + if (offset == ZoneOffset.UTC) "" else offset.id

/** Invalid local times keep the generic zone; an unresolved fold displays both possible offsets. */
internal fun draftZoneLabel(
    zone: ZoneId,
    windows: List<IntentWindowDraft>,
    locale: Locale,
): String {
    val moments = windows.flatMap { window ->
        listOf(window.start to window.startFold, window.end to window.endFold).flatMap {
            (time, fold) ->
            val offsets = zone.rules.getValidOffsets(time)
            val chosen =
                when {
                    offsets.size != 2 || fold == null -> offsets
                    fold == IntentFold.Earlier -> listOf(offsets.first())
                    else -> listOf(offsets.last())
                }
            chosen.map { time.toInstant(it) }
        }
    }
    return moments
        .map { zoneLabel(zone, it, locale) }
        .distinct()
        .joinToString(" / ")
        .ifEmpty { zone.getDisplayName(TextStyle.FULL, locale) }
}
