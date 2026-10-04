package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.components.ComponentUiState
import ru.colabike.app.components.ComponentViewModel
import ru.colabike.app.components.ComponentsViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.ComponentQuery
import ru.colabike.core.model.ComponentSort
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page

/** The component catalog and a model's page. */
@OptIn(ExperimentalCoroutinesApi::class)
class ComponentsTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeComponents()

    private fun catalog() = ComponentsViewModel(repository, debounceMs = 400)

    @Test
    fun `it opens on the whole catalog, newest first, and asks for the filters`() = runTest {
        val viewModel = catalog()

        val state = viewModel.state.value

        assertThat(repository.queries).containsExactly(ComponentQuery() to null)
        assertThat(state.page.items.map { it.id.value }).containsExactly("c1", "c2", "c3", "c4")
        assertThat(state.filters?.categories).containsExactly("Трансмиссия", "Тормоза").inOrder()
        assertThat(repository.filterCalls).isEqualTo(1)
    }

    @Test
    fun `typing waits for a pause and then asks once, with the whole text`() = runTest {
        val viewModel = catalog()
        repository.queries.clear()

        viewModel.onSearchText("к")
        advanceTimeBy(200)
        viewModel.onSearchText("кас")
        advanceTimeBy(200)
        viewModel.onSearchText("кассета ")
        // The field follows every key at once, the list waits.
        assertThat(viewModel.state.value.typed).isEqualTo("кассета ")
        assertThat(repository.queries).isEmpty()

        advanceTimeBy(401)
        runCurrent()

        assertThat(repository.queries).containsExactly(ComponentQuery(text = "кассета") to null)
        assertThat(viewModel.state.value.query.text).isEqualTo("кассета")
    }

    @Test
    fun `a filter starts the list over with no cursor, and choosing it again lifts it`() = runTest {
        repository.pages =
            mapOf(
                null to Page(componentModels(1, 2), "next"),
                "next" to Page(componentModels(3, 2), null),
            )
        val viewModel = catalog()
        viewModel.loadMore()
        assertThat(viewModel.state.value.page.items).hasSize(4)
        repository.queries.clear()

        viewModel.selectCategory("Тормоза")
        assertThat(repository.queries.last())
            .isEqualTo(ComponentQuery(category = "Тормоза") to null)
        assertThat(viewModel.state.value.page.items).hasSize(2)

        viewModel.selectBrand("SRAM")
        assertThat(repository.queries.last().first)
            .isEqualTo(ComponentQuery(category = "Тормоза", brand = "SRAM"))

        viewModel.selectCategory("Тормоза")
        assertThat(repository.queries.last().first).isEqualTo(ComponentQuery(brand = "SRAM"))
    }

    @Test
    fun `a new order is a new list, because a cursor of one order is refused in the other`() =
        runTest {
            repository.pages = mapOf(null to Page(componentModels(1, 2), "next"))
            val viewModel = catalog()

            viewModel.selectSort(ComponentSort.Popular)

            assertThat(repository.queries.last())
                .isEqualTo(ComponentQuery(sort = ComponentSort.Popular) to null)
            assertThat(viewModel.state.value.page.nextCursor).isEqualTo("next")
        }

    @Test
    fun `the next page is appended, and a model on two pages shows once`() = runTest {
        repository.pages =
            mapOf(
                null to Page(componentModels(1, 3), "next"),
                // The popular order moves between pages: c3 comes again.
                "next" to Page(componentModels(3, 2), null),
            )
        val viewModel = catalog()

        viewModel.loadMore()

        assertThat(viewModel.state.value.page.items.map { it.id.value })
            .containsExactly("c1", "c2", "c3", "c4")
            .inOrder()
        assertThat(viewModel.state.value.page.nextCursor).isNull()
    }

    @Test
    fun `clearing the filters goes back to the whole catalog`() = runTest {
        val viewModel = catalog()
        viewModel.selectCategory("Тормоза")
        viewModel.selectSort(ComponentSort.Popular)
        viewModel.onSearchText("sram")

        viewModel.clearFilters()

        assertThat(repository.queries.last()).isEqualTo(ComponentQuery() to null)
        assertThat(viewModel.state.value.typed).isEmpty()
    }

    @Test
    fun `a failed first page is the screen's error and a retry asks again`() = runTest {
        repository.nextError = DataError.Offline(java.io.IOException("x"))
        val viewModel = catalog()
        assertThat(viewModel.state.value.page.error).isEqualTo(UiText.Res(R.string.error_offline))

        viewModel.retry()

        assertThat(viewModel.state.value.page.error).isNull()
        assertThat(viewModel.state.value.page.items).isNotEmpty()
    }

    @Test
    fun `without the filters the list still works, and they come back with the next refresh`() =
        runTest {
            repository.filtersError = DataError.Server(500, null)
            val viewModel = catalog()
            assertThat(viewModel.state.value.filters).isNull()
            assertThat(viewModel.state.value.page.items).isNotEmpty()

            viewModel.refresh()

            assertThat(viewModel.state.value.filters).isNotNull()
        }

    // --- a model's page -------------------------------------------------------------------

    private fun page(id: String) = ComponentViewModel(repository, ComponentId(id))

    @Test
    fun `the page has the model first and its photos after`() = runTest {
        val viewModel = page("c1")

        val state = viewModel.state.value as ComponentUiState.Loaded

        assertThat(state.model.name).isEqualTo("Кассета 1")
        assertThat(state.photos).hasSize(2)
        assertThat(repository.modelCalls).containsExactly("c1")
        assertThat(repository.photoCalls).containsExactly("c1")
    }

    @Test
    fun `a merged model shows the canonical one, and its photos are asked by the canonical id`() =
        runTest {
            repository.aliases = mapOf("old" to "c1")

            val state = page("old").state.value as ComponentUiState.Loaded

            assertThat(state.model.id).isEqualTo(ComponentId("c1"))
            assertThat(repository.photoCalls).containsExactly("c1")
        }

    @Test
    fun `an archived model is read and says so`() = runTest {
        repository.models =
            mapOf("a1" to componentModel(9).copy(id = ComponentId("a1"), archived = true))

        val state = page("a1").state.value as ComponentUiState.Loaded

        assertThat(state.model.archived).isTrue()
    }

    @Test
    fun `photos that cannot be read leave the page as it is`() = runTest {
        repository.photosError = DataError.Server(500, "req")

        val state = page("c1").state.value as ComponentUiState.Loaded

        assertThat(state.model.name).isEqualTo("Кассета 1")
        assertThat(state.photos).isEmpty()
    }

    @Test
    fun `a model that is not there is not found, other failures are retried`() = runTest {
        val missing = page("nope").state.value as ComponentUiState.Failed
        assertThat(missing.notFound).isTrue()

        repository.nextError = DataError.Offline(java.io.IOException("x"))
        val viewModel = page("c1")
        val failed = viewModel.state.value as ComponentUiState.Failed
        assertThat(failed.notFound).isFalse()
        assertThat(failed.message).isEqualTo(UiText.Res(R.string.error_offline))

        viewModel.load()

        assertThat(viewModel.state.value).isInstanceOf(ComponentUiState.Loaded::class.java)
    }
}
