package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.rides.RidePreview
import ru.colabike.app.rides.RidePreviews
import ru.colabike.core.model.DataError
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RidesRepository

class RidePreviewsTest {
    @get:Rule val main = MainDispatcherRule()
    private val fake = FakeRides()
    private val id = RideId("ride-0")

    @Test
    fun `same visible ride shares request and remains until last subscriber leaves`() = runTest {
        val gate = CompletableDeferred<RideDetail>()
        var calls = 0
        val model =
            RidePreviews(
                object : RidesRepository by fake {
                    override suspend fun ride(id: RideId): RideDetail {
                        calls++
                        return gate.await()
                    }
                }
            )
        model.show(id)
        model.show(id)
        assertThat(calls).isEqualTo(1)
        model.hide(id)
        gate.complete(fake.details.getValue(id.value))
        assertThat(model.state.value[id]).isInstanceOf(RidePreview.Ready::class.java)
        model.hide(id)
        assertThat(model.state.value).isEmpty()
    }

    @Test
    fun `scroll cancels outstanding work and no more than two requests run`() = runTest {
        val started = mutableListOf<RideId>()
        val cancelled = mutableListOf<RideId>()
        val model =
            RidePreviews(
                object : RidesRepository by fake {
                    override suspend fun ride(id: RideId): RideDetail {
                        started += id
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled += id
                        }
                    }
                }
            )
        val ids = (0..3).map { RideId("ride-$it") }
        ids.forEach(model::show)
        assertThat(started).containsExactly(ids[0], ids[1])
        model.hide(ids[0])
        assertThat(cancelled).containsExactly(ids[0])
        assertThat(started).containsExactly(ids[0], ids[1], ids[2])
        model.clear()
        assertThat(model.state.value).isEmpty()
        assertThat(cancelled).containsExactly(ids[0], ids[1], ids[2])
        assertThat(started).doesNotContain(ids[3])
    }

    @Test
    fun `cache expires and refresh rereads visible route preserving subscriptions`() = runTest {
        var now = 0L
        val model = RidePreviews(fake) { now }
        model.show(id)
        model.hide(id)
        model.show(id)
        assertThat(fake.rideCalls).isEqualTo(1)
        model.hide(id)
        now = 60_001
        model.show(id)
        assertThat(fake.rideCalls).isEqualTo(2)
        model.show(id)
        model.refresh()
        assertThat(fake.rideCalls).isEqualTo(3)
        model.hide(id)
        assertThat(model.state.value).containsKey(id)
        model.hide(id)
        assertThat(model.state.value).isEmpty()
    }

    @Test
    fun `failed preview retries on revisit and session clear discards cached geometry`() = runTest {
        val model = RidePreviews(fake)
        fake.nextError = DataError.NotFound()
        model.show(id)
        assertThat(model.state.value[id]).isEqualTo(RidePreview.Unavailable)
        model.hide(id)
        model.show(id)
        assertThat(model.state.value[id]).isInstanceOf(RidePreview.Ready::class.java)
        model.clear()
        model.show(id)
        assertThat(fake.rideCalls).isEqualTo(3)
    }

    @Test
    fun `cache holds at most twenty routes`() = runTest {
        var calls = 0
        val model =
            RidePreviews(
                object : RidesRepository by fake {
                    override suspend fun ride(id: RideId): RideDetail {
                        calls++
                        return fake.details.getValue("ride-0")
                    }
                }
            )
        (0..20).forEach {
            val key = RideId("ride-$it")
            model.show(key)
            model.hide(key)
        }
        model.show(id)
        assertThat(calls).isEqualTo(22)
        assertThat(model.state.value).hasSize(1)
    }
}
