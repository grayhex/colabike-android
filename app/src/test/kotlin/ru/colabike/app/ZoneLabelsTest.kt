package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Test
import ru.colabike.app.ui.draftZoneLabel
import ru.colabike.app.ui.zoneLabel
import ru.colabike.core.model.IntentFold
import ru.colabike.core.model.IntentWindowDraft

class ZoneLabelsTest {
    private val ru = Locale.forLanguageTag("ru")

    @Test
    fun `offset follows the meeting season and includes fractional hours`() {
        val winter = Instant.parse("2026-01-15T12:00:00Z")
        val summer = Instant.parse("2026-07-15T12:00:00Z")
        assertThat(zoneLabel(ZoneId.of("Europe/London"), winter, ru)).endsWith("UTC")
        assertThat(zoneLabel(ZoneId.of("Europe/London"), summer, ru)).endsWith("UTC+01:00")
        assertThat(zoneLabel(ZoneId.of("Asia/Kathmandu"), summer, ru)).endsWith("UTC+05:45")
        assertThat(zoneLabel(ZoneId.of("Europe/Moscow"), winter, ru)).doesNotContain("Europe/")
        assertThat(zoneLabel(ZoneId.of("UTC"), summer, ru)).isEqualTo("UTC")
    }

    @Test
    fun `draft spanning clock change shows both offsets without guessing fold`() {
        val time = LocalDateTime.parse("2026-10-25T01:15:00")
        val zone = ZoneId.of("Europe/London")
        val window = IntentWindowDraft(time, time.plusMinutes(15))
        val both = draftZoneLabel(zone, listOf(window), ru)
        assertThat(both).contains("UTC+01:00")
        assertThat(both).contains(" / ")
        val later =
            draftZoneLabel(
                zone,
                listOf(window.copy(startFold = IntentFold.Later, endFold = IntentFold.Later)),
                ru,
            )
        assertThat(later).doesNotContain("UTC+01:00")
    }

    @Test
    fun `a skipped local hour never invents an offset`() {
        val time = LocalDateTime.parse("2026-03-29T01:15:00")
        val result =
            draftZoneLabel(
                ZoneId.of("Europe/London"),
                listOf(IntentWindowDraft(time, time.plusMinutes(15))),
                ru,
            )
        assertThat(result).doesNotContain("UTC+")
        assertThat(result).doesNotContain("Europe/")
        assertThat(result).isNotEmpty()
    }
}
