package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.people.PersonViewModel
import ru.colabike.app.safety.BlockedViewModel
import ru.colabike.app.safety.ReportViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportReason
import ru.colabike.core.model.ReportTarget

class ReportViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val safety = FakeSafety()
    private val target = ReportTarget(ReportKind.Bike, "b0000000-0000-4000-8000-000000000001")

    @Test
    fun `nothing can be sent before a reason is chosen`() = runTest {
        val vm = ReportViewModel(safety, target)

        assertThat(vm.state.value.canSend).isFalse()
        vm.send()

        assertThat(safety.reports).isEmpty()
    }

    @Test
    fun `the chosen reason goes with the object, and the person is thanked`() = runTest {
        val vm = ReportViewModel(safety, target)

        vm.choose(ReportReason.Abuse)
        vm.send()

        assertThat(safety.reports).containsExactly(target to ReportReason.Abuse)
        assertThat(vm.state.value.sent).isTrue()
        assertThat(vm.state.value.sending).isFalse()
        // Once sent it cannot be sent twice from the same dialog.
        vm.send()
        assertThat(safety.reports).hasSize(1)
    }

    @Test
    fun `a report the server already had is a thanks too`() = runTest {
        safety.created = false
        val vm = ReportViewModel(safety, target)
        vm.choose(ReportReason.Spam)

        vm.send()

        assertThat(vm.state.value.sent).isTrue()
    }

    @Test
    fun `a failure keeps the reason and the dialog, and sending again is one tap`() = runTest {
        val vm = ReportViewModel(safety, target)
        vm.choose(ReportReason.Other)
        safety.nextError = DataError.Offline(java.io.IOException("down"))

        vm.send()

        assertThat(vm.state.value.sent).isFalse()
        assertThat(vm.state.value.error).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(vm.state.value.reason).isEqualTo(ReportReason.Other)
        vm.send()
        assertThat(vm.state.value.sent).isTrue()
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `a closed dialog starts the next one from nothing`() = runTest {
        val vm = ReportViewModel(safety, target)
        vm.choose(ReportReason.Spam)
        vm.send()

        vm.reset()

        assertThat(vm.state.value.sent).isFalse()
        assertThat(vm.state.value.reason).isNull()
    }
}

class BlockedViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val everyone = people(0, 3).map { it.copy(relationship = blockedRelationship) }
    private val safety = FakeSafety(everyone)

    @Test
    fun `it lists the people the viewer blocked`() = runTest {
        val vm = BlockedViewModel(safety)

        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.people.map { it.person.id.value })
            .containsExactly("u0", "u1", "u2")
            .inOrder()
    }

    @Test
    fun `unblocking sends the change and the person leaves the list`() = runTest {
        val vm = BlockedViewModel(safety)

        vm.unblock(everyone[1].person.id)

        assertThat(safety.blocks).containsExactly(everyone[1].person.id to false)
        assertThat(vm.state.value.people.map { it.person.id.value }).containsExactly("u0", "u2")
        assertThat(vm.state.value.unblocking).isEmpty()
    }

    @Test
    fun `an unblocking that failed keeps the person and says so`() = runTest {
        val vm = BlockedViewModel(safety)
        safety.nextError = DataError.Offline(java.io.IOException("down"))

        vm.unblock(everyone[0].person.id)

        assertThat(vm.state.value.people).hasSize(3)
        assertThat(vm.state.value.notice).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(vm.state.value.unblocking).isEmpty()
    }

    @Test
    fun `someone unblocked on their own page leaves the open list at once`() = runTest {
        val vm = BlockedViewModel(safety)

        safety.setBlocked(everyone[2].person.id, blocked = false)

        assertThat(vm.state.value.people.map { it.person.id.value }).containsExactly("u0", "u1")
    }

    @Test
    fun `someone blocked on their own page while the list is kept appears in it`() = runTest {
        safety.blockedPeople = everyone.take(2)
        val vm = BlockedViewModel(safety)
        assertThat(vm.state.value.people).hasSize(2)

        safety.blockedPeople = everyone
        safety.setBlocked(everyone[2].person.id, blocked = true)

        assertThat(vm.state.value.people.map { it.person.id.value })
            .containsExactly("u0", "u1", "u2")
            .inOrder()
    }

    @Test
    fun `a list that could not be refreshed after a block keeps what it showed`() = runTest {
        val vm = BlockedViewModel(safety)

        safety.blockedPeople = emptyList()
        // The block itself goes through; only the next read of the list fails.
        safety.listError = DataError.Offline(java.io.IOException("down"))
        safety.setBlocked(everyone[2].person.id, blocked = true)

        assertThat(vm.state.value.people).hasSize(3)
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `when the list cannot be read it offers to try again`() = runTest {
        safety.nextError = DataError.Offline(java.io.IOException("down"))

        val vm = BlockedViewModel(safety)

        assertThat(vm.state.value.error).isEqualTo(UiText.Res(R.string.error_offline))
        vm.load()
        assertThat(vm.state.value.people).hasSize(3)
    }

    @Test
    fun `the next page is asked for with its cursor and added once`() = runTest {
        val paged =
            object : ru.colabike.core.model.SafetyRepository by safety {
                override suspend fun blocked(cursor: String?, limit: Int) =
                    if (cursor == null) Page(everyone.take(2), "next")
                    else Page(everyone.drop(1), null)
            }
        val vm = BlockedViewModel(paged)

        vm.loadMore()

        assertThat(vm.state.value.people.map { it.person.id.value })
            .containsExactly("u0", "u1", "u2")
            .inOrder()
        assertThat(vm.state.value.nextCursor).isNull()
    }
}

