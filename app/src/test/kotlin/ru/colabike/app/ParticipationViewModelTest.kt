package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.participation.ParticipationUiState
import ru.colabike.app.participation.ParticipationViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.AgreementChange
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ParticipationResponse
import ru.colabike.core.model.ParticipationState
import ru.colabike.core.model.RequestedDateStatus
import ru.colabike.core.model.RideStatus

class ParticipationViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val ride = "b2000000-0000-4000-8000-0000000000b2"
    private val date = Instant.parse("2026-10-10T07:00:00Z")
    private val repository = FakeParticipation()

    private fun viewModel(at: Instant? = date) = ParticipationViewModel(repository, ride, at)

    private fun loaded(vm: ParticipationViewModel) = vm.state.value as ParticipationUiState.Loaded

    @Test
    fun `the date a notification named is asked for, and the state is what the server says now`() =
        runTest {
            val vm = viewModel()

            assertThat(repository.asked).containsExactly(date)
            assertThat(loaded(vm).participation.state).isEqualTo(ParticipationState.Invited)
            assertThat(loaded(vm).sending).isNull()
        }

    @Test
    fun `a plan that is not the person's to see is unavailable, and a failed read can be repeated`() =
        runTest {
            repository.current = null
            assertThat(viewModel().state.value).isEqualTo(ParticipationUiState.Unavailable)

            repository.current = sampleParticipation()
            repository.loadError = DataError.Offline(IOException())
            val vm = viewModel()
            assertThat(vm.state.value)
                .isEqualTo(ParticipationUiState.Failed(UiText.Res(R.string.error_offline)))

            repository.loadError = null
            vm.load()
            assertThat(vm.state.value).isInstanceOf(ParticipationUiState.Loaded::class.java)
        }

    @Test
    fun `going is sent for the date and the edition on the screen, only after the call`() =
        runTest {
            val vm = viewModel()
            assertThat(repository.answers).isEmpty()

            vm.respond(ParticipationResponse.Accepted)

            assertThat(repository.answers)
                .containsExactly(Triple(ParticipationResponse.Accepted, date, 3))
            assertThat(loaded(vm).participation.state).isEqualTo(ParticipationState.Accepted)
            assertThat(loaded(vm).notice)
                .isEqualTo(UiText.Res(R.string.participation_saved_accepted))
        }

    @Test
    fun `an answer the server will not take is not sent`() = runTest {
        repository.current = sampleParticipation(allowed = emptySet())
        val vm = viewModel()

        vm.respond(ParticipationResponse.Accepted)
        vm.respond(ParticipationResponse.Declined)

        assertThat(repository.answers).isEmpty()
    }

    @Test
    fun `a cancelled date has nothing to answer and nothing is sent`() = runTest {
        repository.current =
            sampleParticipation(
                requested = RequestedDateStatus.Cancelled,
                allowed = emptySet(),
                scheduledAt = null,
            )
        val vm = viewModel()

        vm.respond(ParticipationResponse.Accepted)

        assertThat(repository.answers).isEmpty()
        assertThat(loaded(vm).participation.dateCancelled).isTrue()
    }

    @Test
    fun `a second answer waits for the first`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val vm = viewModel()

        vm.respond(ParticipationResponse.Accepted)
        vm.respond(ParticipationResponse.Maybe)

        assertThat(loaded(vm).sending).isEqualTo(ParticipationResponse.Accepted)
        assertThat(repository.answers).hasSize(1)
        gate.complete(Unit)
        assertThat(loaded(vm).sending).isNull()
    }

    @Test
    fun `terms that changed while the person chose are not confirmed, the new state is shown`() =
        runTest {
            val vm = viewModel()
            repository.conflictWith =
                sampleParticipation(
                    state = ParticipationState.Reconfirm,
                    previous = ParticipationResponse.Accepted,
                    changed = true,
                    revision = 4,
                    changes = setOf(AgreementChange.Place),
                )

            vm.respond(ParticipationResponse.Accepted)

            val state = loaded(vm)
            assertThat(state.changedMeanwhile).isTrue()
            assertThat(state.participation.agreement.revision).isEqualTo(4)
            assertThat(state.participation.state).isEqualTo(ParticipationState.Reconfirm)
            assertThat(state.notice).isNull()
            // The person decides again, for the new edition.
            vm.respond(ParticipationResponse.Accepted)
            assertThat(repository.answers.last().third).isEqualTo(4)
        }

    @Test
    fun `a plan that went away between showing and answering is unavailable`() = runTest {
        val vm = viewModel()
        repository.conflictToGone = true

        vm.respond(ParticipationResponse.Maybe)

        assertThat(vm.state.value).isEqualTo(ParticipationUiState.Unavailable)
    }

    @Test
    fun `a failed answer keeps what was shown and says why`() = runTest {
        val vm = viewModel()
        repository.failNext = DataError.Offline(IOException())

        vm.respond(ParticipationResponse.Declined)

        val state = loaded(vm)
        assertThat(state.problem).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(state.sending).isNull()
        assertThat(state.participation.state).isEqualTo(ParticipationState.Invited)
    }

    @Test
    fun `a whole cancelled plan is told from a cancelled date`() = runTest {
        repository.current =
            sampleParticipation(
                status = RideStatus.Cancelled,
                allowed = emptySet(),
                scheduledAt = null,
                requested = RequestedDateStatus.Cancelled,
            )

        val p = loaded(viewModel()).participation

        assertThat(p.planCancelled).isTrue()
        assertThat(p.dateCancelled).isFalse()
    }
}
