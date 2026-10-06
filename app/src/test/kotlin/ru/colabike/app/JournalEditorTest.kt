package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.journal.JournalEditorUiState
import ru.colabike.app.journal.JournalEditorViewModel
import ru.colabike.app.journal.JournalForm
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalProblem
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.toDraft

private val ownJournalBike =
    PreviewData.bikeDetail.fromDraft(BikeId("b-own"), PreviewData.bikeDetail.toDraft(), "\"v1\"")

/** An entry of [ownJournalBike] as its author reads it: with a version. */
private fun ownEntry(id: String = "j-own", version: String? = "\"e0\""): JournalEntry {
    val base = journalEntry(1)
    return base.copy(
        summary =
            base.summary.copy(
                id = JournalId(id),
                kind = "service",
                title = "Замена цепи",
                status = JournalStatus.Published,
                isPublic = true,
                eventDate = LocalDate.parse("2026-09-14"),
                mileageKm = 4200,
                bike = BikeRef(BikeId("b-own"), "Мой трейл"),
            ),
        version = version,
    )
}

class JournalEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val bikes = FakeBikes().also { it.details = mapOf("b-own" to ownJournalBike) }
    private val journal = FakeJournal(entries = mapOf("j-own" to ownEntry()))
    private var keys = 0

    private fun editor(
        id: String? = null,
        bike: String = "b-own",
        today: LocalDate = LocalDate.parse("2026-10-06"),
    ) =
        JournalEditorViewModel(
            journal,
            bikes,
            BikeId(bike),
            id?.let(::JournalId),
            today = { today },
            newKey = { "key-${++keys}" },
        )

    private fun JournalEditorViewModel.editing() = state.value as JournalEditorUiState.Editing

    @Test
    fun `a new entry starts as a private draft of a story, with the bike's parts to choose from`() =
        runTest {
            val vm = editor()

            val form = vm.editing().form
            assertThat(form).isEqualTo(JournalForm())
            assertThat(form.kind).isEqualTo("story")
            assertThat(form.status).isEqualTo(JournalStatus.Draft)
            assertThat(form.isPublic).isFalse()
            assertThat(vm.editing().choices).isEqualTo(ownJournalBike.components)
        }

    @Test
    fun `someone else's bike, a missing entry and an entry that is not the person's are not editable`() =
        runTest {
            bikes.details =
                mapOf(
                    "b-other" to
                        ownJournalBike.copy(summary = ownJournalBike.summary.copy(isOwner = false)),
                    "b-own" to ownJournalBike,
                )
            journal.entries =
                mapOf(
                    "j-own" to ownEntry(),
                    // A reader's answer has no version.
                    "j-read" to ownEntry("j-read", version = null),
                    "j-else" to
                        ownEntry("j-else").let {
                            it.copy(
                                summary =
                                    it.summary.copy(
                                        bike = BikeRef(BikeId("b-x"), "Чужой велосипед")
                                    )
                            )
                        },
                )

            assertThat(editor(bike = "b-other").state.value)
                .isEqualTo(JournalEditorUiState.Unavailable)
            assertThat(editor("nope").state.value).isEqualTo(JournalEditorUiState.Unavailable)
            assertThat(editor("j-read").state.value).isEqualTo(JournalEditorUiState.Unavailable)
            assertThat(editor("j-else").state.value).isEqualTo(JournalEditorUiState.Unavailable)
            assertThat(editor("j-own").state.value)
                .isInstanceOf(JournalEditorUiState.Editing::class.java)
        }

    @Test
    fun `a failure to read offers to try again`() = runTest {
        bikes.nextError = DataError.Offline(java.io.IOException("down"))
        val vm = editor()

        assertThat(vm.state.value).isInstanceOf(JournalEditorUiState.Failed::class.java)
        vm.load()
        assertThat(vm.state.value).isInstanceOf(JournalEditorUiState.Editing::class.java)
    }

    @Test
    fun `an entry being changed starts from what the server holds`() = runTest {
        val form = editor("j-own").editing().form

        assertThat(form.kind).isEqualTo("service")
        assertThat(form.title).isEqualTo("Замена цепи")
        assertThat(form.status).isEqualTo(JournalStatus.Published)
        assertThat(form.isPublic).isTrue()
        assertThat(form.eventDate).isEqualTo("14.09.2026")
        assertThat(form.mileage).isEqualTo("4200")
        assertThat(form.componentIds).containsExactly("jc1")
    }

    @Test
    fun `a draft may be empty and is sent, a published entry says what is missing`() = runTest {
        val vm = editor()

        vm.save()
        assertThat(journal.created).hasSize(1)
        assertThat(vm.editing().saved).isNotNull()

        val published = editor()
        published.setStatus(JournalStatus.Published)
        published.save()

        assertThat(journal.created).hasSize(1)
        assertThat(published.editing().problems)
            .containsExactly(JournalProblem.NoTitle, JournalProblem.NoBody)
    }

    @Test
    fun `a date and a mileage that are not what they should be are told, not guessed`() = runTest {
        val vm = editor()
        vm.setEventDate("31.02.2026")
        vm.setMileage("12k")

        vm.save()

        assertThat(vm.editing().problems)
            .containsExactly(JournalProblem.DateInvalid, JournalProblem.MileageInvalid)
        assertThat(journal.created).isEmpty()

        vm.setEventDate("2026-09-14")
        vm.save()
        assertThat(vm.editing().problems).contains(JournalProblem.DateInvalid)

        vm.setEventDate("14.09.2026")
        vm.setMileage("1500")
        vm.save()
        val (_, draft, _) = journal.created.single()
        assertThat(draft.eventDate).isEqualTo(LocalDate.parse("2026-09-14"))
        assertThat(draft.mileageKm).isEqualTo(1500)
    }

    @Test
    fun `today is filled as the person would type it`() = runTest {
        val vm = editor(today = LocalDate.parse("2026-01-05"))

        vm.setEventDateToday()

        assertThat(vm.editing().form.eventDate).isEqualTo("05.01.2026")
    }

    @Test
    fun `how an installation went is said of a build only`() = runTest {
        val vm = editor()
        vm.setKind("build")
        vm.setInstallationResult("modified")
        vm.save()
        assertThat(journal.created.last().second.installationResult).isEqualTo("modified")

        val other = editor()
        other.setKind("build")
        other.setInstallationResult("modified")
        other.setKind("service")
        other.save()
        assertThat(journal.created.last().second.installationResult).isNull()
    }

    @Test
    fun `choosing the same result again takes it back`() = runTest {
        val vm = editor()
        vm.setKind("build")

        vm.setInstallationResult("direct")
        vm.setInstallationResult("direct")

        assertThat(vm.editing().form.installationResult).isNull()
    }

    @Test
    fun `parts are chosen and taken back, and no more than fifty are kept`() = runTest {
        val vm = editor()

        vm.toggleComponent("c1")
        vm.toggleComponent("c2")
        vm.toggleComponent("c1")
        assertThat(vm.editing().form.componentIds).containsExactly("c2")

        (3..60).forEach { vm.toggleComponent("c$it") }
        assertThat(vm.editing().form.componentIds).hasSize(50)
    }

    @Test
    fun `the parts an entry kept that the bike lost are still offered`() = runTest {
        val gone = BikeComponent("gone", "build", "Старая цепь", "KMC", "")
        journal.entries =
            mapOf("j-own" to ownEntry().let { it.copy(components = it.components + gone) })

        val vm = editor("j-own")

        assertThat(vm.editing().choices.map { it.id }).contains("gone")
        assertThat(vm.editing().form.componentIds).contains("gone")
    }

    @Test
    fun `the same form after a lost answer is sent under the same key, a changed one under a new`() =
        runTest {
            val vm = editor()
            vm.setTitle("Первая")
            journal.writeError = DataError.Offline(java.io.IOException("down"))

            vm.save()
            assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.error_offline))
            vm.save()
            vm.setTitle("Другая")
            vm.save()

            val sent = journal.created
            assertThat(sent).hasSize(2)
            assertThat(sent[0].third).isEqualTo("key-1")
            assertThat(sent[1].third).isEqualTo("key-2")
        }

    @Test
    fun `an entry changed sends only what changed, under the version that was read`() = runTest {
        val vm = editor("j-own")
        vm.setTitle("Замена цепи и кассеты")

        vm.save()

        val (id, patch, version) = journal.updated.single()
        assertThat(id.value).isEqualTo("j-own")
        assertThat(patch.title).isEqualTo("Замена цепи и кассеты")
        assertThat(patch.body).isNull()
        assertThat(patch.status).isNull()
        assertThat(version).isEqualTo("\"e0\"")
        // The next change names the version this answer came with.
        vm.setTitle("Ещё раз")
        vm.save()
        assertThat(journal.updated.last().third).isEqualTo("\"e1\"")
    }

    @Test
    fun `a form that changed nothing sends nothing`() = runTest {
        val vm = editor("j-own")

        vm.save()

        assertThat(journal.updated).isEmpty()
        assertThat(vm.editing().saved?.summary?.id?.value).isEqualTo("j-own")
    }

    @Test
    fun `a date and a mileage taken away are cleared`() = runTest {
        val vm = editor("j-own")
        vm.setEventDate("")
        vm.setMileage("")

        vm.save()

        val patch = journal.updated.single().second
        assertThat(patch.clearEventDate).isTrue()
        assertThat(patch.clearMileage).isTrue()
    }

    @Test
    fun `an entry another device changed first offers to be read again`() = runTest {
        val vm = editor("j-own")
        vm.setTitle("Другое")
        journal.writeError = DataError.Rejected(412, "precondition_failed", "")

        vm.save()

        assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.journal_changed_elsewhere))
        assertThat(vm.editing().canReload).isTrue()
        vm.load()
        assertThat(vm.editing().form.title).isEqualTo("Замена цепи")
        assertThat(vm.editing().canReload).isFalse()
    }

    @Test
    fun `publishing without a confirmed address is explained and the form stays`() = runTest {
        val vm = editor()
        vm.setTitle("Заголовок")
        vm.setBody("Текст")
        vm.setStatus(JournalStatus.Published)
        vm.setPublic(true)
        journal.writeError = DataError.Rejected(403, "email_verification_required", "")

        vm.save()

        assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.journal_needs_email))
        assertThat(vm.editing().form.title).isEqualTo("Заголовок")
        assertThat(vm.editing().saved).isNull()
    }

    @Test
    fun `a quota refusal keeps the server's words`() = runTest {
        val vm = editor()
        journal.writeError = DataError.Rejected(409, "conflict", "Записей уже 1000.")

        vm.save()

        assertThat(vm.editing().problem).isEqualTo(UiText.Plain("Записей уже 1000."))
    }

    @Test
    fun `a private bike makes a public entry private, and the form says so`() = runTest {
        bikes.details =
            mapOf(
                "b-own" to
                    ownJournalBike.copy(summary = ownJournalBike.summary.copy(isPublic = false))
            )
        val vm = editor()

        vm.setStatus(JournalStatus.Published)
        vm.setPublic(true)

        assertThat(vm.editing().hiddenByBike).isTrue()
        vm.setPublic(false)
        assertThat(vm.editing().hiddenByBike).isFalse()
    }

    @Test
    fun `an entry is deleted only after the person confirms`() = runTest {
        val vm = editor("j-own")

        vm.askDelete()
        assertThat(vm.editing().confirmingDelete).isTrue()
        vm.cancelDelete()
        assertThat(vm.editing().confirmingDelete).isFalse()
        assertThat(journal.deleted).isEmpty()

        vm.askDelete()
        vm.confirmDelete()

        assertThat(journal.deleted.single().value).isEqualTo("j-own")
        assertThat(vm.editing().deleted).isTrue()
    }

    @Test
    fun `a new entry has nothing to delete`() = runTest {
        val vm = editor()

        vm.askDelete()

        assertThat(vm.editing().confirmingDelete).isFalse()
    }

    @Test
    fun `a refused deletion says why and keeps the form`() = runTest {
        val vm = editor("j-own")
        journal.writeError = DataError.Offline(java.io.IOException("down"))

        vm.askDelete()
        vm.confirmDelete()

        assertThat(vm.editing().deleted).isFalse()
        assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.error_offline))
    }

    @Test
    fun `the form is frozen while it is being saved`() = runTest {
        val gate = CompletableDeferred<Unit>()
        journal.hold = gate
        val vm = editor()
        vm.setTitle("Заголовок")

        vm.save()
        vm.setTitle("Не сохранится")

        assertThat(vm.editing().saving).isTrue()
        assertThat(vm.editing().form.title).isEqualTo("Заголовок")
        gate.complete(Unit)
        assertThat(vm.editing().saved).isNotNull()
    }
}
