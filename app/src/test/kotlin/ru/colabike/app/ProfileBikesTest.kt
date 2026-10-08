package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.profile.ProfileBikesModel
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.PeopleRepository

class ProfileBikesTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `only public profile bikes are requested with a two item limit`() = runTest {
        val calls = mutableListOf<Triple<String, String?, Int>>()
        val people =
            object : PeopleRepository by FakePeople() {
                override suspend fun bikesOf(
                    ref: String,
                    cursor: String?,
                    limit: Int,
                ): Page<BikeSummary> {
                    calls += Triple(ref, cursor, limit)
                    return Page(bikes(1, 3), "next")
                }
            }
        val model = ProfileBikesModel(people, FakeBikes(), "viewer-id")
        assertThat(calls).containsExactly(Triple("viewer-id", null, 2))
        assertThat(model.state.value.bikes).hasSize(2)
        assertThat(model.state.value.loading).isFalse()
    }

    @Test
    fun `failed preview does not erase loaded bikes and can be retried`() = runTest {
        val people = FakePeople()
        val model = ProfileBikesModel(people, FakeBikes(), "viewer-id")
        val before = model.state.value.bikes
        people.nextError = DataError.NotFound()
        model.refresh()
        assertThat(model.state.value.error).isNotNull()
        assertThat(model.state.value.bikes).isEqualTo(before)
        model.refresh()
        assertThat(model.state.value.error).isNull()
        assertThat(model.state.value.loading).isFalse()
    }

    @Test
    fun `a cancelled old refresh cannot replace the newest profile content`() = runTest {
        var call = 0
        val old = CompletableDeferred<Page<BikeSummary>>()
        val latest = bikes(10, 1)
        val people =
            object : PeopleRepository by FakePeople() {
                override suspend fun bikesOf(
                    ref: String,
                    cursor: String?,
                    limit: Int,
                ): Page<BikeSummary> {
                    call++
                    return if (call == 1) withContext(NonCancellable) { old.await() }
                    else Page(latest, null)
                }
            }
        val model = ProfileBikesModel(people, FakeBikes(), "viewer-id")
        model.refresh()
        old.complete(Page(bikes(0, 2), null))
        assertThat(model.state.value.bikes).isEqualTo(latest)
    }
}
