package ru.colabike.app

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import ru.colabike.app.login.LoginScreen
import ru.colabike.app.login.LoginUiState
import ru.colabike.app.ui.AppShell
import ru.colabike.app.ui.UiText
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.ListingBikeLink
import ru.colabike.core.model.ListingCatalogLink
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.Page

/** Every screen of the app, as AGENTS.md requires it in screenshots. */
enum class Screen(val file: String) {
    Login("login"),
    Bikes("bikes"),
    BikeDetail("bike_detail"),

    /** The rest of the bike page below the first screen: the passport, the build in groups. */
    BikeDetailPassport("bike_detail_passport"),
    BikeDetailBuild("bike_detail_build"),
    Profile("profile"),

    /** What a guest sees on the profile tab, and the rest of the profile below the fold. */
    ProfileGuest("profile_guest"),
    ProfileMore("profile_more"),
    Devices("devices"),
    About("about"),

    /** The author of a bike: header, numbers, and bikes. */
    Person("person"),

    /** The followers behind the number on a person's page. */
    PeopleList("people_list"),

    /** The search as it opens: the field, the tabs, the facets, and what to type. */
    SearchStart("search_start"),

    /** The feed of a member, and what a guest sees in its place. */
    Feed("feed"),
    FeedGuest("feed_guest"),

    /** A bike's journal, and an entry: text with structure, components, the save. */
    JournalList("journal_list"),
    Journal("journal"),

    /** The discussion under a bike: a member with the box to write in, and a guest. */
    Comments("comments"),
    CommentsGuest("comments_guest"),

    /** The Rides section as it opens (plans ahead), a plan's page, and a completed ride's page. */
    Rides("rides"),
    RidePlan("ride_plan"),
    RideCompleted("ride_completed"),

    /** The same ride further down: the route and the charts of its series; and the route's map. */
    RideCompletedAnalysis("ride_completed_analysis"),
    RideMap("ride_map"),

    /** The inbox, opened by the bell of the first screen. */
    Notifications("notifications"),

    /** The component catalog, a model's page, and the credits of its photos further down. */
    Components("components"),
    Component("component"),
    ComponentCredits("component_credits"),

    /** The market list, with its filters open, a listing's page, its contact, the saved ones. */
    Market("market"),
    MarketFilters("market_filters"),
    Listing("listing"),
    ListingContact("listing_contact"),
    SavedMarket("saved_market"),

    /**
     * The Messages section with the SDK's list, a conversation, the form for a new one, a guest.
     */
    Messages("messages"),
    Conversation("conversation"),
    NewConversation("new_conversation"),
    MessagesGuest("messages_guest"),
}

enum class Look(val dark: Boolean, val fontScale: Float, val file: String) {
    Light(dark = false, fontScale = 1f, file = "light"),
    Dark(dark = true, fontScale = 1f, file = "dark"),
    LargeText(dark = false, fontScale = 2f, file = "font200"),
}

/** From the Rides section to the first completed ride's page. */
private fun ComposeContentTestRule.openCompletedRide() {
    section("Покатушки").performClick()
    onNodeWithText("Состоявшиеся").performClick()
    onNodeWithContentDescription("Покатушка 0", substring = true).performClick()
}

/** From the list to the first bike's page and from there to its journal. */
private fun ComposeContentTestRule.openJournal() {
    onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
    onNodeWithText("Журнал велосипеда").performScrollTo().performClick()
}

/** From the list to the first bike's page and from there to its author. */
private fun ComposeContentTestRule.openAuthor() {
    onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
    onNodeWithText("Тестовый Райдер").performScrollTo().performClick()
}

/** The listings of the market in the pictures: the first is tied to the catalog and a bike. */
private val marketListings =
    (1..4).associate {
        "l$it" to
            listingModel(
                it,
                saved = it == 1,
                componentModel =
                    if (it == 1) ListingCatalogLink("c1", "Кассета 1", "/components/c1", false)
                    else null,
                linkedBike =
                    if (it == 1) ListingBikeLink(BikeId("b1"), "Городской Трэвел", "/b/b1")
                    else null,
            )
    }

