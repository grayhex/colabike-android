package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.nearby.CoarseResult
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NearbyChange
import ru.colabike.core.model.NearbyOffer
import ru.colabike.core.model.NearbyOffers
import ru.colabike.core.model.NearbyOffersState
import ru.colabike.core.model.NearbyReason
import ru.colabike.core.model.NearbySource

/** Profile → Notifications → Rides near me as a person uses it, on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class NearbyFlowTest {
    @get:Rule val compose = createComposeRule()

    private val nearby = FakeNearby()
    private val location = FakeCoarseLocation()

    private fun start(
        nearby: FakeNearby = this.nearby,
        location: FakeCoarseLocation = this.location,
        rides: FakeRides = FakeRides(),
    ) {
        compose.setContent {
            ColaBikeTheme {
                ColaBikeApp(
                    FakeDependencies(nearby = nearby, coarseLocation = location, rides = rides)
                )
            }
        }
        compose.waitForIdle()
        compose.section("Профиль").performClick()
        compose.onNodeWithText("Что, когда и от кого присылать").performScrollTo().performClick()
        compose.onNodeWithTag("notif-settings:nearby").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun tag(name: String) = compose.onNodeWithTag("nearby:$name")

    private fun scrolled(name: String) = tag(name).performScrollTo()

    @Test
    fun `the notification settings lead to rides near me, which say what it is and is not`() {
        start()

        compose.onNodeWithTag("nearby").assertIsDisplayed()
        tag("area-none").assertIsDisplayed()
        compose
            .onNodeWithText("Включение поиска не включает push", substring = true)
            .assertDoesNotExist()
        scrolled("privacy").performClick()
        compose
            .onNodeWithText("Включение поиска не включает push", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        compose
            .onNodeWithText("Включение поиска не включает push", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `back returns to the notification settings`() {
        start()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag("notif-settings").assertIsDisplayed()
    }

    @Test
    fun `a failed load says why and loading again shows the screen`() {
        nearby.loadError = DataError.Server(503, null)
        start()
        compose.onNodeWithText("Повторить").assertIsDisplayed()

        nearby.loadError = null
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("nearby").assertIsDisplayed()
    }

    @Test
    fun `the switch is on after the server took it, and says so`() {
        start()
        tag("switch").assertIsOff()

        tag("switch").performClick()
        compose.waitForIdle()

        assertThat(nearby.changes).containsExactly(NearbyChange(enabled = true))
        tag("switch").assertIsOn()
        tag("notice").assertTextContains("Сохранено", substring = true)
    }

    @Test
    fun `an operator who has it off lets the person turn it off and not on`() {
        nearby.current = activeNearbySettings.copy(available = false)
        start()

        tag("switch").assertIsOn().performClick()
        compose.waitForIdle()

        assertThat(nearby.changes).containsExactly(NearbyChange(enabled = false))
        tag("switch").assertIsNotEnabled()
    }

    @Test
    fun `the phone is asked only after the explanation, and the place is confirmed before it is sent`() {
        start()

        scrolled("locate").performClick()
        compose.onNodeWithText("Android один раз спросит", substring = true).assertIsDisplayed()
        assertThat(location.reads).isEqualTo(0)
        tag("locate-allow").performClick()
        compose.waitForIdle()

        assertThat(location.reads).isEqualTo(1)
        tag("draft").assertIsDisplayed()
        assertThat(nearby.confirmed).isEmpty()
        tag("radius:10").assertIsSelected()

        scrolled("radius:20").performClick()
        scrolled("confirm").performClick()
        compose.waitForIdle()

        assertThat(nearby.confirmed).containsExactly(Triple(37.625, 55.755, 20_000))
        scrolled("area").assertTextContains("Радиус 20 км", substring = true)
        scrolled("area-source").assertTextContains("Подтверждён телефоном", substring = true)
        compose.onNodeWithTag("nearby:draft").assertDoesNotExist()
    }

    @Test
    fun `a draft can be thrown away and nothing is sent`() {
        start()
        scrolled("locate").performClick()
        tag("locate-allow").performClick()
        compose.waitForIdle()

        scrolled("discard").performClick()

        compose.onNodeWithTag("nearby:draft").assertDoesNotExist()
        assertThat(nearby.confirmed).isEmpty()
    }

    @Test
    fun `an area from the site is replaced only after a yes of its own`() {
        nearby.current = activeNearbySettings
        start()
        scrolled("locate").performClick()
        tag("locate-allow").performClick()
        compose.waitForIdle()

        scrolled("confirm").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Заменить район с сайта?").assertIsDisplayed()
        assertThat(nearby.confirmed).isEmpty()

        tag("replace-yes").performClick()
        compose.waitForIdle()

        assertThat(nearby.replaced).isTrue()
        assertThat(nearby.current.source).isEqualTo(NearbySource.Device)
    }

    @Test
    fun `a switched off location says how to go on`() {
        location.result = CoarseResult.ServiceOff
        start()

        scrolled("locate").performClick()
        tag("locate-allow").performClick()
        compose.waitForIdle()

        tag("problem").assertTextContains("Геолокация на телефоне выключена", substring = true)
        compose.onNodeWithTag("nearby:draft").assertDoesNotExist()
    }

    @Test
    fun `a phone area whose term passed says so`() {
        nearby.current = activeNearbySettings.copy(source = NearbySource.Device, expired = true)
        start()

        scrolled("expired").assertIsDisplayed()
    }

    @Test
    fun `removing the area leaves the switch and the kinds`() {
        nearby.current = activeNearbySettings
        start()

        scrolled("remove-area").performClick()
        compose.waitForIdle()

        assertThat(nearby.removed).isEqualTo(1)
        scrolled("area-none").assertIsDisplayed()
        scrolled("switch").assertIsOn()
        scrolled("purposes:leisure").assertIsSelected()
    }

    @Test
    fun `a kind is chosen, shown after the server took it, and the horizon moves`() {
        start()

        scrolled("paces:relaxed").performClick()
        compose.waitForIdle()
        scrolled("horizon:14").performClick()
        compose.waitForIdle()

        assertThat(nearby.changes)
            .containsExactly(
                NearbyChange(paces = setOf("relaxed")),
                NearbyChange(horizonDays = 14),
            )
            .inOrder()
        scrolled("paces:relaxed").assertIsSelected()
        scrolled("horizon:14").assertIsSelected()
    }

    @Test
    fun `forgetting asks first and then removes everything`() {
        nearby.current = activeNearbySettings
        start()

        scrolled("forget").performClick()
        compose.onNodeWithText("Забыть всё?").assertIsDisplayed()
        assertThat(nearby.forgotten).isEqualTo(0)
        tag("forget-yes").performClick()
        compose.waitForIdle()

        assertThat(nearby.forgotten).isEqualTo(1)
        scrolled("switch").assertIsOff()
        scrolled("area-none").assertIsDisplayed()
    }

    // --- the offers ----------------------------------------------------------------------------

    @Test
    fun `the offers say why each ride is there, and a tap opens the ride`() {
        nearby.current = activeNearbySettings
        nearby.offers =
            NearbyOffers(
                NearbyOffersState.Ready,
                listOf(
                    NearbyOffer(
                        PreviewData.plannedRide,
                        setOf(NearbyReason.Nearby, NearbyReason.Intent),
                    )
                ),
            )
        start()

        scrolled("offers").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("nearby-offers:list").assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Воскресный выезд за город", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("В вашем районе", substring = true).assertIsDisplayed()

        compose.onNodeWithTag("nearby-offer:r2").performClick()
        compose.waitForIdle()

        assertThat(nearby.offerCalls).isEqualTo(1)
    }

    @Test
    fun `an empty list says why and leads to the setting that mends it`() {
        nearby.offers = NearbyOffers(NearbyOffersState.NoArea, emptyList())
        start()

        scrolled("offers").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Район не выбран").assertIsDisplayed()
        compose.onNodeWithText("Настроить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("nearby").assertIsDisplayed()
    }

    @Test
    fun `a failed offers load can be tried again`() {
        nearby.offersError = DataError.Server(503, null)
        start()

        scrolled("offers").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Повторить").assertIsDisplayed()

        nearby.offersError = null
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("nearby-offers:empty").assertIsDisplayed()
    }
}
