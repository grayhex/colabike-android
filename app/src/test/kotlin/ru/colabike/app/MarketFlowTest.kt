package ru.colabike.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import ru.colabike.app.links.LinkOpener
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ListingBikeLink
import ru.colabike.core.model.ListingCatalogLink
import ru.colabike.core.model.ListingCategory
import ru.colabike.core.model.ListingSort
import ru.colabike.core.model.ListingStatus
import ru.colabike.core.model.MarketQuery
import ru.colabike.core.model.Page

/** From the Bikes section to the market and a listing, and to the saved ones from the profile. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class MarketFlowTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun start(
        market: FakeMarket = FakeMarket(),
        signedIn: Boolean = true,
        comments: FakeComments = FakeComments(),
    ): FakeDependencies {
        val dependencies =
            FakeDependencies(
                bikes = FakeBikes(mapOf(null to Page(bikes(0, 3), null))),
                market = market,
                comments = comments,
                auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
                settings = FakeSettings(guest = !signedIn),
            )
        compose.setContent {
            ColaBikeTheme {
                CompositionLocalProvider(LocalLinkOpener provides LinkOpener { opened += it }) {
                    ColaBikeApp(dependencies)
                }
            }
        }
        compose.waitForIdle()
        return dependencies
    }

    private fun openMarket() {
        compose.onNodeWithContentDescription("Объявления").performClick()
        compose.waitForIdle()
    }

    private fun openListing(id: String = "l1") {
        openMarket()
        compose.onNodeWithTag("listing:$id").performClick()
        compose.waitForIdle()
    }

    private fun settle() {
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.waitForIdle()
    }

    private fun clipboardText(): String? {
        val manager =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return manager.primaryClip?.getItemAt(0)?.text?.toString()
    }

    // --- the list ----------------------------------------------------------------------------

    @Test
    fun `the bikes screen opens the market, which lists the listings with price and place`() {
        start()

        openMarket()

        compose.onNodeWithText("Объявления").assertIsDisplayed()
        compose.onNodeWithTag("listing:l1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Втулка 1", substring = true).assertIsDisplayed()
    }

    @Test
    fun `search, the order and the filters go to the server, and nothing found offers a reset`() {
        val market = FakeMarket()
        start(market)
        openMarket()

        compose.onNodeWithTag("market:search").performTextInput("втулка")
        settle()
        assertThat(market.queries.last().first).isEqualTo(MarketQuery(text = "втулка"))

        compose.onNodeWithText("Дешевле").performClick()
        settle()
        assertThat(market.queries.last().first)
            .isEqualTo(MarketQuery(text = "втулка", sort = ListingSort.PriceAsc))

        market.pages = mapOf(null to Page(emptyList(), null))
        compose.onNodeWithTag("market:filters").performClick()
        compose.onNodeWithText("Компоненты").performClick()
        settle()
        assertThat(market.queries.last().first)
            .isEqualTo(
                MarketQuery(
                    text = "втулка",
                    category = ListingCategory.Components,
                    sort = ListingSort.PriceAsc,
                )
            )
        compose.onNodeWithText("Ничего не нашли").assertIsDisplayed()

        market.pages = mapOf(null to Page(listingBriefs(1, 2), null))
        compose.onNodeWithText("Сбросить").performScrollTo().performClick()
        settle()
        // The filters are lifted, the order stays.
        assertThat(market.queries.last().first).isEqualTo(MarketQuery(sort = ListingSort.PriceAsc))
        compose.onNodeWithTag("listing:l1").assertIsDisplayed()
    }

    @Test
    fun `the price and the city go to the server as typed`() {
        val market = FakeMarket()
        start(market)
        openMarket()

        compose.onNodeWithTag("market:filters").performClick()
        compose.onNodeWithTag("market:price_min").performTextInput("1000")
        compose.onNodeWithTag("market:price_max").performTextInput("5000")
        compose.onNodeWithTag("market:city").performTextInput("Казань")
        settle()

        assertThat(market.queries.last().first)
            .isEqualTo(MarketQuery(priceMin = 1000, priceMax = 5000, city = "Казань"))
    }

    @Test
    fun `a failure of the list is an error with a retry`() {
        val market = FakeMarket()
        market.nextError = DataError.Offline(IOException("x"))
        start(market)
        openMarket()
        compose.onNodeWithText("Повторить").assertIsDisplayed()

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("listing:l1").assertIsDisplayed()
    }

    // --- the page ----------------------------------------------------------------------------

    @Test
    fun `a listing opens its page with the price, the place, the description and the seller`() {
        val market = FakeMarket()
        start(market)

        openListing()

        assertThat(market.listingCalls).containsExactly("l1")
        compose.onNodeWithText("Втулка 1").assertIsDisplayed()
        compose.onNodeWithTag("listing:price").assertIsDisplayed()
        compose.onNodeWithText("Описание объявления 1.").assertIsDisplayed()
        compose.onNodeWithText("Тестовый Райдер").performScrollTo().assertIsDisplayed()
        // Opening a page asks for no contact.
        assertThat(market.contactCalls).isEmpty()
    }

    @Test
    fun `the seller's other listings are on the page, and all of them are one tap away`() {
        val market = FakeMarket()
        start(market)
        openListing()

        compose.onNodeWithTag("listing:other:l11").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("listing:others_all").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(market.queries.last().first.seller).isEqualTo("test-rider")
        compose.onNodeWithText("Продавец @test-rider").assertIsDisplayed()
    }

    @Test
    fun `the catalog model and the bike of a listing open their screens`() {
        val market =
            FakeMarket(
                listings =
                    mapOf(
                        "l1" to
                            listingModel(
                                1,
                                componentModel =
                                    ListingCatalogLink("c1", "Кассета 1", "/components/c1", false),
                                bikeModel =
                                    ListingCatalogLink(
                                        "bm1",
                                        "Cube Reaction",
                                        "/bike-models/cube-reaction-bm000001",
                                        false,
                                    ),
                                linkedBike = ListingBikeLink(BikeId("b1"), "Мой Cube", "/b/cube"),
                            )
                    )
            )
        val dependencies = start(market)
        openListing()

        compose.onNodeWithTag("listing:bike_model").performScrollTo().performClick()
        assertThat(opened)
            .containsExactly("https://colabike.test/bike-models/cube-reaction-bm000001")

        compose.onNodeWithTag("listing:component").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(dependencies.components.modelCalls).containsExactly("c1")
    }

    @Test
    fun `a sold listing is opened with its note, cannot be saved and has no contact to ask for`() {
        val market =
            FakeMarket(listings = mapOf("l1" to listingModel(1, status = ListingStatus.Sold)))
        start(market)

        openListing()

        compose.onNodeWithTag("listing:notice").assertIsDisplayed()
        compose.onNodeWithText("Продано", ignoreCase = true).assertExists()
        compose.onNodeWithTag("listing:save").assertDoesNotExist()
        compose.onNodeWithTag("listing:contact_show").assertDoesNotExist()
        compose
            .onNodeWithText("Объявления уже нет на рынке, контакт не отдаётся.")
            .performScrollTo()
            .assertIsDisplayed()
        assertThat(market.contactCalls).isEmpty()
    }

    @Test
    fun `an expired listing says so, and an own draft is a draft that only the owner sees`() {
        val market =
            FakeMarket(
                listings =
                    mapOf(
                        "l1" to listingModel(1, expired = true),
                        "l2" to listingModel(2, status = ListingStatus.Draft, isOwner = true),
                    )
            )
        start(market)

        openListing("l1")
        compose.onNodeWithText("Срок размещения вышел", ignoreCase = true).assertExists()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("listing:l2").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Черновик", ignoreCase = true).assertExists()
        compose
            .onNodeWithText("Это ваше объявление: контакт видите только вы, в редакторе на сайте.")
            .performScrollTo()
            .assertIsDisplayed()
        assertThat(market.contactCalls).isEmpty()
    }

    @Test
    fun `a listing that is not there is told so, not shown as an error with a retry`() {
        start(FakeMarket(listings = emptyMap()))

        openListing()

        compose.onNodeWithText("Объявление не найдено").assertIsDisplayed()
        compose.onNodeWithText("Повторить").assertDoesNotExist()
    }

    @Test
    fun `a guest who meets a listing that is closed to them is offered the sign-in`() {
        start(FakeMarket(listings = emptyMap()), signedIn = false)

        openListing()

        compose
            .onNodeWithText("Закрытое объявление гостю не показывается. Войдите, если оно ваше.")
            .assertIsDisplayed()
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
    }

    // --- save --------------------------------------------------------------------------------

    @Test
    fun `the bookmark saves and un-saves, and the saved list holds only what is still saved`() {
        val market = FakeMarket()
        start(market)
        openListing()

        compose.onNodeWithTag("listing:save").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Сохранено").assertExists()
        assertThat(market.saveCalls).containsExactly("l1" to true)

        compose.onNodeWithTag("listing:save").performClick()
        compose.waitForIdle()
        assertThat(market.saveCalls.last()).isEqualTo("l1" to false)
        compose.onNodeWithText("Сохранить").assertExists()
    }

    @Test
    fun `a guest who taps the bookmark is asked to sign in and nothing is saved`() {
        val market = FakeMarket()
        start(market, signedIn = false)
        openListing()

        compose.onNodeWithTag("listing:save").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(market.saveCalls).isEmpty()
    }

    @Test
    fun `a refused save shows the old bookmark and says why`() {
        val market = FakeMarket()
        market.saveError = DataError.Offline(IOException("x"))
        start(market)
        openListing()

        compose.onNodeWithTag("listing:save").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Сохранить").assertExists()
        compose.onNodeWithText("Нет соединения", substring = true).assertExists()
        compose.onNodeWithTag("listing:save").assertIsEnabled()
    }

    @Test
    fun `the profile opens the saved listings, and a listing un-saved there leaves the list`() {
        // The two saved ones are saved on their pages too.
        val market =
            FakeMarket(listings = (1..4).associate { "l$it" to listingModel(it, saved = it <= 2) })
        start(market)
        compose.section("Профиль").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Объявления").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("listing:l1").assertIsDisplayed()
        compose.onNodeWithTag("listing:l2").assertIsDisplayed()

        compose.onNodeWithTag("listing:l1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("listing:save").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("listing:l1").assertDoesNotExist()
        compose.onNodeWithTag("listing:l2").assertIsDisplayed()
    }

    // --- the contact -------------------------------------------------------------------------

    @Test
    fun `the contact appears only after a tap, and it can be copied and taken off`() {
        val market = FakeMarket()
        start(market)
        openListing()
        compose.onNodeWithTag("listing:contact_show").performScrollTo().assertIsDisplayed()
        assertThat(market.contactCalls).isEmpty()
        compose.onNodeWithTag("listing:contact").assertDoesNotExist()

        compose.onNodeWithTag("listing:contact_show").performClick()
        compose.waitForIdle()

        assertThat(market.contactCalls).containsExactly("l1")
        compose.onNodeWithTag("listing:contact").assertIsDisplayed()
        compose.onNodeWithText("+7 900 111-22-33, Telegram @seller").assertIsDisplayed()

        compose.onNodeWithTag("listing:contact_copy").performClick()
        compose.waitForIdle()
        assertThat(clipboardText()).isEqualTo("+7 900 111-22-33, Telegram @seller")
        compose.onNodeWithText("Контакт скопирован").assertExists()

        compose.onNodeWithTag("listing:contact_hide").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("listing:contact").assertDoesNotExist()
        compose.onNodeWithTag("listing:contact_show").assertIsEnabled()
    }

    @Test
    fun `a contact that is a plain https address can be opened in the browser, anything else cannot`() {
        val market = FakeMarket(contacts = mapOf("l1" to "https://t.me/seller"))
        start(market)
        openListing()
        compose.onNodeWithTag("listing:contact_show").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("listing:contact_link").performClick()

        assertThat(opened).containsExactly("https://t.me/seller")
    }

    @Test
    fun `a contact that is not an https address offers no link`() {
        val market = FakeMarket(contacts = mapOf("l1" to "http://evil.example/x"))
        start(market)
        openListing()
        compose.onNodeWithTag("listing:contact_show").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("listing:contact").assertIsDisplayed()
        compose.onNodeWithTag("listing:contact_link").assertDoesNotExist()
        assertThat(opened).isEmpty()
    }

    @Test
    fun `a guest who asks for the contact is sent to sign in and no contact is requested`() {
        val market = FakeMarket()
        start(market, signedIn = false)
        openListing()
        compose
            .onNodeWithText("Контакт видят вошедшие в аккаунт с подтверждённой почтой.")
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithTag("listing:contact_show").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(market.contactCalls).isEmpty()
    }

    @Test
    fun `an unconfirmed e-mail and a limit are told in words and the button stays`() {
        val market = FakeMarket()
        start(market)
        openListing()
        market.contactError =
            DataError.Rejected(403, "email_verification_required", "Подтвердите почту")
        compose.onNodeWithTag("listing:contact_show").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("listing:contact_error").assertIsDisplayed()
        compose
            .onNodeWithText("без этого контакт продавца не показывается", substring = true)
            .assertExists()

        market.contactError = DataError.RateLimited(retryAfterSeconds = 120)
        compose.onNodeWithTag("listing:contact_show").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("listing:contact_error").assertIsDisplayed()
        // The limit is told with the wait in minutes: 120 seconds are two.
        compose.onNodeWithText("2 мин", substring = true).assertExists()

        // Then it works, once the limit is over.
        compose.onNodeWithTag("listing:contact_show").assertIsEnabled()
        compose.onNodeWithTag("listing:contact_show").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("listing:contact").assertIsDisplayed()
        assertThat(market.contactCalls).hasSize(3)
    }
}
