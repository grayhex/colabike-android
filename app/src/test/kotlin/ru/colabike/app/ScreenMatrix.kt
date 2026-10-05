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
import java.io.File
import ru.colabike.app.config.LaunchFrame
import ru.colabike.app.config.LaunchPlan
import ru.colabike.app.config.OnboardingScreen
import ru.colabike.app.config.UpdateRequiredScreen
import ru.colabike.app.login.LoginScreen
import ru.colabike.app.login.LoginUiState
import ru.colabike.app.navigation.Destination
import ru.colabike.app.ui.AppShell
import ru.colabike.app.ui.UiText
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.AppNotice
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.Compatibility
import ru.colabike.core.model.Feature
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.LaunchFill
import ru.colabike.core.model.ListingBikeLink
import ru.colabike.core.model.ListingCatalogLink
import ru.colabike.core.model.NearbyOffer
import ru.colabike.core.model.NearbyOffers
import ru.colabike.core.model.NearbyOffersState
import ru.colabike.core.model.NearbyReason
import ru.colabike.core.model.NoticeAction
import ru.colabike.core.model.NoticeKind
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.OnboardingItem
import ru.colabike.core.model.Page
import ru.colabike.core.model.RideId
import ru.colabike.core.model.ServiceLinks
import ru.colabike.core.model.UpdateMode
import ru.colabike.core.model.UpdateState

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

    /**
     * Profile → Notifications: the account's part as it opens, an account that has set most things
     * (a chosen circle, quiet hours, a pause, mutes), and the phone's part after a refusal.
     */
    NotificationSettings("notification_settings"),
    NotificationSettingsBusy("notification_settings_busy"),
    NotificationSettingsPhone("notification_settings_phone"),

    /**
     * Profile → Notifications → Rides near me: an area from the site with a kind chosen, an area
     * this phone has just read waiting for a yes, and the rides on now in it.
     */
    Nearby("nearby"),
    NearbyDraft("nearby_draft"),
    NearbyOffers("nearby_offers"),

    /** The component catalog, a model's page, and the credits of its photos further down. */
    Components("components"),
    Component("component"),
    ComponentCredits("component_credits"),

    /** About with the pages the server names (support among them), and a link into what is off. */
    AboutConfigured("about_configured"),
    FeatureOff("feature_off"),

    /** What the server announces: the launch screen, the introduction, a message, the update. */
    Launch("launch"),
    Onboarding("onboarding"),
    NoticePromo("notice_promo"),
    NoticeService("notice_service"),
    NoticeMaintenance("notice_maintenance"),
    UpdateOffer("update_offer"),
    UpdateRequired("update_required"),

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

private const val NOTICE_IMAGE = "https://colabike.ru/api/assets/notice?width=1280"

private val onboardingItems =
    listOf(
        OnboardingItem(
            "Ваш гараж",
            "Соберите велосипед по частям и ведите журнал обслуживания.",
            "p",
        ),
        OnboardingItem("Покатушки", "Планы, маршруты и разбор проезда: всё рядом с людьми.", null),
        OnboardingItem("Сообщения", null, null),
    )