class PersonBlockTest {
    @get:Rule val main = MainDispatcherRule()

    private val people = FakePeople()
    private val bikes = FakeBikes()
    private val safety = FakeSafety()

    private fun vm(): PersonViewModel =
        PersonViewModel(people, bikes, rider.id.value, safety = safety)

    private fun serverBlocked(blocked: Boolean) {
        val profile =
            profileOf(
                relationship =
                    Relationship(
                        isSelf = false,
                        following = false,
                        followedBy = false,
                        friends = false,
                        blockedByMe = blocked,
                    )
            )
        people.profiles = mapOf(rider.id.value to profile, rider.username to profile)
    }

    @Test
    fun `blocking asks first and sends nothing until it is confirmed`() = runTest {
        val vm = vm()

        vm.toggleBlock()

        assertThat(vm.state.value.blockPrompt).isTrue()
        assertThat(safety.blocks).isEmpty()
        vm.dismissBlockPrompt()
        assertThat(vm.state.value.blockPrompt).isFalse()
        assertThat(safety.blocks).isEmpty()
    }

    @Test
    fun `a confirmed block is sent and the page is read again, not guessed`() = runTest {
        val vm = vm()
        vm.toggleBlock()
        serverBlocked(true)

        vm.confirmBlock()

        assertThat(safety.blocks).containsExactly(rider.id to true)
        assertThat(vm.state.value.profile?.relationship?.blockedByMe).isTrue()
        assertThat(vm.state.value.blockPrompt).isFalse()
        assertThat(vm.state.value.blockBusy).isFalse()
    }

    @Test
    fun `unblocking is one tap and needs no question`() = runTest {
        serverBlocked(true)
        val vm = vm()
        serverBlocked(false)

        vm.toggleBlock()

        assertThat(vm.state.value.blockPrompt).isFalse()
        assertThat(safety.blocks).containsExactly(rider.id to false)
        assertThat(vm.state.value.profile?.relationship?.blockedByMe).isFalse()
    }

    @Test
    fun `a block that failed is told and the page stays as it was`() = runTest {
        val vm = vm()
        vm.toggleBlock()
        safety.nextError = DataError.Offline(java.io.IOException("down"))

        vm.confirmBlock()

        assertThat(vm.state.value.blockError).isEqualTo(UiText.Res(R.string.error_offline))
        assertThat(vm.state.value.profile?.relationship?.blockedByMe).isFalse()
        assertThat(vm.state.value.blockBusy).isFalse()
    }

    @Test
    fun `one cannot block oneself`() = runTest {
        people.profiles =
            mapOf(
                rider.id.value to
                    profileOf(
                        relationship =
                            Relationship(
                                isSelf = true,
                                following = false,
                                followedBy = false,
                                friends = false,
                            )
                    )
            )
        val vm = vm()

        vm.toggleBlock()

        assertThat(vm.state.value.blockPrompt).isFalse()
        assertThat(safety.blocks).isEmpty()
    }

    @Test
    fun `a block made elsewhere shows on the open page`() = runTest {
        val vm = vm()
        serverBlocked(true)

        safety.setBlocked(rider.id, blocked = true)

        assertThat(vm.state.value.profile?.relationship?.blockedByMe).isTrue()
    }
}

private val blockedRelationship =
    Relationship(
        isSelf = false,
        following = false,
        followedBy = false,
        friends = false,
        blockedByMe = true,
    )
