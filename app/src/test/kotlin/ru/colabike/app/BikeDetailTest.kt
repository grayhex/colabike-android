package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.bikes.BikeDetailUiState
import ru.colabike.app.bikes.BikeDetailViewModel
import ru.colabike.app.bikes.BikeLabels
import ru.colabike.app.bikes.fullscreenUrl
import ru.colabike.app.bikes.orderComponents
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.model.BikeClassification
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.LikeState
import ru.colabike.core.model.Photo

@OptIn(ExperimentalCoroutinesApi::class)
class BikeLikeTest {
    @get:Rule val main = MainDispatcherRule()

    private val id = BikeId("b1")

    private fun loaded(vm: BikeDetailViewModel) = vm.state.value as BikeDetailUiState.Loaded

    /** `bikes(0, 3)`: b0 is liked, b1 is not, b2 is liked (`liked = it % 2 == 0`). */
    @Test
    fun `the heart turns at once and the server's count replaces the guess`() = runTest {
        val repo = FakeBikes()
        val vm = BikeDetailViewModel(repo, id)
        val before = loaded(vm).bike.summary
        assertThat(before.liked).isFalse()

        vm.toggleLike()

        val after = loaded(vm).bike.summary
        assertThat(after.liked).isTrue()
        assertThat(repo.likes).containsExactly(id to true)
        assertThat(loaded(vm).liking).isFalse()
        assertThat(after.likes).isEqualTo(before.likes + 1)
    }

    @Test
    fun `while the server thinks the heart is already turned and taps do nothing`() = runTest {
        val repo = FakeBikes()
        val gate = CompletableDeferred<Unit>()
        val slow =
            object : ru.colabike.core.model.BikesRepository by repo {
                override suspend fun setLiked(id: BikeId, liked: Boolean): LikeState {
                    gate.await()
                    return repo.setLiked(id, liked)
                }
            }
        val vm = BikeDetailViewModel(slow, id)

        vm.toggleLike()
        assertThat(loaded(vm).liking).isTrue()
        assertThat(loaded(vm).bike.summary.liked).isTrue()
        vm.toggleLike() // a second tap while the first is on its way
        gate.complete(Unit)
        runCurrent()

        assertThat(repo.likes).containsExactly(id to true)
        assertThat(loaded(vm).bike.summary.liked).isTrue()
    }

    @Test
    fun `a refusal puts the heart back and says why`() = runTest {
        val repo = FakeBikes()
        val vm = BikeDetailViewModel(repo, id)
        val before = loaded(vm).bike.summary
        repo.likeError = DataError.Offline(java.io.IOException())

        vm.toggleLike()

        val after = loaded(vm)
        assertThat(after.bike.summary.liked).isEqualTo(before.liked)
        assertThat(after.bike.summary.likes).isEqualTo(before.likes)
        assertThat(after.liking).isFalse()
        assertThat(after.likeError).isEqualTo(UiText.Res(R.string.error_offline))
        // The next try starts clean.
        vm.toggleLike()
        assertThat(loaded(vm).likeError).isNull()
    }

    @Test
    fun `one's own and private bikes cannot be liked`() = runTest {
        val own = PreviewData.bike.copy(id = BikeId("own"), isOwner = true)
        val private = PreviewData.bike.copy(id = BikeId("private"), isPublic = false)
        val repo = FakeBikes(mapOf(null to ru.colabike.core.model.Page(listOf(own, private), null)))

        BikeDetailViewModel(repo, own.id).toggleLike()
        BikeDetailViewModel(repo, private.id).toggleLike()

        assertThat(repo.likes).isEmpty()
    }

    @Test
    fun `a like given on the list shows on the open page`() = runTest {
        val repo = FakeBikes()
        val vm = BikeDetailViewModel(repo, id)

        repo.setLiked(id, true)
        runCurrent()

        assertThat(loaded(vm).bike.summary.liked).isTrue()
        assertThat(repo.detailCalls).isEqualTo(1) // not loaded again
    }

    @Test
    fun `a like of another bike is not this page's business`() = runTest {
        val repo = FakeBikes()
        val vm = BikeDetailViewModel(repo, id)
        val before = loaded(vm).bike.summary

        repo.setLiked(BikeId("b0"), false)
        runCurrent()

        assertThat(loaded(vm).bike.summary).isEqualTo(before)
    }
}