@OptIn(ExperimentalCoilApi::class)
private val photos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * Renders [screen] with fake data and compares it with
 * src/test/screenshots/<screen>_<window>_<look>.png. Ripples are off: the platform draws them on
 * its own clock, so a click would make captures differ between runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun ComposeContentTestRule.captureScreen(screen: Screen, window: String, look: Look) {
    setContent {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides photos,
            LocalRippleConfiguration provides null,
            LocalDensity provides Density(LocalDensity.current.density, look.fontScale),
        ) {
            ColaBikeTheme(darkTheme = look.dark) {
                when (screen) {
                    Screen.Login ->
                        LoginScreen(
                            LoginUiState(
                                email = "rider@example.test",
                                password = "password",
                                error = UiText.Res(R.string.login_wrong_credentials),
                                yandexEnabled = true,
                            ),
                            onEmailChange = {},
                            onPasswordChange = {},
                            onTogglePassword = {},
                            onSubmit = {},
                            onYandex = {},
                            onBrowseAsGuest = {},
                        )
                    Screen.ProfileGuest,
                    Screen.FeedGuest,
                    Screen.CommentsGuest,
                    Screen.MessagesGuest ->
                        AppShell(
                            FakeDependencies(
                                comments = sampleDiscussion(),
                                auth = FakeAuth(initial = AuthState.SignedOut),
                                settings = FakeSettings(guest = true),
                            )
                        )
                    else ->
                        AppShell(
                            FakeDependencies(
                                bikes = FakeBikes(mapOf(null to Page(bikes(0, 6), "c1"))),
                                market = FakeMarket(listings = marketListings),
                                comments = sampleDiscussion(),
                                notifications =
                                    FakeNotifications(
                                        mapOf(null to Page(sampleInbox(), null)),
                                        NotificationCount(4, capped = false),
                                    ),
                                feed =
                                    FakeFeed(
                                        mapOf(
                                            null to
                                                Page(
                                                    listOf(
                                                        feedBike(0),
                                                        feedJournal(1),
                                                        FeedItem.Ride(
                                                            PreviewData.ride,
                                                            PreviewData.ride.time!!,
                                                        ),
                                                        FeedItem.Listing(
                                                            PreviewData.listing,
                                                            PreviewData.ride.time!!,
                                                        ),
                                                    ),
                                                    null,
                                                )
                                        )
                                    ),
                            )
                        )
                }
            }
        }
    }
    waitForIdle()
    when (screen) {
        Screen.BikeDetail ->
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        Screen.BikeDetailPassport -> {
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
            onNodeWithText("Характеристики").performScrollTo()
        }
        Screen.BikeDetailBuild -> {
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
            onNodeWithText("Shimano Deore 10-speed").performScrollTo()
        }
        Screen.Profile,
        Screen.ProfileGuest -> section("Профиль").performClick()
        Screen.ProfileMore -> {
            section("Профиль").performClick()
            onNodeWithText("О приложении").performScrollTo()
        }
        Screen.Devices -> {
            section("Профиль").performClick()
            onNodeWithText("Устройства и входы").performScrollTo().performClick()
        }
        Screen.About -> {
            section("Профиль").performClick()
            onNodeWithText("О приложении").performScrollTo().performClick()
        }
        Screen.Person -> openAuthor()
        Screen.PeopleList -> {
            openAuthor()
            onNodeWithContentDescription("Подписчики, 12").performClick()
        }
        Screen.SearchStart -> onNodeWithContentDescription("Поиск").performClick()
        Screen.Feed,
        Screen.FeedGuest -> section("Лента").performClick()
        Screen.Comments,
        Screen.CommentsGuest -> {
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
            onNodeWithText("Комментарии").performScrollTo().performClick()
        }
        Screen.Rides -> section("Покатушки").performClick()
        Screen.RidePlan -> {
            section("Покатушки").performClick()
            onNodeWithContentDescription("Воскресный выезд за город", substring = true)
                .performClick()
        }
        Screen.RideCompleted -> openCompletedRide()
        Screen.RideCompletedAnalysis -> {
            openCompletedRide()
            // The last chart (heart rate, with its gap) brings the others into the picture too.
            onNodeWithContentDescription("Пульс: от", substring = true).performScrollTo()
        }
        Screen.RideMap -> {
            openCompletedRide()
            // On a wide window the map is already beside the page; a phone opens it by a button.
            if (onAllNodesWithText("Открыть карту").fetchSemanticsNodes().isNotEmpty()) {
                onNodeWithText("Открыть карту").performScrollTo().performClick()
            }
        }
        Screen.Notifications ->
            onNode(hasContentDescription("Уведомления", substring = true)).performClick()
        Screen.Components -> onNodeWithContentDescription("Каталог компонентов").performClick()
        Screen.Component,
        Screen.ComponentCredits -> {
            onNodeWithContentDescription("Каталог компонентов").performClick()
            onNodeWithTag("component:c1").performClick()
            if (screen == Screen.ComponentCredits) {
                onNodeWithTag("component:page").performScrollToNode(hasText("Фотографии"))
            }
        }
        Screen.Market -> onNodeWithContentDescription("Объявления").performClick()
        Screen.MarketFilters -> {
            onNodeWithContentDescription("Объявления").performClick()
            onNodeWithTag("market:filters").performClick()
        }
        Screen.Listing,
        Screen.ListingContact -> {
            onNodeWithContentDescription("Объявления").performClick()
            onNodeWithTag("listing:l1").performClick()
            if (screen == Screen.ListingContact) {
                onNodeWithTag("listing:contact_show").performScrollTo().performClick()
            }
        }
        Screen.SavedMarket -> {
            section("Профиль").performClick()
            onNodeWithText("Сохранённые объявления").performScrollTo().performClick()
        }
        Screen.Messages,
        Screen.MessagesGuest -> section("Сообщения").performClick()
        Screen.Conversation -> {
            section("Сообщения").performClick()
            // Not a touch: on a wide window the row stays in the picture, and a pressed or
            // focused row is drawn at a moment of the platform's own clock.
            onNodeWithTag("chat:open:dm-1").performSemanticsAction(SemanticsActions.OnClick)
        }
        Screen.NewConversation -> {
            section("Сообщения").performClick()
            onNodeWithContentDescription("Новое сообщение").performClick()
        }
        Screen.JournalList -> openJournal()
        Screen.Journal -> {
            openJournal()
            onNodeWithContentDescription("Запись 0", substring = true).performClick()
        }
        else -> Unit
    }
    mainClock.advanceTimeBy(3_000)
    waitForIdle()
    captureWhenDrawn("src/test/screenshots/${screen.file}_${window}_${look.file}.png")
}
