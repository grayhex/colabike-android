package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.bikes.BikeWizardViewModel
import ru.colabike.app.bikes.SearchFailure
import ru.colabike.app.bikes.SearchPhase
import ru.colabike.app.bikes.WizardQuestion
import ru.colabike.app.bikes.WizardStep
import ru.colabike.core.model.BikeProblem
import ru.colabike.core.model.BuildQuery
import ru.colabike.core.model.ComponentProblem
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ResolutionStatus
import ru.colabike.core.model.ResolveRequest
import ru.colabike.core.model.SourceKind

/**
 * The wizard of a new bike, as the site's: search, the parts, the details (docs/adr/0028). The
 * server does the searching and the rules; these are about what the person types and chooses never
 * being lost, and about nothing being decided for them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BikeWizardViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val wizard = FakeBikeWizard()
    private var keys = 0
    private val query = BuildQuery("Giant", "Contend", "AR 1", 2024)

    private fun vm(catalog: FakeCatalog = FakeCatalog().apply { site() }) =
        BikeWizardViewModel(wizard, { catalog.state.value.catalog }, { "key-${++keys}" })

    private fun BikeWizardViewModel.found(
        build: ru.colabike.core.model.ResolvedBuild = FakeBikeWizard.build(query)
    ) {
        wizard.answers += { FakeBikeWizard.resolved(query, build) }
        setSearchText("Giant Contend AR 1 2024")
        search()
    }

    private fun BikeWizardViewModel.fillDetails() {
        setCategory("road_gravel")
        setSubtype("road")
    }

    // --- the search line
    // ---------------------------------------------------------------------------

    @Test
    fun `it opens on the search with nothing asked`() = runTest {
        val vm = vm()

        assertThat(vm.state.value.step).isEqualTo(WizardStep.Search)
        assertThat(vm.state.value.dirty).isFalse()
        assertThat(wizard.requests).isEmpty()
    }

    @Test
    fun `a line that says too little is not sent, and says so`() = runTest {
        val vm = vm()
        vm.setSearchText("Giant")

        vm.search()

        assertThat(wizard.requests).isEmpty()
        assertThat(vm.state.value.phase).isEqualTo(SearchPhase.Failed(SearchFailure.NoQuery))
    }

    @Test
    fun `the line is taken apart with the site's lists and the search asks for variants`() =
        runTest {
            val vm = vm()
            vm.found()

            assertThat(wizard.requests.single())
                .isEqualTo(ResolveRequest.Search(BuildQuery("Giant", "Contend", "AR 1", 2024)))
        }

    @Test
    fun `a build that was found is the parts to check, and the bike is what was asked`() = runTest {
        val vm = vm()
        vm.setGarageName("Мой шоссейник")

        vm.found()

        val state = vm.state.value
        assertThat(state.step).isEqualTo(WizardStep.Build)
        assertThat(state.parts.map { it.category }).containsExactly("Рама", "Седло").inOrder()
        assertThat(state.parts.map { it.groupId }).containsExactly("frame", "cockpit").inOrder()
        assertThat(state.found?.previewId).isEqualTo("pv-1")
        assertThat(state.form.brand).isEqualTo("Giant")
        assertThat(state.form.model).isEqualTo("Contend")
        assertThat(state.form.trim).isEqualTo("AR 1")
        assertThat(state.form.year).isEqualTo("2024")
        // The name of the garage is the person's, and a search leaves it alone.
        assertThat(state.form.name).isEqualTo("Мой шоссейник")
        // Nothing of the page's weight or colour is put in a field by itself.
        assertThat(state.form.weight).isEmpty()
        assertThat(state.form.color).isEmpty()
        assertThat(state.partsEdited).isFalse()
    }

    @Test
    fun `a year the person did not give is the page's, never invented`() = runTest {
        val vm = vm()
        val asked = BuildQuery("Giant", "Contend")
        wizard.answers += {
            FakeBikeWizard.resolved(
                asked,
                FakeBikeWizard.build(asked, year = null, sourceYear = 2023),
            )
        }
        vm.setSearchText("Giant Contend")
        vm.search()

        assertThat(vm.state.value.form.year).isEqualTo("2023")

        val without = vm()
        wizard.answers += {
            FakeBikeWizard.resolved(
                asked,
                FakeBikeWizard.build(asked, year = null, sourceYear = null),
            )
        }
        without.setSearchText("Giant Contend")
        without.search()
        assertThat(without.state.value.form.year).isEmpty()
    }

    // --- variants
    // -----------------------------------------------------------------------------------

    @Test
    fun `several variants are shown and the person chooses one by its own identifier`() = runTest {
        val vm = vm()
        val store = FakeBikeWizard.candidate("c".repeat(64), "Contend AR 1 2023", 2023)
        val official =
            FakeBikeWizard.candidate(
                "d".repeat(64),
                "Contend AR 1",
                null,
                SourceKind.Manufacturer,
                "Giant",
            )
        wizard.answers += { FakeBikeWizard.offered(query, store, official) }
        wizard.answers += {
            FakeBikeWizard.resolved(query, FakeBikeWizard.build(query, name = "Contend AR 1"))
        }
        vm.setSearchText("Giant Contend AR 1 2024")

        vm.search()

        assertThat(vm.state.value.step).isEqualTo(WizardStep.Search)
        assertThat(vm.state.value.offer?.candidates).containsExactly(store, official).inOrder()
        assertThat(vm.state.value.offer?.sources?.complete).isFalse()

        vm.choose(official)

        // Asked by the identifier of the variant, and nothing else: no new search, no page.
        assertThat(wizard.requests.last()).isEqualTo(ResolveRequest.Variant(query, "d".repeat(64)))
        assertThat(wizard.requests).hasSize(2)
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Build)
        assertThat(vm.state.value.offer).isNull()
        // The parts are of the one chosen variant.
        assertThat(vm.state.value.found?.build?.name).isEqualTo("Contend AR 1")
    }

    @Test
    fun `a variant with no identifier is chosen by its page`() = runTest {
        val vm = vm()
        val page = FakeBikeWizard.candidate(null, "Contend", 2022)
        wizard.answers += { FakeBikeWizard.offered(query, page) }
        wizard.answers += { FakeBikeWizard.resolved(query) }
        vm.setSearchText("Giant Contend AR 1 2024")
        vm.search()

        vm.choose(page)

        assertThat(wizard.requests.last()).isEqualTo(ResolveRequest.Page(query, page.url))
    }

    @Test
    fun `variants stay on the screen while another search is made, and after it fails`() = runTest {
        val vm = vm()
        val one = FakeBikeWizard.candidate("e".repeat(64), "A")
        wizard.answers += { FakeBikeWizard.offered(query, one) }
        wizard.answers += { throw DataError.Offline(java.io.IOException()) }
        vm.setSearchText("Giant Contend AR 1 2024")
        vm.search()

        vm.choose(one)

        assertThat(vm.state.value.phase).isInstanceOf(SearchPhase.Failed::class.java)
        assertThat(vm.state.value.offer?.candidates).containsExactly(one)
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Search)
    }

    @Test
    fun `variants that the server has forgotten are dropped and the search starts again`() =
        runTest {
            val vm = vm()
            val one = FakeBikeWizard.candidate("e".repeat(64), "A")
            wizard.answers += { FakeBikeWizard.offered(query, one) }
            wizard.answers += {
                FakeBikeWizard.failed(query, ResolutionStatus.NotFound, "candidate_expired", true)
            }
            vm.setSearchText("Giant Contend AR 1 2024")
            vm.search()

            vm.choose(one)

            assertThat(vm.state.value.phase).isEqualTo(SearchPhase.Failed(SearchFailure.Expired))
            assertThat(vm.state.value.offer).isNull()
        }

    // --- what a search can come to
    // --------------------------------------------------------------------

    @Test
    fun `no result, an unknown brand and a page that is not a bike are told apart`() = runTest {
        val cases =
            listOf(
                FakeBikeWizard.failed(query, ResolutionStatus.NotFound) to SearchFailure.NotFound,
                FakeBikeWizard.failed(query, ResolutionStatus.UnsupportedBrand) to
                    SearchFailure.UnsupportedBrand,
                FakeBikeWizard.failed(
                    query,
                    ResolutionStatus.ParseError,
                    "spec_fields_not_found",
                ) to SearchFailure.Unreadable("spec_fields_not_found"),
                FakeBikeWizard.failed(query, ResolutionStatus.NotFound, "not_complete_bike") to
                    SearchFailure.Unreadable("not_complete_bike"),
                FakeBikeWizard.failed(query, ResolutionStatus.Unavailable, "timeout", true) to
                    SearchFailure.Unavailable("timeout", true),
            )
        for ((answer, failure) in cases) {
            val vm = vm()
            wizard.answers += { answer }
            vm.setSearchText("Giant Contend AR 1 2024")

            vm.search()

            assertThat(vm.state.value.phase).isEqualTo(SearchPhase.Failed(failure))
            assertThat(vm.state.value.step).isEqualTo(WizardStep.Search)
        }
    }

    @Test
    fun `no network is a failure of the request, and the line stays as typed`() = runTest {
        val vm = vm()
        wizard.answers += { throw DataError.Offline(java.io.IOException()) }
        vm.setSearchText("Giant Contend AR 1 2024")
        vm.setGarageName("Мой")

        vm.search()

        val failure = (vm.state.value.phase as SearchPhase.Failed).failure
        assertThat(failure).isInstanceOf(SearchFailure.Failed::class.java)
        assertThat(vm.state.value.searchText).isEqualTo("Giant Contend AR 1 2024")
        assertThat(vm.state.value.garageName).isEqualTo("Мой")
    }

    @Test
    fun `a search can be stopped and then its answer is never shown`() = runTest {
        val vm = vm()
        val gate = CompletableDeferred<Unit>()
        wizard.answers += {
            gate.await()
            FakeBikeWizard.resolved(query)
        }
        vm.setSearchText("Giant Contend AR 1 2024")
        vm.search()
        advanceUntilIdle()
        assertThat(vm.state.value.phase).isInstanceOf(SearchPhase.Resolving::class.java)

        vm.stopSearch()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(vm.state.value.phase).isEqualTo(SearchPhase.Idle)
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Search)
        assertThat(vm.state.value.found).isNull()
    }

    @Test
    fun `a second search while one is on its way is not started`() = runTest {
        val vm = vm()
        val gate = CompletableDeferred<Unit>()
        wizard.answers += {
            gate.await()
            FakeBikeWizard.resolved(query)
        }
        vm.setSearchText("Giant Contend AR 1 2024")
        vm.search()
        advanceUntilIdle()

        vm.search()
        advanceUntilIdle()

        assertThat(wizard.requests).hasSize(1)
        gate.complete(Unit)
    }

    // --- a page by its address
    // ---------------------------------------------------------------------------

    @Test
    fun `a pasted page is read with the line, and only an address is taken`() = runTest {
        val vm = vm()
        vm.setSearchText("Giant Contend AR 1 2024")
        vm.setPageUrl("javascript:alert(1)")

        vm.searchPage()

        assertThat(wizard.requests).isEmpty()
        vm.setPageUrl(" https://shop.example/giant-contend ")
        wizard.answers += { FakeBikeWizard.resolved(query) }
        vm.searchPage()
        assertThat(wizard.requests.single())
            .isEqualTo(ResolveRequest.Page(query, "https://shop.example/giant-contend"))
    }

    // --- a page that is not the bike
    // -----------------------------------------------------------------------

    @Test
    fun `a page of another model or year waits for the person, and nothing is taken before`() =
        runTest {
            val vm = vm()
            val other =
                FakeBikeWizard.build(
                    query,
                    name = "Giant Contend SL",
                    sourceYear = 2025,
                    identityMismatch = true,
                    yearMismatch = true,
                )

            vm.found(other)

            val question = vm.state.value.question as WizardQuestion.Identity
            assertThat(question.build.name).isEqualTo("Giant Contend SL")
            assertThat(question.asked).isEqualTo(2024)
            assertThat(vm.state.value.step).isEqualTo(WizardStep.Search)
            assertThat(vm.state.value.parts).isEmpty()
        }

    @Test
    fun `declining a page of another model keeps the search where it was`() = runTest {
        val vm = vm()
        vm.found(FakeBikeWizard.build(query, yearMismatch = true, sourceYear = 2025))

        vm.decline()

        assertThat(vm.state.value.question).isNull()
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Search)
        assertThat(vm.state.value.phase).isEqualTo(SearchPhase.Failed(SearchFailure.Declined))
        assertThat(vm.state.value.parts).isEmpty()
        assertThat(vm.state.value.found).isNull()
    }

    @Test
    fun `accepting it takes the build, and the agreement goes with the bike`() = runTest {
        val vm = vm()
        vm.found(FakeBikeWizard.build(query, yearMismatch = true, sourceYear = 2025))

        vm.confirm()

        assertThat(vm.state.value.step).isEqualTo(WizardStep.Build)
        assertThat(vm.state.value.found?.confirmed).isTrue()
        vm.next()
        vm.fillDetails()
        vm.save()
        advanceUntilIdle()
        assertThat(wizard.made.single().identityConfirmed).isTrue()
    }

    @Test
    fun `an agreement the person never gave is never sent`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()
        vm.fillDetails()

        vm.save()
        advanceUntilIdle()

        assertThat(wizard.made.single().identityConfirmed).isFalse()
    }

    // --- by hand
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `by hand there are no parts, no source, and the line fills what it can`() = runTest {
        val vm = vm()
        vm.setSearchText("Orbea Terra H30 2022")

        vm.continueByHand()

        val state = vm.state.value
        assertThat(state.step).isEqualTo(WizardStep.Build)
        assertThat(state.parts).isEmpty()
        assertThat(state.found).isNull()
        assertThat(state.form.brand).isEqualTo("Orbea")
        assertThat(state.form.model).isEqualTo("Terra H30")
        assertThat(state.form.year).isEqualTo("2022")
        assertThat(wizard.requests).isEmpty()
    }

    @Test
    fun `by hand with an empty line leaves the brand and the model to the details`() = runTest {
        val vm = vm()

        vm.continueByHand()

        assertThat(vm.state.value.query).isNull()
        assertThat(vm.state.value.form.brand).isEmpty()
    }

    // --- a new search and the parts that were edited
    // ------------------------------------------------------

    @Test
    fun `a new search replaces parts that were not touched without asking`() = runTest {
        val vm = vm()
        vm.found()
        vm.back()
        wizard.answers += {
            FakeBikeWizard.resolved(query, FakeBikeWizard.build(query, name = "Другой"))
        }

        vm.search()

        assertThat(vm.state.value.question).isNull()
        assertThat(vm.state.value.found?.build?.name).isEqualTo("Другой")
    }

    @Test
    fun `a new search never replaces edited parts unasked`() = runTest {
        val vm = vm()
        vm.found()
        val part = vm.state.value.parts.first()
        vm.setPartName(part.key, "Моя рама")
        vm.back()
        wizard.answers += {
            FakeBikeWizard.resolved(query, FakeBikeWizard.build(query, name = "Другой"))
        }

        vm.search()

        assertThat(vm.state.value.question).isInstanceOf(WizardQuestion.ReplaceParts::class.java)
        assertThat(wizard.requests).hasSize(1)
        assertThat(vm.state.value.parts.first().name).isEqualTo("Моя рама")

        vm.decline()
        assertThat(vm.state.value.parts.first().name).isEqualTo("Моя рама")
        assertThat(wizard.requests).hasSize(1)

        vm.search()
        vm.confirm()
        assertThat(wizard.requests).hasSize(2)
        assertThat(vm.state.value.found?.build?.name).isEqualTo("Другой")
        assertThat(vm.state.value.parts.first().name).isEqualTo("ALUXX aluminium")
    }

    // --- the parts
    // --------------------------------------------------------------------------------------------

    @Test
    fun `parts are edited, added and removed, and the site's category gives the group`() = runTest {
        val vm = vm()
        vm.found()
        vm.addPart()
        val added = vm.state.value.parts.last()
        vm.setPartCategory(added.key, "звонок")
        vm.setPartName(added.key, "Bell")
        vm.removePart(vm.state.value.parts.first().key)

        val parts = vm.state.value.parts
        assertThat(parts.map { it.name }).containsExactly("Giant Contact", "Bell").inOrder()
        // The site's group and section follow a category of the site's.
        assertThat(parts.last().groupId).isEqualTo("equipment")
        assertThat(parts.last().section).isEqualTo("accessories")
        assertThat(vm.state.value.partsEdited).isTrue()
    }

    @Test
    fun `a category of the person's own keeps the section and the group it had`() = runTest {
        val vm = vm()
        vm.found()
        val part = vm.state.value.parts.first()

        vm.setPartCategory(part.key, "Моя деталь")

        assertThat(vm.state.value.parts.first().groupId).isEqualTo(part.groupId)
        assertThat(vm.state.value.parts.first().section).isEqualTo(part.section)
    }

    @Test
    fun `an empty row is dropped and a wrong one stops the way, named`() = runTest {
        val vm = vm()
        vm.found()
        vm.addPart()
        vm.addPart()
        val (first, second) = vm.state.value.parts.takeLast(2)
        vm.setPartCategory(second.key, "Звонок")

        vm.next()

        // One had a category and no name: it is wrong, the empty one is gone.
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Build)
        assertThat(vm.state.value.parts.map { it.key }).doesNotContain(first.key)
        assertThat(vm.state.value.partProblems[second.key]).containsExactly(ComponentProblem.NoName)

        vm.setPartName(second.key, "Bell")
        vm.next()
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Details)
    }

    @Test
    fun `a price that is not a number stops the way`() = runTest {
        val vm = vm()
        vm.found()
        val part = vm.state.value.parts.first()
        vm.setPartPrice(part.key, "дорого")

        vm.next()

        assertThat(vm.state.value.step).isEqualTo(WizardStep.Build)
        assertThat(vm.state.value.partProblems[part.key])
            .containsExactly(ComponentProblem.PriceInvalid)
    }

    @Test
    fun `back goes one step at a time and tells when the wizard is to be left`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Details)

        assertThat(vm.back()).isTrue()
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Build)
        assertThat(vm.back()).isTrue()
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Search)
        assertThat(vm.back()).isFalse()
        // What was found is still there.
        assertThat(vm.state.value.found).isNotNull()
    }

    // --- details and saving
    // -------------------------------------------------------------------------------------

    @Test
    fun `what the page says is offered, and taken only into a field that is empty`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()

        vm.useSuggestedWeight()
        vm.useSuggestedColor()
        vm.setWeight("")
        vm.setColor("Красный")
        vm.useSuggestedColor()
        vm.useSuggestedWeight()

        assertThat(vm.state.value.form.color).isEqualTo("Красный")
        assertThat(vm.state.value.form.weight).isEqualTo("9.8")
    }

    @Test
    fun `a form that is not good says what is missing and sends nothing`() = runTest {
        val vm = vm()
        vm.found(
            FakeBikeWizard.build(BuildQuery("Giant", "Contend", null, null), sourceYear = null)
        )
        vm.next()
        vm.setYear("")

        vm.save()
        advanceUntilIdle()

        assertThat(wizard.made).isEmpty()
        assertThat(vm.state.value.problems)
            .containsAtLeast(BikeProblem.NoYear, BikeProblem.NoCategory)
    }

    @Test
    fun `the bike is made with the checked parts, the preview and no name of its own`() = runTest {
        val vm = vm()
        vm.found()
        vm.setPartName(vm.state.value.parts.first().key, "Моя рама")
        vm.next()
        vm.fillDetails()
        vm.setMileage("750")

        vm.save()
        advanceUntilIdle()

        val made = wizard.made.single()
        assertThat(made.previewId).isEqualTo("pv-1")
        assertThat(made.draft.name).isEmpty()
        assertThat(made.draft.brand).isEqualTo("Giant")
        assertThat(made.draft.year).isEqualTo(2024)
        assertThat(made.draft.mileageKm).isEqualTo(750)
        assertThat(made.draft.classification.category).isEqualTo("road_gravel")
        assertThat(made.draft.isPublic).isFalse()
        assertThat(made.parts.map { it.name })
            .containsExactly("Моя рама", "Giant Contact")
            .inOrder()
        assertThat(made.parts.map { it.groupId }).containsExactly("frame", "cockpit").inOrder()
        assertThat(vm.state.value.created).isNotNull()
    }

    @Test
    fun `a name for the garage is sent as it is`() = runTest {
        val vm = vm()
        vm.setGarageName("Мой шоссейник")
        vm.found()
        vm.next()
        vm.fillDetails()

        vm.save()
        advanceUntilIdle()

        assertThat(wizard.made.single().draft.name).isEqualTo("Мой шоссейник")
    }

    @Test
    fun `a bike by hand is made with no preview`() = runTest {
        val vm = vm()
        vm.setSearchText("Orbea Terra 2022")
        vm.continueByHand()
        vm.next()
        vm.fillDetails()

        vm.save()
        advanceUntilIdle()

        val made = wizard.made.single()
        assertThat(made.previewId).isNull()
        assertThat(made.parts).isEmpty()
        assertThat(made.draft.brand).isEqualTo("Orbea")
    }

    // --- a lost answer, a refusal
    // ----------------------------------------------------------------------------------

    @Test
    fun `the same request after a lost answer has the same key, a changed one has a new key`() =
        runTest {
            val vm = vm()
            vm.found()
            vm.next()
            vm.fillDetails()
            wizard.outcomes += { throw DataError.Offline(java.io.IOException()) }

            vm.save()
            advanceUntilIdle()
            assertThat(vm.state.value.problem).isNotNull()
            assertThat(vm.state.value.saving).isFalse()
            assertThat(vm.state.value.created).isNull()

            vm.save()
            advanceUntilIdle()

            assertThat(wizard.made).hasSize(2)
            assertThat(wizard.made[1].key).isEqualTo(wizard.made[0].key)
            assertThat(vm.state.value.created).isNotNull()
        }

    @Test
    fun `a change of the form makes a new request, so a new key`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()
        vm.fillDetails()
        wizard.outcomes += { throw DataError.Offline(java.io.IOException()) }
        vm.save()
        advanceUntilIdle()

        vm.setColor("Красный")
        vm.save()
        advanceUntilIdle()

        assertThat(wizard.made[1].key).isNotEqualTo(wizard.made[0].key)
    }

    @Test
    fun `nothing that was typed is lost by a failure`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()
        vm.fillDetails()
        vm.setDescription("Купил с рук")
        wizard.outcomes += { throw DataError.Offline(java.io.IOException()) }

        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.step).isEqualTo(WizardStep.Details)
        assertThat(state.form.description).isEqualTo("Купил с рук")
        assertThat(state.parts).hasSize(2)
        assertThat(state.found?.previewId).isEqualTo("pv-1")
    }

    @Test
    fun `a preview that is gone says so, and the bike can be saved without its source`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()
        vm.fillDetails()
        wizard.outcomes += {
            throw DataError.Rejected(
                409,
                "conflict",
                "Результат поиска устарел.",
                null,
                "previewId",
            )
        }

        vm.save()
        advanceUntilIdle()

        assertThat(vm.state.value.previewGone).isTrue()
        assertThat(vm.state.value.created).isNull()
        assertThat(vm.state.value.parts).hasSize(2)

        vm.saveWithoutSource()
        advanceUntilIdle()

        val made = wizard.made.last()
        assertThat(made.previewId).isNull()
        assertThat(made.parts).hasSize(2)
        assertThat(made.key).isNotEqualTo(wizard.made.first().key)
        assertThat(vm.state.value.created).isNotNull()
    }

    @Test
    fun `a difference the server asks about on saving is asked of the person, then saved`() =
        runTest {
            val vm = vm()
            vm.found()
            vm.next()
            vm.fillDetails()
            vm.setYear("2025")
            wizard.outcomes += {
                throw DataError.Rejected(409, "conflict", "Подтвердите.", null, "identityConfirmed")
            }

            vm.save()
            advanceUntilIdle()

            val question = vm.state.value.question as WizardQuestion.Identity
            assertThat(question.onSave).isTrue()
            assertThat(question.asked).isEqualTo(2025)
            assertThat(vm.state.value.created).isNull()

            vm.confirm()
            advanceUntilIdle()

            assertThat(wizard.made).hasSize(2)
            assertThat(wizard.made[1].identityConfirmed).isTrue()
            assertThat(vm.state.value.created).isNotNull()
        }

    @Test
    fun `a difference the person does not agree to saves nothing`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()
        vm.fillDetails()
        wizard.outcomes += {
            throw DataError.Rejected(409, "conflict", "Подтвердите.", null, "identityConfirmed")
        }
        vm.save()
        advanceUntilIdle()

        vm.decline()

        assertThat(vm.state.value.question).isNull()
        assertThat(vm.state.value.created).isNull()
        assertThat(wizard.made).hasSize(1)
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Details)
    }

    @Test
    fun `publishing without a confirmed address says so and keeps everything`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()
        vm.fillDetails()
        vm.setPublic(true)
        wizard.outcomes += {
            throw DataError.Rejected(403, "email_verification_required", "Нужна почта")
        }

        vm.save()
        advanceUntilIdle()

        assertThat(vm.state.value.problem)
            .isEqualTo(ru.colabike.app.ui.UiText.Res(R.string.bike_needs_email))
        assertThat(vm.state.value.form.isPublic).isTrue()
        assertThat(vm.state.value.created).isNull()
    }

    @Test
    fun `saving twice at once makes one bike`() = runTest {
        val vm = vm()
        vm.found()
        vm.next()
        vm.fillDetails()
        val gate = CompletableDeferred<Unit>()
        wizard.outcomes += { gate.await() }

        vm.save()
        vm.save()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(wizard.made).hasSize(1)
    }

    // --- retrying and the person's own parts
    // --------------------------------------------------------------------

    @Test
    fun `a failed request is asked again as it was, whatever it was`() = runTest {
        val vm = vm()
        wizard.answers += {
            FakeBikeWizard.failed(query, ResolutionStatus.Unavailable, "timeout", retryable = true)
        }
        wizard.answers += { FakeBikeWizard.resolved(query) }
        vm.setSearchText("Giant Contend AR 1 2024")
        vm.setPageUrl("https://shop.example/giant")
        vm.searchPage()
        advanceUntilIdle()

        vm.retry()
        advanceUntilIdle()

        assertThat(wizard.requests).hasSize(2)
        assertThat(wizard.requests[1]).isEqualTo(wizard.requests[0])
        assertThat(wizard.requests[1]).isInstanceOf(ResolveRequest.Page::class.java)
        assertThat(vm.state.value.step).isEqualTo(WizardStep.Build)
    }

    @Test
    fun `nothing is asked again when nothing was asked`() = runTest {
        val vm = vm()

        vm.retry()
        advanceUntilIdle()

        assertThat(wizard.requests).isEmpty()
    }

    @Test
    fun `parts the person adds are told from the ones the specification drafted`() = runTest {
        val vm = vm()
        vm.found()
        advanceUntilIdle()
        assertThat(vm.state.value.parts.none { it.added }).isTrue()

        vm.addPart()

        assertThat(vm.state.value.parts.map { it.added }).containsExactly(false, false, true)
    }

    @Test
    fun `a drafted part with no category of its own is the person's to name`() = runTest {
        val vm = vm()
        vm.found(FakeBikeWizard.build(query, parts = listOf(FakeBikeWizard.part("", "Что-то"))))
        advanceUntilIdle()

        assertThat(vm.state.value.parts.single().added).isTrue()
    }

    // --- leaving
    // ---------------------------------------------------------------------------------------------------

    @Test
    fun `leaving with something typed asks first, leaving an empty wizard does not`() = runTest {
        val vm = vm()
        assertThat(vm.state.value.dirty).isFalse()

        vm.setSearchText("Giant")

        assertThat(vm.state.value.dirty).isTrue()
        vm.askLeave()
        assertThat(vm.state.value.question).isEqualTo(WizardQuestion.Discard)
        vm.decline()
        assertThat(vm.state.value.question).isNull()
        assertThat(vm.state.value.searchText).isEqualTo("Giant")
    }
}