class BikePresentationTest {
    private fun component(
        id: String,
        section: String = "build",
        category: String = "Рама",
        groupId: String = "",
        sortOrder: Int = 0,
    ) = BikeComponent(id, section, category, "name-$id", "", null, groupId, sortOrder, null)

    private fun ids(sections: List<ru.colabike.app.bikes.ComponentSection>) = sections.map {
        it.section to it.components.map { c -> c.id }
    }

    @Test
    fun `sections come build, accessories, then anything newer`() {
        val sections =
            orderComponents(
                listOf(
                    component("a", section = "other"),
                    component("b", section = "accessories"),
                    component("c", section = "build"),
                    component("d", section = "luggage-from-the-future"),
                ),
                emptyList(),
            )

        assertThat(sections.map { it.section })
            .containsExactly("build", "accessories", "other")
            .inOrder()
        // A section this version does not know is shown with the "other" ones, not dropped.
        assertThat(sections.last().components.map { it.id }).containsExactly("a", "d")
    }

    @Test
    fun `the owner's group order comes first and a group stays together`() {
        val sections =
            orderComponents(
                listOf(
                    component("f1", category = "Рама", groupId = "frame", sortOrder = 0),
                    component(
                        "d1",
                        category = "Трансмиссия",
                        groupId = "drivetrain",
                        sortOrder = 1,
                    ),
                    component("w1", category = "Колёса", groupId = "wheels", sortOrder = 2),
                    component(
                        "d2",
                        category = "Трансмиссия",
                        groupId = "drivetrain",
                        sortOrder = 3,
                    ),
                ),
                groupOrder = listOf("wheels", "drivetrain"),
            )

        // wheels, drivetrain (both its components), then frame, which the owner did not place.
        assertThat(ids(sections)).containsExactly("build" to listOf("w1", "d1", "d2", "f1"))
    }

    @Test
    fun `groups the owner did not order follow by their own sort order, a category is a group`() {
        val sections =
            orderComponents(
                listOf(
                    component("b", category = "Тормоза", sortOrder = 5),
                    component("a", category = "Руль", sortOrder = 1),
                    component("c", category = "Тормоза", sortOrder = 6),
                ),
                groupOrder = emptyList(),
            )

        assertThat(ids(sections)).containsExactly("build" to listOf("a", "b", "c"))
    }

    @Test
    fun `the badges say what the bike is, three at most, unknown keys left out`() {
        fun badges(c: BikeClassification) = BikeLabels.badges(c)
        val mtb =
            BikeClassification(
                "mtb",
                "trail",
                "full_suspension",
                "standard",
                electric = false,
                fatbike = false,
            )

        assertThat(badges(mtb)).containsExactly("MTB · Trail", "Full Suspension").inOrder()
        assertThat(badges(mtb.copy(construction = "folding", electric = true, fatbike = true)))
            .containsExactly("MTB · Trail", "Full Suspension", "Folding")
            .inOrder()
        assertThat(
                badges(
                    BikeClassification(
                        "urban_touring",
                        null,
                        null,
                        null,
                        electric = true,
                        fatbike = false,
                    )
                )
            )
            .containsExactly("Город / туризм", "E-bike")
            .inOrder()
        // The site may add values before the app learns them.
        assertThat(
                badges(
                    BikeClassification("hoverboard", "wobbly", "airy", "inflatable", false, false)
                )
            )
            .isEmpty()
    }

    @Test
    fun `the full-screen address asks the server for a larger picture`() {
        val photo = Photo("p", "https://colabike.ru/api/photos/0b7e6a52?width=640")

        assertThat(photo.fullscreenUrl())
            .isEqualTo("https://colabike.ru/api/photos/0b7e6a52?width=1280")
        // No width yet: one is added; not an address: left as it is.
        assertThat(Photo("p", "https://colabike.ru/api/photos/1").fullscreenUrl())
            .isEqualTo("https://colabike.ru/api/photos/1?width=1280")
        assertThat(Photo("p", "not a url").fullscreenUrl()).isEqualTo("not a url")
    }
}
