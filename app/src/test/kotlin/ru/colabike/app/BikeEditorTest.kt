package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.bikes.BikeEditorUiState
import ru.colabike.app.bikes.BikeEditorViewModel
import ru.colabike.app.bikes.BikeForm
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikePatch
import ru.colabike.core.model.BikeProblem
import ru.colabike.core.model.ClassificationDraft
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PriceVisibility
import ru.colabike.core.model.toDraft

class BikeEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val own =
        PreviewData.bikeDetail.fromDraft(
            BikeId("b-own"),
            PreviewData.bikeDetail.toDraft(),
            "\"v1\"",
        )
    private val bikes = FakeBikes().also { it.details = mapOf("b-own" to own) }
    private var keys = 0

    private fun creating() = BikeEditorViewModel(bikes, null) { "key-${++keys}" }

    private fun changing(id: String = "b-own") = BikeEditorViewModel(bikes, BikeId(id)) { "key" }

    private fun BikeEditorViewModel.editing() = state.value as BikeEditorUiState.Editing

    private fun BikeEditorViewModel.fillValid() {
        setName("Мой гравел")
        setYear("2023")
        setCategory("road_gravel")
    }

    // --- a new bike ------------------------------------------------------------------------------

    @Test
    fun `a new form is empty and private`() = runTest {
        val form = creating().editing().form

        assertThat(form).isEqualTo(BikeForm())
        assertThat(form.isPublic).isFalse()
    }

    @Test
    fun `a category takes its subtypes and drops one that is not its own`() = runTest {
        val vm = creating()

        vm.setCategory("mtb")
        vm.setSubtype("trail")
        assertThat(vm.editing().form.classification.subtype).isEqualTo("trail")
        vm.setCategory("bmx")

        assertThat(vm.editing().form.classification.category).isEqualTo("bmx")
        assertThat(vm.editing().form.classification.subtype).isNull()
    }

    @Test
    fun `a subtype of another category is not accepted and choosing a chosen one takes it back`() =
        runTest {
            val vm = creating()
            vm.setCategory("mtb")

            vm.setSubtype("gravel")
            assertThat(vm.editing().form.classification.subtype).isNull()
            vm.setSubtype("enduro")
            vm.setSubtype("enduro")

            assertThat(vm.editing().form.classification.subtype).isNull()
        }

    @Test
    fun `at most three uses, a fourth is not added and a chosen one is taken back`() = runTest {
        val vm = creating()

        listOf("racing", "xc", "trail", "enduro").forEach(vm::toggleUse)
        assertThat(vm.editing().form.classification.uses).containsExactly("racing", "xc", "trail")
        vm.toggleUse("xc")

        assertThat(vm.editing().form.classification.uses).containsExactly("racing", "trail")
        vm.toggleUse("not-a-use")
        assertThat(vm.editing().form.classification.uses).containsExactly("racing", "trail")
    }

    @Test
    fun `saving an empty form sends nothing and says what is missing`() = runTest {
        val vm = creating()

        vm.save()

        assertThat(bikes.created).isEmpty()
        assertThat(vm.editing().problems)
            .containsExactly(BikeProblem.NoName, BikeProblem.NoYear, BikeProblem.NoCategory)
            .inOrder()
        vm.setName("Ещё")
        assertThat(vm.editing().problems).isEmpty()
    }

    @Test
    fun `numbers that are not numbers are findings of the form, not values`() = runTest {
        val vm = creating()
        vm.fillValid()
        vm.setWeight("тяжёлый")
        vm.setMileage("12k")
        vm.setPrice("1 000")
        vm.setYear("20x3")

        vm.save()

        assertThat(bikes.created).isEmpty()
        assertThat(vm.editing().problems)
            .containsAtLeast(
                BikeProblem.WeightInvalid,
                BikeProblem.MileageInvalid,
                BikeProblem.PriceInvalid,
                BikeProblem.YearOutOfRange,
            )
    }

    @Test
    fun `a decimal comma is read as a point and a blank number is nothing`() = runTest {
        val vm = creating()
        vm.fillValid()
        vm.setWeight("12,5")
        vm.setPrice("")
        vm.setMileage("")

        vm.save()

        val draft = bikes.created.single().first
        assertThat(draft.weightKg).isEqualTo(12.5)
        assertThat(draft.priceRub).isNull()
        assertThat(draft.mileageKm).isEqualTo(0)
    }

    @Test
    fun `a good form is created private, with its key, and the page opens`() = runTest {
        val vm = creating()
        vm.fillValid()

        vm.save()

        val (draft, key) = bikes.created.single()
        assertThat(key).isEqualTo("key-1")
        assertThat(draft.name).isEqualTo("Мой гравел")
        assertThat(draft.year).isEqualTo(2023)
        assertThat(draft.isPublic).isFalse()
        val saved = vm.editing().saved!!
        assertThat(saved.summary.name).isEqualTo("Мой гравел")
        assertThat(vm.editing().saving).isFalse()
    }

    @Test
    fun `the same form after a lost answer is sent with the same key, a changed one with a new`() =
        runTest {
            val vm = creating()
            vm.fillValid()
            bikes.writeError = DataError.Offline(java.io.IOException("down"))

            vm.save()
            assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.error_offline))
            assertThat(vm.editing().saved).isNull()
            vm.save()
            assertThat(bikes.created.single().second).isEqualTo("key-1")
        }

    @Test
    fun `a form changed after a failure is a new bike with a new key`() = runTest {
        val vm = creating()
        vm.fillValid()
        bikes.writeError = DataError.Offline(java.io.IOException("down"))
        vm.save()
        // The first attempt used a key; the form changes; the next attempt must not reuse it.
        vm.setName("Другое имя")

        vm.save()

        assertThat(bikes.created.single().second).isEqualTo("key-2")
        assertThat(keys).isEqualTo(2)
    }

    @Test
    fun `publishing without a confirmed address is explained and the form stays`() = runTest {
        val vm = creating()
        vm.fillValid()
        vm.setPublic(true)
        bikes.writeError =
            DataError.Rejected(403, "email_verification_required", "Подтвердите почту")

        vm.save()

        assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.bike_needs_email))
        assertThat(vm.editing().form.isPublic).isTrue()
        assertThat(vm.editing().form.name).isEqualTo("Мой гравел")
        assertThat(vm.editing().saved).isNull()
    }

    @Test
    fun `the form does not take a key stroke while it is being saved`() = runTest {
        val vm = creating()
        vm.fillValid()
        bikes.hold = kotlinx.coroutines.CompletableDeferred()
        vm.save()
        assertThat(vm.editing().saving).isTrue()

        vm.setName("Поздно")
        vm.save()

        assertThat(vm.editing().form.name).isEqualTo("Мой гравел")
        bikes.hold!!.complete(Unit)
        assertThat(bikes.created).hasSize(1)
    }

    // --- a change --------------------------------------------------------------------------------

    @Test
    fun `an own bike opens in the form as the server holds it`() = runTest {
        val vm = changing()

        val editing = vm.editing()
        assertThat(editing.form.name).isEqualTo("Городской Трэвел")
        assertThat(editing.form.year).isEqualTo("2020")
        assertThat(editing.form.weight).isEqualTo("14.2")
        assertThat(editing.form.price).isEqualTo("85000")
        assertThat(editing.form.classification.subtype).isEqualTo("trail")
        assertThat(editing.editing?.version).isEqualTo("\"v1\"")
    }

    @Test
    fun `only the changed fields go, with the version that was read`() = runTest {
        val vm = changing()

        vm.setColor("Красный")
        vm.save()

        val (id, patch, version) = bikes.updated.single()
        assertThat(id).isEqualTo(BikeId("b-own"))
        assertThat(patch).isEqualTo(BikePatch(color = "Красный"))
        assertThat(version).isEqualTo("\"v1\"")
        assertThat(vm.editing().saved?.summary?.name).isEqualTo("Городской Трэвел")
        // The version of the next change is the one this answer came with.
        assertThat(vm.editing().editing?.version).isEqualTo("\"v2\"")
    }

    @Test
    fun `a weight and a price emptied are cleared, not forgotten`() = runTest {
        val vm = changing()

        vm.setWeight("")
        vm.setPrice("")
        vm.save()

        val patch = bikes.updated.single().second
        assertThat(patch.clearWeight).isTrue()
        assertThat(patch.clearPrice).isTrue()
    }

    @Test
    fun `a changed type and audience go as the whole type and the audience`() = runTest {
        val vm = changing()

        vm.setCategory("urban_touring")
        vm.setPublic(false)
        vm.setPublic(true)
        vm.setPriceVisibility(PriceVisibility(bike = true))
        vm.save()

        val patch = bikes.updated.single().second
        assertThat(patch.classification)
            .isEqualTo(
                ClassificationDraft(
                    category = "urban_touring",
                    subtype = null,
                    suspension = "full_suspension",
                )
            )
        assertThat(patch.priceVisibility).isEqualTo(PriceVisibility(bike = true))
        // The preview bike is already public: the audience did not change, so it is not sent.
        assertThat(patch.isPublic).isNull()
    }

    @Test
    fun `a year that was there is not taken away, nothing is sent`() = runTest {
        val vm = changing()

        vm.setYear("")
        vm.setColor("Красный")
        vm.save()

        assertThat(bikes.updated).isEmpty()
        assertThat(vm.editing().problems).containsExactly(BikeProblem.NoYear)
        assertThat(vm.editing().saved).isNull()
    }

    @Test
    fun `a bike the server holds without a year is changed without one`() = runTest {
        bikes.details = mapOf("b-own" to own.copy(summary = own.summary.copy(year = null)))
        val vm = changing()

        vm.setColor("Красный")
        vm.save()

        assertThat(bikes.updated.single().second).isEqualTo(BikePatch(color = "Красный"))
    }

    @Test
    fun `a form nobody changed sends nothing and closes as saved`() = runTest {
        val vm = changing()

        vm.save()

        assertThat(bikes.updated).isEmpty()
        assertThat(vm.editing().saved).isEqualTo(own)
    }

    @Test
    fun `a bike another device changed first is explained and can be read again`() = runTest {
        val vm = changing()
        vm.setColor("Красный")
        bikes.writeError = DataError.Rejected(412, "precondition_failed", "")

        vm.save()

        assertThat(vm.editing().problem).isEqualTo(UiText.Res(R.string.bike_changed_elsewhere))
        assertThat(vm.editing().canReload).isTrue()
        bikes.details =
            mapOf(
                "b-own" to
                    own.fromDraft(BikeId("b-own"), own.toDraft().copy(color = "Зелёный"), "\"v9\"")
            )
        vm.load()
        assertThat(vm.editing().form.color).isEqualTo("Зелёный")
        assertThat(vm.editing().editing?.version).isEqualTo("\"v9\"")
        assertThat(vm.editing().problem).isNull()
    }

    @Test
    fun `a bike that is not the person's, or has no version, is not editable`() = runTest {
        bikes.details = mapOf("b-other" to own.copy(summary = own.summary.copy(isOwner = false)))

        assertThat(changing("b-other").state.value).isEqualTo(BikeEditorUiState.Unavailable)
        bikes.details = mapOf("b-nover" to own.copy(version = null))
        assertThat(changing("b-nover").state.value).isEqualTo(BikeEditorUiState.Unavailable)
    }

    @Test
    fun `a bike that is gone is unavailable and a failure to read offers to try again`() = runTest {
        assertThat(changing("nobody").state.value).isEqualTo(BikeEditorUiState.Unavailable)
        bikes.nextError = DataError.Offline(java.io.IOException("down"))

        val vm = changing()

        assertThat(vm.state.value).isInstanceOf(BikeEditorUiState.Failed::class.java)
        vm.load()
        assertThat(vm.state.value).isInstanceOf(BikeEditorUiState.Editing::class.java)
    }

    // --- deleting --------------------------------------------------------------------------------

    @Test
    fun `deleting asks first and nothing happens until the answer`() = runTest {
        val vm = changing()

        vm.askDelete()
        assertThat(vm.editing().confirmingDelete).isTrue()
        assertThat(bikes.deleted).isEmpty()
        vm.cancelDelete()

        assertThat(vm.editing().confirmingDelete).isFalse()
        assertThat(bikes.deleted).isEmpty()
    }

    @Test
    fun `a confirmed deletion is sent and the form says the bike is gone`() = runTest {
        val vm = changing()

        vm.askDelete()
        vm.confirmDelete()

        assertThat(bikes.deleted).containsExactly(BikeId("b-own"))
        assertThat(vm.editing().deleted).isTrue()
    }

    @Test
    fun `a bike with rides stays and the server's words are shown`() = runTest {
        val vm = changing()
        bikes.writeError = DataError.Rejected(409, "conflict", "У велосипеда есть покатушки.")

        vm.askDelete()
        vm.confirmDelete()

        assertThat(vm.editing().deleted).isFalse()
        assertThat(vm.editing().problem).isEqualTo(UiText.Plain("У велосипеда есть покатушки."))
    }

    @Test
    fun `a bike deleted elsewhere is as good as deleted here`() = runTest {
        val vm = changing()
        bikes.writeError = DataError.NotFound()

        vm.askDelete()
        vm.confirmDelete()

        assertThat(vm.editing().deleted).isTrue()
    }

    @Test
    fun `a new form has nothing to delete`() = runTest {
        val vm = creating()

        vm.askDelete()

        assertThat(vm.editing().confirmingDelete).isFalse()
    }
}
