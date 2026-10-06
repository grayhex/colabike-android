package ru.colabike.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class JournalEditingTest {
    private val base =
        JournalDraft(
            kind = "service",
            title = "Замена цепи",
            body = "Поставил **KMC**.",
            status = JournalStatus.Published,
            isPublic = true,
            eventDate = LocalDate.parse("2026-09-14"),
            mileageKm = 4200,
            installationResult = null,
            componentIds = listOf("a", "b"),
        )

    @Test
    fun `a form nobody touched changes nothing`() {
        assertThat(base.diff(base).isEmpty).isTrue()
    }

    @Test
    fun `only the fields that differ are named`() {
        val patch = base.copy(title = "Другое", mileageKm = 4300).diff(base)

        assertThat(patch).isEqualTo(JournalPatch(title = "Другое", mileageKm = 4300))
    }

    @Test
    fun `a date, a mileage and a result taken away are clearings, not missing fields`() {
        val had = base.copy(kind = "build", installationResult = "direct")

        val patch =
            had.copy(eventDate = null, mileageKm = null, installationResult = null).diff(had)

        assertThat(patch.clearEventDate).isTrue()
        assertThat(patch.clearMileage).isTrue()
        assertThat(patch.clearInstallation).isTrue()
        assertThat(patch.eventDate).isNull()
        assertThat(patch.isEmpty).isFalse()
    }

    @Test
    fun `a date that was empty and still is, is not a clearing`() {
        val without = base.copy(eventDate = null, mileageKm = null)

        assertThat(without.diff(without).isEmpty).isTrue()
    }

    @Test
    fun `the same components in another order are the same selection`() {
        assertThat(base.copy(componentIds = listOf("b", "a")).diff(base).componentIds).isNull()
        assertThat(base.copy(componentIds = listOf("a")).diff(base).componentIds)
            .containsExactly("a")
        assertThat(base.copy(componentIds = emptyList()).diff(base).componentIds).isEmpty()
    }

    @Test
    fun `a good form has no problems`() {
        assertThat(JournalRules.check(base)).isEmpty()
    }

    @Test
    fun `a draft may be empty, a published entry needs a title and a text`() {
        val empty = base.copy(title = "  ", body = "")

        assertThat(JournalRules.check(empty.copy(status = JournalStatus.Draft))).isEmpty()
        assertThat(JournalRules.check(empty))
            .containsExactly(JournalProblem.NoTitle, JournalProblem.NoBody)
    }

    @Test
    fun `the limits are the server's`() {
        val problems =
            JournalRules.check(
                base.copy(
                    title = "н".repeat(161),
                    body = "т".repeat(20_001),
                    mileageKm = 10_000_001,
                    componentIds = (1..51).map { "c$it" },
                )
            )

        assertThat(problems)
            .containsExactly(
                JournalProblem.TitleTooLong,
                JournalProblem.BodyTooLong,
                JournalProblem.MileageInvalid,
                JournalProblem.TooManyComponents,
            )
    }

    @Test
    fun `the edges of the limits are allowed`() {
        val problems =
            JournalRules.check(
                base.copy(
                    title = "н".repeat(160),
                    body = "т".repeat(20_000),
                    mileageKm = 10_000_000,
                    componentIds = (1..50).map { "c$it" },
                )
            )

        assertThat(problems).isEmpty()
    }

    @Test
    fun `the entry as the form starts from it keeps its snapshot ids and result`() {
        val summary = PreviewJournal.summary
        val entry =
            JournalEntry(
                summary = summary,
                body = "Текст",
                components =
                    listOf(
                        BikeComponent("p1", "build", "Цепь", "KMC", "", groupId = "drivetrain"),
                        BikeComponent(
                            "p2",
                            "build",
                            "Кассета",
                            "Deore",
                            "",
                            groupId = "drivetrain",
                        ),
                    ),
                photos = emptyList(),
                version = "\"v\"",
                installationResult = "failed",
            )

        val draft = entry.toDraft()

        assertThat(draft.componentIds).containsExactly("p1", "p2").inOrder()
        assertThat(draft.installationResult).isEqualTo("failed")
        assertThat(draft.status).isEqualTo(summary.status)
        assertThat(draft.body).isEqualTo("Текст")
    }
}

private object PreviewJournal {
    val summary =
        JournalSummary(
            id = JournalId("e1"),
            kind = "build",
            title = "Сборка",
            status = JournalStatus.Draft,
            isPublic = false,
            eventDate = LocalDate.parse("2026-09-01"),
            mileageKm = null,
            createdAt = java.time.Instant.parse("2026-09-01T10:00:00Z"),
            updatedAt = java.time.Instant.parse("2026-09-01T10:00:00Z"),
            bike = BikeRef(BikeId("b1"), "Мой"),
            author = Person(UserId("u1"), "me", "Я", null),
            likes = 0,
            comments = 0,
            liked = false,
            excerpt = "",
        )
}
