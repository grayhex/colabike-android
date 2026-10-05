package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.intents.IntentEditorUiState
import ru.colabike.app.intents.IntentEditorViewModel
import ru.colabike.app.intents.IntentSegment
import ru.colabike.app.intents.IntentUiState
import ru.colabike.app.intents.IntentViewModel
import ru.colabike.app.intents.IntentsViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.IntentFold
import ru.colabike.core.model.IntentProblem
import ru.colabike.core.model.IntentReadiness
import ru.colabike.core.model.IntentStatus
import ru.colabike.core.model.IntentVisibility

class IntentsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeIntents()

    @Test
    fun `the community's intentions are the first list, and the person's own is another`() =
        runTest {
            repository.mine = listOf(sampleIntent(3, own = true))
            val vm = IntentsViewModel(repository)

            assertThat(vm.state.value.segment).isEqualTo(IntentSegment.Community)
            assertThat(vm.state.value.page.items.map { it.own }).containsExactly(false, false)

            vm.select(IntentSegment.Mine)

            assertThat(vm.state.value.segment).isEqualTo(IntentSegment.Mine)
            assertThat(vm.state.value.page.items.map { it.own }).containsExactly(true)
        }

    @Test
    fun `a failed first page says why and retrying recovers`() = runTest {
        repository.loadError = DataError.Offline(IOException())
        val vm = IntentsViewModel(repository)

        assertThat(vm.state.value.page.error).isEqualTo(UiText.Res(R.string.error_offline))

        repository.loadError = null
        vm.retry()

        assertThat(vm.state.value.page.items).hasSize(2)
    }

    @Test
    fun `an empty list is empty, not an error`() = runTest {
        repository.community = emptyList()

        val vm = IntentsViewModel(repository)

        assertThat(vm.state.value.page.isEmpty).isTrue()
    }
}

class IntentViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val own = sampleIntent(3, own = true, visibility = IntentVisibility.Private)
    private val repository = FakeIntents(mine = listOf(own))

    @Test
    fun `an intention is read, and one that is not there is simply unavailable`() = runTest {
        val found = IntentViewModel(repository, own.id)
        val gone = IntentViewModel(repository, "b3000000-0000-4000-8000-0000000000ff")

        assertThat((found.state.value as IntentUiState.Loaded).intent.id).isEqualTo(own.id)
        assertThat(gone.state.value).isEqualTo(IntentUiState.Unavailable)
    }

    @Test
    fun `a failed read says why and reading again recovers`() = runTest {
        repository.loadError = DataError.Server(503, null)
        val vm = IntentViewModel(repository, own.id)
        assertThat(vm.state.value).isInstanceOf(IntentUiState.Failed::class.java)

        repository.loadError = null
        vm.load()

        assertThat(vm.state.value).isInstanceOf(IntentUiState.Loaded::class.java)
    }

    @Test
    fun `cancelling closes the intention and it stays as cancelled`() = runTest {
        val vm = IntentViewModel(repository, own.id)

        vm.cancel()

        val loaded = vm.state.value as IntentUiState.Loaded
        assertThat(repository.cancelled).containsExactly(own.id)
        assertThat(loaded.intent.status).isEqualTo(IntentStatus.Cancelled)
        assertThat(loaded.intent.editable).isFalse()
    }

    @Test
    fun `deleting removes it and asks the screen to close`() = runTest {
        val vm = IntentViewModel(repository, own.id)

        vm.delete()

        assertThat((vm.state.value as IntentUiState.Loaded).deleted).isTrue()
        assertThat(repository.deleted).containsExactly(own.id)
    }

    @Test
    fun `an intention removed meanwhile becomes unavailable, a refusal keeps the page`() = runTest {
        val vm = IntentViewModel(repository, own.id)
        repository.failNext = DataError.Rejected(409, "conflict", "")

        vm.cancel()

        val refused = vm.state.value as IntentUiState.Loaded
        assertThat(refused.problem).isEqualTo(UiText.Res(R.string.intent_conflict))
        assertThat(refused.busy).isFalse()

        repository.failNext = DataError.NotFound()
        vm.delete()
        assertThat(vm.state.value).isEqualTo(IntentUiState.Unavailable)
    }
}

class IntentEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    // A Wednesday evening, 2026-10-07 20:00 in Moscow.
    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = Clock.fixed(Instant.parse("2026-10-07T17:00:00Z"), ZoneOffset.UTC)
    private val repository = FakeIntents()
    private var keys = 0

    private fun create() =
        IntentEditorViewModel(
            repository,
            clock,
            moscow,
            id = null,
            newKey = {
                "00000000-0000-4000-8000-00000000000${++keys}"
            },
        )

    private fun editing(vm: IntentEditorViewModel) = vm.state.value as IntentEditorUiState.Editing

    @Test
    fun `a new form is private, ready, and prefilled with the coming Saturday`() = runTest {
        val form = editing(create()).form

        assertThat(form.visibility).isEqualTo(IntentVisibility.Private)
        assertThat(form.readiness).isEqualTo(IntentReadiness.Ready)
        assertThat(form.allowSuggestions).isFalse()
        assertThat(form.timeZone).isEqualTo(moscow)
        assertThat(form.windows.single().start).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 0))
        assertThat(form.windows.single().end).isEqualTo(LocalDateTime.of(2026, 10, 10, 13, 0))
    }

    @Test
    fun `saving without an area names the problem and sends nothing`() = runTest {
        val vm = create()

        vm.save()

        assertThat(editing(vm).problems).containsExactly(IntentProblem.NoArea)
        assertThat(repository.created).isEmpty()
    }

    @Test
    fun `a valid form is created with its key and the intention is shown as saved`() = runTest {
        val vm = create()
        vm.setAreaLabel("Парк Горького")
        vm.setPurpose("social")

        vm.save()

        val state = editing(vm)
        assertThat(repository.created).hasSize(1)
        val (draft, key) = repository.created.single()
        assertThat(key).isEqualTo("00000000-0000-4000-8000-000000000001")
        assertThat(draft.passport.areaLabel).isEqualTo("Парк Горького")
        assertThat(draft.passport.purpose).isEqualTo("social")
        assertThat(draft.visibility).isEqualTo(IntentVisibility.Private)
        assertThat(state.saved).isNotNull()
        assertThat(state.saving).isFalse()
    }

    @Test
    fun `after a lost answer the same form is sent with the same key, a changed form with a new one`() =
        runTest {
            val vm = create()
            vm.setAreaLabel("Парк")
            repository.failNext = DataError.Offline(IOException())

            vm.save()
            assertThat(editing(vm).problem).isEqualTo(UiText.Res(R.string.error_offline))
            vm.save()
            vm.setAreaLabel("Парк Победы")
            repository.failNext = DataError.Offline(IOException())
            vm.save()
            vm.save()

            assertThat(repository.created.map { it.second })
                .containsExactly(
                    "00000000-0000-4000-8000-000000000001",
                    "00000000-0000-4000-8000-000000000001",
                    "00000000-0000-4000-8000-000000000002",
                    "00000000-0000-4000-8000-000000000002",
                )
                .inOrder()
        }

    @Test
    fun `the same key makes one intention however many times it is sent`() = runTest {
        val vm = create()
        vm.setAreaLabel("Парк")
        vm.save()
        val first = editing(vm).saved

        // A second form saved with the very same key (a lost answer): the server returns the same
        // one.
        val again =
            repository.create(repository.created.single().first, repository.created.single().second)

        assertThat(again.id).isEqualTo(first?.id)
        assertThat(repository.mine).hasSize(1)
    }

    @Test
    fun `a window keeps its length when its start moves, and an end before the start is the next day`() =
        runTest {
            val vm = create()

            vm.setStart(0, LocalTime.of(9, 0))
            assertThat(editing(vm).form.windows.single().end)
                .isEqualTo(LocalDateTime.of(2026, 10, 10, 12, 0))

            vm.setEnd(0, LocalTime.of(8, 0))
            assertThat(editing(vm).form.windows.single().end)
                .isEqualTo(LocalDateTime.of(2026, 10, 11, 8, 0))

            vm.setDate(0, LocalDate.of(2026, 10, 11))
            val moved = editing(vm).form.windows.single()
            assertThat(moved.start).isEqualTo(LocalDateTime.of(2026, 10, 11, 9, 0))
            assertThat(moved.end).isEqualTo(LocalDateTime.of(2026, 10, 12, 8, 0))
        }

    @Test
    fun `at most four windows, never fewer than one`() = runTest {
        val vm = create()

        repeat(6) { vm.addWindow() }
        assertThat(editing(vm).form.windows).hasSize(4)
        assertThat(editing(vm).form.windows[1].start)
            .isEqualTo(LocalDateTime.of(2026, 10, 11, 10, 0))

        repeat(6) { vm.removeWindow(0) }
        assertThat(editing(vm).form.windows).hasSize(1)
    }

    @Test
    fun `overlapping windows are named before anything is sent`() = runTest {
        val vm = create()
        vm.setAreaLabel("Парк")
        vm.addWindow()
        vm.setDate(1, LocalDate.of(2026, 10, 10))
        vm.setStart(1, LocalTime.of(12, 0))

        vm.save()

        assertThat(editing(vm).problems).containsExactly(IntentProblem.Overlap)
        assertThat(repository.created).isEmpty()
    }

    @Test
    fun `a pace or a surface chosen twice is taken back`() = runTest {
        val vm = create()

        vm.setPace("sporty")
        assertThat(editing(vm).form.pace).isEqualTo("sporty")
        vm.setPace("sporty")
        assertThat(editing(vm).form.pace).isNull()
    }

    @Test
    fun `publishing to the community is the person's own choice`() = runTest {
        val vm = create()
        vm.setAreaLabel("Парк")

        vm.setVisibility(IntentVisibility.Community)
        vm.save()

        assertThat(repository.created.single().first.visibility)
            .isEqualTo(IntentVisibility.Community)
    }

    @Test
    fun `a repeated hour needs a fold and then goes through`() = runTest {
        // Berlin on 2026-10-25: 02:30 happens twice. The clock is a week before.
        val berlin = ZoneId.of("Europe/Berlin")
        val vm =
            IntentEditorViewModel(
                repository,
                Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC),
                berlin,
                id = null,
            )
        vm.setAreaLabel("Тиргартен")
        vm.setDate(0, LocalDate.of(2026, 10, 25))
        vm.setStart(0, LocalTime.of(2, 30))
        vm.setEnd(0, LocalTime.of(5, 0))

        vm.save()
        assertThat(editing(vm).problems).containsExactly(IntentProblem.TimeIsRepeated(0))

        vm.setFold(0, start = true, IntentFold.Earlier)
        vm.save()

        assertThat(editing(vm).problems).isEmpty()
        assertThat(repository.created.single().first.windows.single().startFold)
            .isEqualTo(IntentFold.Earlier)
    }

    // --- changing one's own --------------------------------------------------------------------

    @Test
    fun `an own intention is loaded into the form with all it held, and saved with its version`() =
        runTest {
            val own =
                sampleIntent(3, own = true, visibility = IntentVisibility.Private).let {
                    it.copy(
                        passport =
                            it.passport.copy(
                                distanceKm = ru.colabike.core.model.Range(20.0, 40.0),
                                difficulty = "easy",
                            )
                    )
                }
            repository.mine = listOf(own)
            val vm = IntentEditorViewModel(repository, clock, moscow, own.id)

            val form = editing(vm).form
            assertThat(form.areaLabel).isEqualTo("Парк Горького")
            assertThat(form.windows.single().start).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 0))
            vm.setReadiness(IntentReadiness.Considering)
            vm.save()

            val (id, draft, version) = repository.replaced.single()
            assertThat(id).isEqualTo(own.id)
            assertThat(version).isEqualTo(own.version)
            // What the form has no field for goes back as it was.
            assertThat(draft.passport.distanceKm)
                .isEqualTo(ru.colabike.core.model.Range(20.0, 40.0))
            assertThat(draft.passport.difficulty).isEqualTo("easy")
            assertThat(draft.readiness).isEqualTo(IntentReadiness.Considering)
        }

    @Test
    fun `a cancelled, foreign or missing intention cannot be changed`() = runTest {
        repository.mine = listOf(sampleIntent(3, own = true, status = IntentStatus.Cancelled))
        repository.community = listOf(sampleIntent(1))

        val cancelled =
            IntentEditorViewModel(repository, clock, moscow, repository.mine.single().id)
        val foreign =
            IntentEditorViewModel(repository, clock, moscow, repository.community.single().id)
        val missing =
            IntentEditorViewModel(repository, clock, moscow, "b3000000-0000-4000-8000-0000000000ff")

        assertThat(cancelled.state.value).isEqualTo(IntentEditorUiState.Unavailable)
        assertThat(foreign.state.value).isEqualTo(IntentEditorUiState.Unavailable)
        assertThat(missing.state.value).isEqualTo(IntentEditorUiState.Unavailable)
    }

    @Test
    fun `a change another device got in first says so and can be read again`() = runTest {
        val own = sampleIntent(3, own = true)
        repository.mine = listOf(own)
        val vm = IntentEditorViewModel(repository, clock, moscow, own.id)
        repository.failNext = DataError.Rejected(412, "precondition_failed", "")

        vm.save()

        val state = editing(vm)
        assertThat(state.problem).isEqualTo(UiText.Res(R.string.intent_changed_elsewhere))
        assertThat(state.canReload).isTrue()
        assertThat(state.saving).isFalse()

        vm.load()
        assertThat(editing(vm).problem).isNull()
    }

    @Test
    fun `too many open intentions is said in words`() = runTest {
        val vm = create()
        vm.setAreaLabel("Парк")
        repository.failNext = DataError.Rejected(409, "conflict", "")

        vm.save()

        assertThat(editing(vm).problem).isEqualTo(UiText.Res(R.string.intent_too_many))
    }

    @Test
    fun `an unconfirmed e-mail for a published intention is said in words`() = runTest {
        val vm = create()
        vm.setAreaLabel("Парк")
        vm.setVisibility(IntentVisibility.Community)
        repository.failNext = DataError.Rejected(403, "email_verification_required", "")

        vm.save()

        assertThat(editing(vm).problem).isEqualTo(UiText.Res(R.string.error_email_verification))
    }

    @Test
    fun `an edit of the form clears the old findings`() = runTest {
        val vm = create()
        vm.save()
        assertThat(editing(vm).problems).isNotEmpty()

        vm.setAreaLabel("Парк")

        assertThat(editing(vm).problems).isEmpty()
    }
}
