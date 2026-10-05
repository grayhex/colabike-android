package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class IntentRulesTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private val berlin = ZoneId.of("Europe/Berlin")

    // 2026-10-05 09:00 in Moscow.
    private val now = Instant.parse("2026-10-05T06:00:00Z")

    private fun window(start: LocalDateTime, end: LocalDateTime) = IntentWindowDraft(start, end)

    private fun draft(
        windows: List<IntentWindowDraft>,
        zone: ZoneId = moscow,
        label: String? = "Парк",
    ) =
        IntentDraft(
            readiness = IntentReadiness.Ready,
            timeZone = zone,
            windows = windows,
            passport = RidePassport(areaLabel = label, purpose = "leisure"),
            meetNewPeople = null,
            visibility = IntentVisibility.Private,
            allowSuggestions = false,
        )

    private val saturday =
        window(LocalDateTime.of(2026, 10, 10, 10, 0), LocalDateTime.of(2026, 10, 10, 13, 0))

    @Test
    fun `a window ahead, within a day and with an area is fine`() {
        assertThat(IntentRules.check(draft(listOf(saturday)), now)).isEmpty()
    }

    @Test
    fun `no window and no area are two problems of their own`() {
        val problems = IntentRules.check(draft(emptyList(), label = "  "), now)

        assertThat(problems).containsExactly(IntentProblem.NoArea, IntentProblem.NoWindows)
    }

    @Test
    fun `an area longer than the server takes is refused`() {
        val problems = IntentRules.check(draft(listOf(saturday), label = "я".repeat(101)), now)

        assertThat(problems).containsExactly(IntentProblem.AreaTooLong)
    }

    @Test
    fun `a window that ends before it begins, is over or is longer than a day is named`() {
        val backwards =
            window(LocalDateTime.of(2026, 10, 10, 13, 0), LocalDateTime.of(2026, 10, 10, 10, 0))
        val over =
            window(LocalDateTime.of(2026, 10, 4, 10, 0), LocalDateTime.of(2026, 10, 4, 12, 0))
        val long =
            window(LocalDateTime.of(2026, 10, 10, 8, 0), LocalDateTime.of(2026, 10, 11, 9, 0))

        assertThat(IntentRules.check(draft(listOf(backwards)), now))
            .containsExactly(IntentProblem.EndsBeforeStart(0))
        assertThat(IntentRules.check(draft(listOf(over)), now))
            .containsExactly(IntentProblem.InThePast(0))
        assertThat(IntentRules.check(draft(listOf(long)), now))
            .containsExactly(IntentProblem.TooLong(0))
    }

    @Test
    fun `exactly a day is allowed, a minute more is not`() {
        val day = window(LocalDateTime.of(2026, 10, 10, 8, 0), LocalDateTime.of(2026, 10, 11, 8, 0))
        val more =
            window(LocalDateTime.of(2026, 10, 10, 8, 0), LocalDateTime.of(2026, 10, 11, 8, 1))

        assertThat(IntentRules.check(draft(listOf(day)), now)).isEmpty()
        assertThat(IntentRules.check(draft(listOf(more)), now))
            .containsExactly(IntentProblem.TooLong(0))
    }

    @Test
    fun `further than ninety days ahead is too far`() {
        val far = window(LocalDateTime.of(2027, 2, 1, 10, 0), LocalDateTime.of(2027, 2, 1, 12, 0))

        assertThat(IntentRules.check(draft(listOf(far)), now))
            .containsExactly(IntentProblem.TooFarAhead(0))
    }

    @Test
    fun `windows may touch but not overlap`() {
        val a = window(LocalDateTime.of(2026, 10, 10, 10, 0), LocalDateTime.of(2026, 10, 10, 12, 0))
        val touching =
            window(LocalDateTime.of(2026, 10, 10, 12, 0), LocalDateTime.of(2026, 10, 10, 14, 0))
        val overlapping =
            window(LocalDateTime.of(2026, 10, 10, 11, 0), LocalDateTime.of(2026, 10, 10, 14, 0))

        assertThat(IntentRules.check(draft(listOf(a, touching)), now)).isEmpty()
        assertThat(IntentRules.check(draft(listOf(overlapping, a)), now))
            .containsExactly(IntentProblem.Overlap)
    }

    @Test
    fun `a time the clocks skip does not exist, and one they repeat needs a fold`() {
        // Berlin: 2026-03-29 02:30 does not exist; 2026-10-25 02:30 happens twice.
        val skipped =
            window(LocalDateTime.of(2026, 3, 29, 2, 30), LocalDateTime.of(2026, 3, 29, 4, 0))
        val repeated =
            window(LocalDateTime.of(2026, 10, 25, 2, 30), LocalDateTime.of(2026, 10, 25, 4, 0))
        val folded = repeated.copy(startFold = IntentFold.Later)

        val early = Instant.parse("2026-01-01T00:00:00Z")
        assertThat(IntentRules.check(draft(listOf(skipped), berlin), early))
            .containsExactly(IntentProblem.TimeDoesNotExist(0))
        assertThat(IntentRules.check(draft(listOf(repeated), berlin), now))
            .containsExactly(IntentProblem.TimeIsRepeated(0))
        assertThat(IntentRules.check(draft(listOf(folded), berlin), now)).isEmpty()
    }

    @Test
    fun `the fold picks the earlier or the later of the repeated moments`() {
        val repeated =
            IntentWindowDraft(
                LocalDateTime.of(2026, 10, 25, 2, 30),
                LocalDateTime.of(2026, 10, 25, 3, 30),
                startFold = IntentFold.Earlier,
            )
        val later = repeated.copy(startFold = IntentFold.Later)

        val first = IntentRules.resolve(repeated, berlin, 0) as IntentRules.Resolved.Window
        val second = IntentRules.resolve(later, berlin, 0) as IntentRules.Resolved.Window

        assertThat(first.window.startsAt).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
        assertThat(second.window.startsAt).isEqualTo(Instant.parse("2026-10-25T01:30:00Z"))
    }

    @Test
    fun `a window read from the server comes back as the same instants`() {
        val window =
            IntentWindow(
                Instant.parse("2026-10-25T00:30:00Z"),
                Instant.parse("2026-10-25T01:30:00Z"),
            )

        val back =
            IntentRules.resolve(window.toDraft(berlin), berlin, 0) as IntentRules.Resolved.Window

        assertThat(back.window).isEqualTo(window)
    }
}