/** The shell as the server announces something: a message, or a version policy for build 10. */
private fun announcing(
    notice: AppNotice? = null,
    compatibility: Compatibility = Compatibility.None,
) =
    FakeDependencies(
        bikes = FakeBikes(mapOf(null to Page(bikes(0, 6), "c1"))),
        appConfig = FakeAppConfig().apply { set(notice = notice, compatibility = compatibility) },
        configAssets = FakeConfigAssets(mutableMapOf(NOTICE_IMAGE to File("notice.webp"))),
        versionCode = 10,
    )

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
                    Screen.AboutConfigured ->
                        AppShell(
                            FakeDependencies(
                                appConfig =
                                    FakeAppConfig().apply {
                                        set(
                                            links =
                                                ServiceLinks(
                                                    help = "https://help.example.ru/guide",
                                                    privacy = null,
                                                    terms = null,
                                                    about = null,
                                                    support = "https://help.example.ru/support",
                                                )
                                        )
                                    }
                            )
                        )
                    Screen.NotificationSettingsBusy ->
                        AppShell(
                            FakeDependencies(
                                notificationSettings =
                                    FakeNotificationSettings(busyNotificationSettings),
                                deviceNotifications = FakeDeviceNotifications(readyPhone),
                            )
                        )
                    Screen.NotificationSettingsPhone ->
                        AppShell(
                            FakeDependencies(
                                deviceNotifications = FakeDeviceNotifications(deniedPhone)
                            )
                        )
                    Screen.Nearby,
                    Screen.NearbyDraft,
                    Screen.NearbyOffers ->
                        AppShell(
                            FakeDependencies(
                                nearby =
                                    FakeNearby(
                                        current = activeNearbySettings,
                                        offers =
                                            NearbyOffers(
                                                NearbyOffersState.Ready,
                                                listOf(
                                                    NearbyOffer(
                                                        PreviewData.plannedRide,
                                                        setOf(NearbyReason.Nearby),
                                                    ),
                                                    NearbyOffer(
                                                        PreviewData.plannedRide.copy(
                                                            id = RideId("plan-2"),
                                                            title = "Вечерний круг по набережной",
                                                        ),
                                                        setOf(
                                                            NearbyReason.Nearby,
                                                            NearbyReason.Intent,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                    )
                            )
                        )
                    Screen.FeatureOff ->
                        AppShell(
                            FakeDependencies(
                                appConfig =
                                    FakeAppConfig().apply {
                                        set(features = featuresOff(Feature.Market))
                                    },
                                pending = FakePending().apply { offer(Destination.Listing("l1")) },
                            )
                        )
                    Screen.Launch ->
                        LaunchFrame(
                            LaunchPlan.Show(File("launch.webp"), LaunchFill.Crop, "Сезон открыт")
                        )
                    Screen.Onboarding ->
                        OnboardingScreen(
                            items = onboardingItems,
                            imageOf = { File("onboarding.webp") },
                            onDone = {},
                        )
                    Screen.UpdateRequired ->
                        UpdateRequiredScreen(
                            state = UpdateState.Required("https://play.example.ru/colabike", null),
                            refreshing = false,
                            refreshFailed = true,
                            onUpdate = {},
                            onCheckAgain = {},
                        )
                    Screen.NoticePromo,
                    Screen.NoticeService,
                    Screen.NoticeMaintenance ->
                        AppShell(
                            announcing(
                                notice =
                                    AppNotice(
                                        revision = 1,
                                        kind =
                                            when (screen) {
                                                Screen.NoticePromo -> NoticeKind.Promo
                                                Screen.NoticeService -> NoticeKind.Service
                                                else -> NoticeKind.Maintenance
                                            },
                                        title = "В воскресенье — общая покатушка",
                                        body = "Собираемся в 10:00 у главного входа в парк.",
                                        imageUrl =
                                            if (screen == Screen.NoticePromo) NOTICE_IMAGE
                                            else null,
                                        action =
                                            NoticeAction("Подробнее", "https://colabike.ru/r/1"),
                                    )
                            )
                        )
                    Screen.UpdateOffer ->
                        AppShell(
                            announcing(
                                compatibility =
                                    Compatibility(
                                        minimumSupportedVersionCode = 15,
                                        latestVersionCode = 20,
                                        mode = UpdateMode.Soft,
                                        updateUrl = "https://play.example.ru/colabike",
                                        message = null,
                                    )
                            )
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
        Screen.AboutConfigured -> {
            section("Профиль").performClick()
            onNodeWithText("О приложении").performScrollTo().performClick()
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
        Screen.NotificationSettings,
        Screen.NotificationSettingsBusy,
        Screen.NotificationSettingsPhone -> {
            section("Профиль").performClick()
            onNodeWithText("Что, когда и от кого присылать").performScrollTo().performClick()
            when (screen) {
                Screen.NotificationSettingsBusy ->
                    onNodeWithTag("notif-settings:mute:ride-1").performScrollTo()
                Screen.NotificationSettingsPhone ->
                    onNodeWithTag("notif-settings:os-off").performScrollTo()
                else -> Unit
            }
        }
        Screen.Nearby,
        Screen.NearbyDraft,
        Screen.NearbyOffers -> {
            section("Профиль").performClick()
            onNodeWithText("Что, когда и от кого присылать").performScrollTo().performClick()
            onNodeWithTag("notif-settings:nearby").performScrollTo().performClick()
            when (screen) {
                Screen.NearbyDraft -> {
                    onNodeWithTag("nearby:locate").performScrollTo().performClick()
                    onNodeWithTag("nearby:locate-allow").performClick()
                    onNodeWithTag("nearby:confirm").performScrollTo()
                }
                Screen.NearbyOffers ->
                    onNodeWithTag("nearby:offers").performScrollTo().performClick()
                else -> Unit
            }
        }
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
