package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.bikes.BikePartEditorViewModel
import ru.colabike.app.bikes.BikePartsUiState
import ru.colabike.app.bikes.BikePartsViewModel
import ru.colabike.app.bikes.PartEditorUiState
import ru.colabike.app.bikes.PartForm
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.ComponentPatch
import ru.colabike.core.model.ComponentProblem
import ru.colabike.core.model.DataError
import ru.colabike.core.model.toDraft

private val ownBike =
    PreviewData.bikeDetail.fromDraft(
        BikeId("b-own"),
        PreviewData.bikeDetail.toDraft(),
        "\"v1\"",
    )

class BikePartsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val bikes = FakeBikes().also { it.details = mapOf("b-own" to ownBike) }

    private fun parts(id: String = "b-own") = BikePartsViewModel(bikes, BikeId(id))

    private fun BikePartsViewModel.ready() = state.value as BikePartsUiState.Ready

    @Test
    fun `an own bike's build is listed with the groups that can be put in order`() = runTest {
        val vm = parts()

        assertThat(vm.ready().bike.components).hasSize(ownBike.components.size)
        assertThat(vm.ready().groups.map { it.key })
            .containsExactly("drivetrain", "frame")
            .inOrder()
        assertThat(vm.ready().groups.first().title).isEqualTo("Трансмиссия")
    }

    @Test
    fun `someone else's bike and a missing one have no build to change`() = runTest {
        bikes.details =
            mapOf("b-other" to ownBike.copy(summary = ownBike.summary.copy(isOwner = false)))

        assertThat(parts("b-other").state.value).isEqualTo(BikePartsUiState.Unavailable)
        assertThat(parts("nobody").state.value).isEqualTo(BikePartsUiState.Unavailable)
    }

    @Test
    fun `a failure to read offers to try again`() = runTest {
        bikes.nextError = DataError.Offline(java.io.IOException("down"))

        val vm = parts()

        assertThat(vm.state.value).isInstanceOf(BikePartsUiState.Failed::class.java)
        vm.load()
        assertThat(vm.state.value).isInstanceOf(BikePartsUiState.Ready::class.java)
    }

    @Test
    fun `a group moved down sends the whole order with that one move`() = runTest {
        val vm = parts()
        val before = vm.ready().groups.map { it.key }

        vm.move(before.first(), up = false)

        assertThat(bikes.groupOrders.single()).containsExactly(before[1], before[0]).inOrder()
        assertThat(vm.ready().groups.map { it.key }).containsExactly(before[1], before[0]).inOrder()
        assertThat(vm.ready().ordering).isFalse()
    }

    @Test
    fun `the first group cannot go up and the last cannot go down`() = runTest {
        val vm = parts()
        val keys = vm.ready().groups.map { it.key }

        vm.move(keys.first(), up = true)
        vm.move(keys.last(), up = false)
        vm.move("not-a-group", up = true)

        assertThat(bikes.groupOrders).isEmpty()
    }

    @Test
    fun `a refused order shows why and keeps the old one`() = runTest {
        val vm = parts()
        val before = vm.ready().groups.map { it.key }
        bikes.writeError = DataError.Rejected(403, "email_verification_required", "")

        vm.move(before.first(), up = false)

        assertThat(vm.ready().problem).isEqualTo(UiText.Res(R.string.part_needs_email))
        assertThat(vm.ready().groups.map { it.key }).isEqualTo(before)
    }

    @Test
    fun `a part added elsewhere shows up without leaving the screen`() = runTest {
        val vm = parts()
        val count = vm.ready().bike.components.size
        val editor = BikePartEditorViewModel(bikes, BikeId("b-own"), null) { "key" }
        editor.setCategory("Цепь")
        editor.setName("KMC X11")

        editor.save()

        assertThat(vm.ready().bike.components).hasSize(count + 1)
    }
}

class BikePartEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val bikes = FakeBikes().also { it.details = mapOf("b-own" to ownBike) }
    private var keys = 0

    private fun creating() =
        BikePartEditorViewModel(bikes, BikeId("b-own"), null) { "key-${++keys}" }

    private fun changing(part: String = "c2") =
        BikePartEditorViewModel(bikes, BikeId("b-own"), part) { "key" }

    private fun BikePartEditorViewModel.editing() = state.value as PartEditorUiState.Editing

    @Test
    fun `a new form is empty and builds the build section`() = runTest {
        val form = creating().editing().form

        assertThat(form).isEqualTo(PartForm())
    }

    @Test
    fun `a category of the site gives suggestions, its section and its group`() = runTest {
        val vm = creating()

        vm.setCategory("звон")
        assertThat(vm.editing().suggestions).contains("Звонок")
        vm.pickCategory("Звонок")
        vm.setName("Лёгкий звонок")
        vm.save()

        assertThat(vm.editing().form.section).isEqualTo("accessories")
        val (_, draft, _) = bikes.addedParts.single()
        assertThat(draft.section).isEqualTo("accessories")
        assertThat(draft.groupId).isEqualTo("equipment")
    }

    @Test
    fun `a section the person chose is not changed by the category`() = runTest {
        val vm = creating()

        vm.setSection("accessories")
        vm.setCategory("Цепь")

        assertThat(vm.editing().form.section).isEqualTo("accessories")
    }

    @Test
    fun `a category of the person's own has no group`() = runTest {
        val vm = creating()
        vm.setCategory("Моё особое")
        vm.setName("Что-то")

        vm.save()

        assertThat(bikes.addedParts.single().second.groupId).isEmpty()
    }

    @Test
    fun `an empty form says what is missing and sends nothing`() = runTest {
        val vm = creating()

        vm.save()

        assertThat(vm.editing().problems)
            .containsExactly(ComponentProblem.NoCategory, ComponentProblem.NoName)
        assertThat(bikes.addedParts).isEmpty()
    }

    @Test
    fun `a price that is not a number is a finding of the form`() = runTest {
        val vm = creating()
        vm.setCategory("Цепь")
        vm.setName("KMC")
        vm.setPrice("дорого")

        vm.save()

        assertThat(vm.editing().problems).containsExactly(ComponentProblem.PriceInvalid)
        vm.setPrice("1 200")
        vm.save()
        assertThat(vm.editing().problems).containsExactly(ComponentProblem.PriceInvalid)
        vm.setPrice("1200,5")
        vm.save()
        assertThat(bikes.addedParts.single().second.priceRub).isEqualTo(1200.5)
    }

    @Test
    fun `the same form after a lost answer is the same part, a changed one is new`() = runTest {
        val vm = creating()
        vm.setCategory("Цепь")
        vm.setName("KMC")
        bikes.writeError = DataError.Offline(java.io.IOException("down"))

        vm.save()
        assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.error_offline))
        vm.save()
        assertThat(bikes.addedParts.single().third).isEqualTo("key-1")
    }

    @Test
    fun `a part of a public bike without a confirmed address is explained`() = runTest {
        val vm = creating()
        vm.setCategory("Цепь")
        vm.setName("KMC")
        bikes.writeError = DataError.Rejected(403, "email_verification_required", "")

        vm.save()

        assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.part_needs_email))
        assertThat(vm.editing().form.name).isEqualTo("KMC")
    }

    @Test
    fun `a part opens in the form as the server holds it`() = runTest {
        val form = changing().editing().form

        assertThat(form.category).isEqualTo("Трансмиссия")
        assertThat(form.name).isEqualTo("Shimano Deore 10-speed")
        assertThat(form.price).isEqualTo("12500")
        assertThat(form.url).isEqualTo("https://example.test/deore")
    }

    @Test
    fun `only the changed fields go, and the group stays while the category does`() = runTest {
        val vm = changing()

        vm.setName("Shimano Deore 12-speed")
        vm.save()

        val (id, patch, _) = bikes.changedParts.single()
        assertThat(id).isEqualTo("c2")
        assertThat(patch).isEqualTo(ComponentPatch(name = "Shimano Deore 12-speed"))
    }

    @Test
    fun `a new category gives the part the group of that category`() = runTest {
        val vm = changing()

        vm.setCategory("Рама")
        vm.save()

        val patch = bikes.changedParts.single().second
        assertThat(patch.category).isEqualTo("Рама")
        assertThat(patch.groupId).isEqualTo("frame")
    }

    @Test
    fun `a price emptied is cleared`() = runTest {
        val vm = changing()

        vm.setPrice("")
        vm.save()

        assertThat(bikes.changedParts.single().second.clearPrice).isTrue()
    }

    @Test
    fun `a form nobody changed sends nothing and closes as saved`() = runTest {
        val vm = changing()

        vm.save()

        assertThat(bikes.changedParts).isEmpty()
        assertThat(vm.editing().saved).isTrue()
    }

    @Test
    fun `a part another device changed first can be read again`() = runTest {
        val vm = changing()
        vm.setName("Другое имя")
        bikes.writeError = DataError.Rejected(412, "precondition_failed", "")

        vm.save()

        assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.part_changed_elsewhere))
        assertThat(vm.editing().canReload).isTrue()
        vm.load()
        assertThat(vm.editing().form.name).isEqualTo("Shimano Deore 10-speed")
    }

    @Test
    fun `a part that is not there, or a bike that is not the person's, is unavailable`() = runTest {
        assertThat(changing("nope").state.value).isEqualTo(PartEditorUiState.Unavailable)
        bikes.details =
            mapOf("b-own" to ownBike.copy(summary = ownBike.summary.copy(isOwner = false)))
        assertThat(creating().state.value).isEqualTo(PartEditorUiState.Unavailable)
    }

    @Test
    fun `deleting asks first, then removes, and a part already gone is as good`() = runTest {
        val vm = changing()

        vm.askDelete()
        assertThat(vm.editing().confirmingDelete).isTrue()
        vm.cancelDelete()
        assertThat(bikes.removedParts).isEmpty()
        vm.askDelete()
        vm.confirmDelete()

        assertThat(bikes.removedParts).containsExactly("c2")
        assertThat(vm.editing().deleted).isTrue()

        val again = changing("c3")
        again.askDelete()
        bikes.writeError = DataError.NotFound()
        again.confirmDelete()
        assertThat(again.editing().deleted).isTrue()
    }

    @Test
    fun `a new form has nothing to delete`() = runTest {
        val vm = creating()

        vm.askDelete()

        assertThat(vm.editing().confirmingDelete).isFalse()
    }
}
