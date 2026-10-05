package ru.colabike.app

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.bikes.BikeDetailScreen
import ru.colabike.app.bikes.BikeDetailUiState
import ru.colabike.app.bikes.BikesScreen
import ru.colabike.app.bikes.BikesUiState
import ru.colabike.app.comments.CommentNode
import ru.colabike.app.comments.CommentsActions
import ru.colabike.app.comments.CommentsScreen
import ru.colabike.app.comments.CommentsUiState
import ru.colabike.app.comments.Composer
import ru.colabike.app.components.ComponentScreen
import ru.colabike.app.components.ComponentUiState
import ru.colabike.app.components.ComponentsScreen
import ru.colabike.app.components.ComponentsUiState
import ru.colabike.app.devices.DevicesScreen
import ru.colabike.app.devices.DevicesUiState
import ru.colabike.app.feed.FeedActions
import ru.colabike.app.feed.FeedScreen
import ru.colabike.app.feed.FeedUiState
import ru.colabike.app.intents.IntentActions
import ru.colabike.app.intents.IntentEditorActions
import ru.colabike.app.intents.IntentEditorScreen
import ru.colabike.app.intents.IntentEditorUiState
import ru.colabike.app.intents.IntentForm
import ru.colabike.app.intents.IntentScreen
import ru.colabike.app.intents.IntentSegment
import ru.colabike.app.intents.IntentUiState
import ru.colabike.app.intents.IntentsActions
import ru.colabike.app.intents.IntentsScreen
import ru.colabike.app.intents.IntentsUiState
import ru.colabike.app.journal.JournalActions
import ru.colabike.app.journal.JournalListScreen
import ru.colabike.app.journal.JournalScreen
import ru.colabike.app.journal.JournalUiState
import ru.colabike.app.market.ContactState
import ru.colabike.app.market.ListingActions
import ru.colabike.app.market.ListingScreen
import ru.colabike.app.market.ListingUiState
import ru.colabike.app.market.MarketFields
import ru.colabike.app.market.MarketScreen
import ru.colabike.app.market.MarketUiState
import ru.colabike.app.market.SavedMarketScreen
import ru.colabike.app.messages.ChatConnection
import ru.colabike.app.messages.ChatFailure
import ru.colabike.app.messages.ChatStatusScreen
import ru.colabike.app.messages.GuestMessages
import ru.colabike.app.messages.NewConversationScreen
import ru.colabike.app.messages.NewConversationUiState
import ru.colabike.app.messages.WriteState
import ru.colabike.app.nearby.NearbyActions
import ru.colabike.app.nearby.NearbyOffersActions
import ru.colabike.app.nearby.NearbyOffersScreen
import ru.colabike.app.nearby.NearbyOffersUiState
import ru.colabike.app.nearby.NearbyScreen
import ru.colabike.app.nearby.NearbyUiState
import ru.colabike.app.notifications.InboxUi
import ru.colabike.app.notifications.NotificationsScreen
import ru.colabike.app.notifications.settings.NotificationSettingsActions
import ru.colabike.app.notifications.settings.NotificationSettingsScreen
import ru.colabike.app.notifications.settings.NotificationSettingsUiState
import ru.colabike.app.notifications.settings.SettingsProblem
import ru.colabike.app.people.PeopleListKind
import ru.colabike.app.people.PeopleListScreen
import ru.colabike.app.people.PeopleListUiState
import ru.colabike.app.people.PersonActions
import ru.colabike.app.people.PersonScreen
import ru.colabike.app.people.PersonUiState
import ru.colabike.app.profile.ProfileScreen
import ru.colabike.app.profile.ProfileUiState
import ru.colabike.app.rides.AnalysisSection
import ru.colabike.app.rides.AnalysisUiState
import ru.colabike.app.rides.RideActions
import ru.colabike.app.rides.RideRow
import ru.colabike.app.rides.RideScreen
import ru.colabike.app.rides.RideSegment
import ru.colabike.app.rides.RideUiState
import ru.colabike.app.rides.RidesScreen
import ru.colabike.app.rides.RidesUiState
import ru.colabike.app.search.Results
import ru.colabike.app.search.SearchScreen
import ru.colabike.app.search.SearchTab
import ru.colabike.app.search.SearchUiState
import ru.colabike.app.settings.ThemeMode
import ru.colabike.app.ui.PagedState
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.designsystem.theme.ColaCanvas
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AppNotification
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.ComponentFilters
import ru.colabike.core.model.ComponentQuery
import ru.colabike.core.model.ComponentSort
import ru.colabike.core.model.DataError
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.IntentReadiness
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.IntentWindowDraft
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.Listing
import ru.colabike.core.model.ListingCategory
import ru.colabike.core.model.ListingCondition
import ru.colabike.core.model.ListingSort
import ru.colabike.core.model.ListingStatus
import ru.colabike.core.model.ListingType
import ru.colabike.core.model.MarketQuery
import ru.colabike.core.model.NearbyOffers
import ru.colabike.core.model.NearbyOffersState
import ru.colabike.core.model.NearbySource
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationFilter
import ru.colabike.core.model.NotificationSettingsChange
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.RideRole
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.SellerListings

/** The states a screen has besides its content (DESIGN.md): loading, empty, error, an odd bike. */
enum class ScreenState(val file: String) {
    BikesLoading("bikes_loading"),
    BikesEmptyMine("bikes_empty_mine"),
    BikesError("bikes_error"),
    BikeDetailError("bike_detail_error"),
    BikeDetailBare("bike_detail_bare"),
    BikeDetailPrivateGuest("bike_detail_private_guest"),
    BikesSearchEmpty("bikes_search_empty"),
    BikesFiltered("bikes_filtered"),
    DevicesLoading("devices_loading"),
    DevicesError("devices_error"),
    ProfileFailed("profile_failed"),
    PersonFollowing("person_following"),
    PersonOwn("person_own"),
    PersonNoBikes("person_no_bikes"),
    PersonNotFound("person_not_found"),
    PeopleFollowing("people_following"),
    PeopleEmpty("people_empty"),
    SearchBikes("search_bikes"),
    SearchBikesEmpty("search_bikes_empty"),
    SearchPeople("search_people"),
    SearchPeopleEmpty("search_people_empty"),
    SearchError("search_error"),
    FeedEmpty("feed_empty"),
    FeedEmptyRides("feed_empty_rides"),
    FeedError("feed_error"),
    FeedLoadingMore("feed_loading_more"),
    JournalListEmpty("journal_list_empty"),
    JournalListError("journal_list_error"),
    SavedEmpty("saved_empty"),
    JournalDraft("journal_draft"),
    JournalNotFoundGuest("journal_not_found_guest"),
    JournalSaved("journal_saved"),
    CommentsReplying("comments_replying"),
    CommentsEditing("comments_editing"),
    CommentsSendError("comments_send_error"),
    CommentsTooLong("comments_too_long"),
    CommentsEmpty("comments_empty"),
    CommentsBranch("comments_branch"),
    CommentsMoreError("comments_more_error"),
    RidesEmpty("rides_empty"),
    RidesError("rides_error"),
    RidesMoreError("rides_more_error"),
    RidesMine("rides_mine"),
    RidesMyPlans("rides_my_plans"),
    RideNotFoundGuest("ride_not_found_guest"),
    AnalysisLoading("analysis_loading"),
    AnalysisFailed("analysis_failed"),
    NotificationsEmpty("notifications_empty"),
    NotificationsError("notifications_error"),
    NotificationsMoreError("notifications_more_error"),

    /** Narrowed to the unread and nothing is left, and a "mark as read" that the server refused. */
    NotificationsAllRead("notifications_all_read"),
    NotificationsReadFailed("notifications_read_failed"),

    /** The settings while loading, when they cannot be read, paused, and after a refused change. */
    NotificationSettingsLoading("notification_settings_loading"),
    NotificationSettingsFailed("notification_settings_failed"),
    NotificationSettingsPaused("notification_settings_paused"),
    NotificationSettingsRefused("notification_settings_refused"),

    /** Rides near me: unreadable, a phone's area whose term passed, and the offers' empty lists. */
    NearbyFailed("nearby_failed"),

    /** "I want to ride": nothing yet, a page that is gone, and a form that cannot be saved. */
    IntentsEmpty("intents_empty"),
    IntentUnavailable("intent_unavailable"),
    IntentEditorSaving("intent_editor_saving"),
    NearbyExpired("nearby_expired"),
    NearbyOffersNoArea("nearby_offers_no_area"),
    NearbyOffersNone("nearby_offers_none"),
    ComponentsEmpty("components_empty"),
    ComponentsNone("components_none"),
    ComponentsError("components_error"),
    ComponentsMoreError("components_more_error"),
    ComponentsFiltered("components_filtered"),
    ComponentArchived("component_archived"),
    ComponentNotFound("component_not_found"),
    ComponentFailed("component_failed"),
    ComponentNoPhotos("component_no_photos"),
    MarketEmpty("market_empty"),
    MarketNone("market_none"),
    MarketError("market_error"),
    MarketMoreError("market_more_error"),
    MarketFiltered("market_filtered"),
    MarketSeller("market_seller"),
    ListingSold("listing_sold"),
    ListingExpired("listing_expired"),
    ListingDraft("listing_draft"),
    ListingNotFound("listing_not_found"),
    ListingNotFoundGuest("listing_not_found_guest"),
    ListingFailed("listing_failed"),
    ListingFree("listing_free"),
    ListingNoContact("listing_no_contact"),
    ListingGuest("listing_guest"),
    ListingContactUnverified("listing_contact_unverified"),
    ListingContactLimited("listing_contact_limited"),
    ListingContactShown("listing_contact_shown"),
    SavedMarketEmpty("saved_market_empty"),
    SavedMarketError("saved_market_error"),
    ChatConnecting("chat_connecting"),
    ChatFailed("chat_failed"),
    ChatEmailUnconfirmed("chat_email_unconfirmed"),
    ChatUnavailable("chat_unavailable"),
    ChatGuest("chat_guest"),
    ChatNewPeople("chat_new_people"),
    ChatNewSearch("chat_new_search"),
    ChatNewGroup("chat_new_group"),
    ChatNewEmpty("chat_new_empty"),
    ChatNewError("chat_new_error"),
    ChatNewRefused("chat_new_refused"),
}

@OptIn(ExperimentalCoilApi::class)
private val photos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

private val bareBike =
    PreviewData.bikeDetail.copy(
        summary =
            PreviewData.bikeWithoutPhoto.copy(
                name = "Старый шоссейник",
                year = 1992,
                isPublic = false,
                author = PreviewData.rider,
            ),
        trim = "",
        description = "",
        weightKg = null,
        mileageKm = 0,
        manufacturerUrl = null,
        priceRub = null,
        groupOrder = emptyList(),
        photos = emptyList(),
        components =
            listOf(
                BikeComponent("c1", "build", "Рама", "Reynolds 531, сталь", ""),
                BikeComponent("c2", "build", "Вилка", "Жёсткая, сталь", ""),
                BikeComponent("c3", "accessories", "Звонок", "Латунный", ""),
            ),
    )

/** Phone width, both themes and 200 % text. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class StateScreenshotTest(private val state: ScreenState, private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capture() {
        // No pulsing skeletons: the picture must not depend on the virtual clock.
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides photos,
                LocalRippleConfiguration provides null,
                LocalDensity provides Density(LocalDensity.current.density, look.fontScale),
            ) {
                ColaBikeTheme(darkTheme = look.dark) { ColaCanvas { Content(state) } }
            }
        }
        // A capture right after waitForIdle() can come out as the bare window, before the first
        // frame is drawn (seen on CI as an all-white image); the clock lets that frame happen.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.captureWhenDrawn(
            "src/test/screenshots/state_${state.file}_compact_${look.file}.png"
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> =
            ScreenState.entries.flatMap { state -> Look.entries.map { arrayOf(state, it) } }
    }
}

@Composable
private fun Content(state: ScreenState) {
    when (state) {
        ScreenState.BikesLoading -> BikesWith(BikesUiState(loading = true))
        ScreenState.BikesEmptyMine ->
            BikesWith(
                BikesUiState(
                    query = BikeQuery(BikeScope.Mine),
                    loading = false,
                    bikes = emptyList(),
                )
            )
        ScreenState.BikesSearchEmpty ->
            BikesWith(
                BikesUiState(
                    query = BikeQuery(text = "Бромптон", categories = setOf("urban_touring")),
                    typed = "Бромптон",
                    loading = false,
                    bikes = emptyList(),
                )
            )
        ScreenState.BikesFiltered ->
            BikesWith(
                BikesUiState(
                    query = BikeQuery(text = "cube", categories = setOf("mtb", "urban_touring")),
                    typed = "cube",
                    loading = false,
                    bikes = listOf(PreviewData.bike, PreviewData.bikeWithoutPhoto),
                )
            )
        ScreenState.BikesError ->
            BikesWith(BikesUiState(loading = false, error = UiText.Res(R.string.error_offline)))
        ScreenState.BikeDetailError ->
            BikeDetailScreen(
                BikeDetailUiState.Failed(UiText.Res(R.string.error_not_found)),
                showBack = true,
                onBack = {},
                onRetry = {},
            )
        ScreenState.DevicesLoading -> DevicesWith(DevicesUiState.Loading)
        ScreenState.DevicesError ->
            DevicesWith(DevicesUiState.Failed(UiText.Res(R.string.error_offline)))
        ScreenState.ProfileFailed ->
            ProfileScreen(
                ProfileUiState.Failed(UiText.Res(R.string.error_offline)),
                themeMode = ThemeMode.System,
                onThemeMode = {},
                onRetry = {},
                onSignOut = {},
                onSignIn = {},
                onRegister = {},
                onOpenDevices = {},
                onOpenNotifications = {},
                onManageOnWeb = {},
                onOpenAbout = {},
            )
        ScreenState.BikeDetailPrivateGuest ->
            BikeDetailScreen(
                BikeDetailUiState.Failed(UiText.Res(R.string.error_not_found), notFound = true),
                showBack = true,
                onBack = {},
                onRetry = {},
                onSignIn = {},
            )
        ScreenState.PersonFollowing ->
            PersonWith(
                PersonUiState(
                    profile =
                        profileOf(
                            PreviewData.rider,
                            Relationship(
                                isSelf = false,
                                following = true,
                                followedBy = true,
                                friends = true,
                            ),
                        ),
                    loading = false,
                    bikes = listOf(PreviewData.bike, PreviewData.bikeWithoutPhoto),
                )
            )
        ScreenState.PersonOwn ->
            PersonWith(
                PersonUiState(
                    profile =
                        profileOf(
                            PreviewData.rider,
                            Relationship(
                                isSelf = true,
                                following = false,
                                followedBy = false,
                                friends = false,
                            ),
                        ),
                    loading = false,
                    bikes = listOf(PreviewData.bike),
                )
            )
        ScreenState.PersonNoBikes ->
            PersonWith(
                PersonUiState(
                    profile = profileOf(PreviewData.rider).copy(bio = "", location = ""),
                    loading = false,
                )
            )
        ScreenState.PersonNotFound ->
            PersonWith(
                PersonUiState(
                    loading = false,
                    error = UiText.Res(R.string.error_not_found),
                    notFound = true,
                )
            )
        ScreenState.PeopleFollowing ->
            PeopleWith(PeopleListUiState(people = people(10, 4), loading = false))
        ScreenState.PeopleEmpty ->
            PeopleWith(PeopleListUiState(people = emptyList(), loading = false))
        ScreenState.SearchBikes ->
            SearchWith(
                SearchUiState(
                    typed = "cube",
                    text = "cube",
                    category = "mtb",
                    bikes =
                        Results(
                            asked = true,
                            items = listOf(PreviewData.bike, PreviewData.bikeWithoutPhoto),
                        ),
                )
            )
        ScreenState.SearchBikesEmpty ->
            SearchWith(
                SearchUiState(
                    typed = "Бромптон",
                    text = "Бромптон",
                    electric = true,
                    bikes = Results(asked = true),
                )
            )
        ScreenState.SearchPeople ->
            SearchWith(
                SearchUiState(
                    tab = SearchTab.People,
                    typed = "райдер",
                    text = "райдер",
                    people = Results(asked = true, items = people(0, 4)),
                )
            )
        ScreenState.SearchPeopleEmpty ->
            SearchWith(
                SearchUiState(
                    tab = SearchTab.People,
                    typed = "zzzz",
                    text = "zzzz",
                    people = Results(asked = true),
                )
            )
        ScreenState.SearchError ->
            SearchWith(
                SearchUiState(
                    typed = "cube",
                    text = "cube",
                    bikes = Results(asked = true, error = UiText.Res(R.string.error_offline)),
                )
            )
        ScreenState.FeedEmpty -> FeedWith(FeedUiState(page = PagedState(loading = false)))
        ScreenState.FeedEmptyRides ->
            FeedWith(FeedUiState(FeedFilter.Rides, PagedState(loading = false)))
        ScreenState.FeedError ->
            FeedWith(
                FeedUiState(
                    page = PagedState(loading = false, error = UiText.Res(R.string.error_offline))
                )
            )
        ScreenState.FeedLoadingMore ->
            FeedWith(
                FeedUiState(
                    FeedFilter.Journal,
                    PagedState(
                        items = listOf(feedJournal(1), feedJournal(2)),
                        nextCursor = "c1",
                        loading = false,
                        moreError = UiText.Res(R.string.error_offline),
                    ),
                )
            )
        ScreenState.JournalListEmpty -> JournalListWith(PagedState(loading = false), saved = false)
        ScreenState.JournalListError ->
            JournalListWith(
                PagedState(loading = false, error = UiText.Res(R.string.error_offline)),
                saved = false,
            )
        ScreenState.SavedEmpty -> JournalListWith(PagedState(loading = false), saved = true)
        ScreenState.JournalDraft ->
            JournalWith(
                JournalUiState.Loaded(
                    journalEntry(0, body = "Пока только заметки.\n\n1. Снять педали\n2. Смазать")
                        .let {
                            it.copy(
                                summary =
                                    it.summary.copy(
                                        status = JournalStatus.Draft,
                                        kind = "other",
                                        eventDate = null,
                                        mileageKm = null,
                                    ),
                                components = emptyList(),
                            )
                        },
                    saved = null,
                )
            )
        ScreenState.JournalNotFoundGuest ->
            JournalWith(
                JournalUiState.Failed(UiText.Res(R.string.error_not_found), notFound = true),
                onSignIn = {},
            )
        ScreenState.JournalSaved ->
            JournalWith(JournalUiState.Loaded(journalEntry(0), saved = true))
        ScreenState.CommentsReplying ->
            CommentsWith(
                discussion(
                    Composer(
                        text = "@neighbour Спасибо, учту!",
                        replyTo = discussionNodes[0].replies[0],
                    )
                )
            )
        ScreenState.CommentsEditing ->
            CommentsWith(
                discussion(
                    Composer(
                        text = "Мой ответ, исправленный",
                        editing = discussionNodes[0].replies[1],
                    )
                )
            )
        ScreenState.CommentsSendError ->
            CommentsWith(
                discussion(
                    Composer(
                        text = "Этот текст не пропал",
                        error = UiText.Res(R.string.error_offline),
                        key = "k",
                    )
                )
            )
        ScreenState.CommentsTooLong -> CommentsWith(discussion(Composer(text = "я".repeat(1_040))))
        ScreenState.CommentsEmpty ->
            CommentsWith(CommentsUiState(page = PagedState(loading = false)))
        ScreenState.CommentsBranch ->
            CommentsWith(
                discussion(
                    Composer(),
                    nodes = listOf(discussionNodes[0]),
                    focus = discussionNodes[0].replies[1].id,
                )
            )
        ScreenState.CommentsMoreError ->
            CommentsWith(
                CommentsUiState(
                    page =
                        PagedState(
                            items = discussionNodes,
                            nextCursor = "c1",
                            loading = false,
                            moreError = UiText.Res(R.string.error_offline),
                        )
                )
            )
        ScreenState.RidesEmpty -> RidesWith(RidesUiState(page = PagedState(loading = false)))
        ScreenState.RidesError ->
            RidesWith(
                RidesUiState(
                    page = PagedState(loading = false, error = UiText.Res(R.string.error_offline))
                )
            )
        ScreenState.RidesMoreError ->
            RidesWith(
                RidesUiState(
                    segment = RideSegment.Completed,
                    page =
                        PagedState(
                            items = rides(1, 3).map { RideRow.Public(it) },
                            nextCursor = "c1",
                            loading = false,
                            moreError = UiText.Res(R.string.error_offline),
                        ),
                )
            )
        ScreenState.RidesMine ->
            RidesWith(
                RidesUiState(
                    segment = RideSegment.Mine,
                    page =
                        PagedState(
                            items =
                                listOf(
                                    RideRow.Own(OwnRide(rideSummary(1), true, 200, 540)),
                                    RideRow.Own(
                                        OwnRide(
                                            rideSummary(2).copy(title = "Тренировка"),
                                            false,
                                            null,
                                            300,
                                        )
                                    ),
                                    RideRow.Own(
                                        OwnRide(
                                            rideSummary(3, RideStatus.Cancelled),
                                            true,
                                            null,
                                            0,
                                        )
                                    ),
                                ),
                            loading = false,
                        ),
                )
            )
        ScreenState.RidesMyPlans ->
            RidesWith(
                RidesUiState(
                    segment = RideSegment.MyPlans,
                    page =
                        PagedState(
                            items =
                                listOf(
                                    RideRow.Plan(plan(1, RideRole.Organizer)),
                                    RideRow.Plan(plan(2, RideRole.Accepted, changed = true)),
                                    RideRow.Plan(plan(3, RideRole.Maybe)),
                                ),
                            loading = false,
                        ),
                )
            )
        ScreenState.RideNotFoundGuest ->
            RideScreen(
                state = RideUiState.Failed(UiText.Res(R.string.error_not_found), notFound = true),
                actions = RideActions({}, {}, {}, { _, _ -> }),
                onRetry = {},
                onSignIn = {},
            )
        ScreenState.AnalysisLoading ->
            Box(Modifier.padding(Spacing.screen)) {
                AnalysisSection(AnalysisUiState.Loading, onRetry = {})
            }
        ScreenState.AnalysisFailed ->
            Box(Modifier.padding(Spacing.screen)) {
                AnalysisSection(
                    AnalysisUiState.Failed(UiText.Res(R.string.error_offline)),
                    onRetry = {},
                )
            }
        ScreenState.NotificationsEmpty -> InboxWith(PagedState(loading = false))
        ScreenState.NotificationsError ->
            InboxWith(PagedState(loading = false, error = UiText.Res(R.string.error_offline)))
        ScreenState.NotificationsAllRead ->
            InboxWith(
                PagedState(loading = false),
                InboxUi(filter = NotificationFilter(unreadOnly = true)),
            )
        ScreenState.NotificationsReadFailed ->
            InboxWith(
                PagedState(items = sampleInbox().take(3), loading = false),
                InboxUi(
                    filter = NotificationFilter(category = NotificationCategory.Discussions),
                    watermark = "mark-1",
                    message = UiText.Res(R.string.error_offline),
                ),
            )
        ScreenState.NotificationSettingsLoading ->
            NotificationSettingsScreen(
                NotificationSettingsUiState.Loading,
                NotificationSettingsActions(),
            )
        ScreenState.NotificationSettingsFailed ->
            NotificationSettingsScreen(
                NotificationSettingsUiState.Failed(UiText.Res(R.string.error_offline)),
                NotificationSettingsActions(),
            )
        ScreenState.NotificationSettingsPaused ->
            NotificationSettingsScreen(
                NotificationSettingsUiState.Loaded(
                    settings = busyNotificationSettings,
                    device = readyPhone,
                    phoneZone = ZoneId.of("Europe/Moscow"),
                    notice = UiText.Res(R.string.notif_settings_saved),
                ),
                NotificationSettingsActions(),
            )
        ScreenState.NotificationSettingsRefused ->
            NotificationSettingsScreen(
                NotificationSettingsUiState.Loaded(
                    settings = defaultNotificationSettings,
                    device = deniedPhone,
                    phoneZone = ZoneId.of("Europe/Moscow"),
                    problem =
                        SettingsProblem(
                            UiText.Res(R.string.error_server_plain),
                            NotificationSettingsChange(emailEnabled = true),
                        ),
                ),
                NotificationSettingsActions(),
            )
        ScreenState.IntentsEmpty ->
            IntentsScreen(
                IntentsUiState(IntentSegment.Mine, PagedState(loading = false)),
                IntentsActions(),
            )
        ScreenState.IntentUnavailable ->
            IntentScreen(IntentUiState.Unavailable, WriteState.Idle, IntentActions())
        ScreenState.IntentEditorSaving ->
            IntentEditorScreen(
                IntentEditorUiState.Editing(
                    form =
                        IntentForm(
                            readiness = IntentReadiness.Considering,
                            timeZone = java.time.ZoneId.of("Europe/Moscow"),
                            windows =
                                listOf(
                                    IntentWindowDraft(
                                        java.time.LocalDateTime.of(2026, 10, 10, 10, 0),
                                        java.time.LocalDateTime.of(2026, 10, 10, 13, 0),
                                    )
                                ),
                            areaLabel = "Парк Горького",
                            purpose = "leisure",
                            pace = null,
                            surface = null,
                            meetNewPeople = false,
                            visibility = IntentVisibility.Community,
                            allowSuggestions = true,
                        ),
                    problem = UiText.Res(R.string.intent_too_many),
                ),
                editing = false,
                actions = IntentEditorActions(),
            )
        ScreenState.NearbyFailed ->
            NearbyScreen(NearbyUiState.Failed(UiText.Res(R.string.error_offline)), NearbyActions())
        ScreenState.NearbyExpired ->
            NearbyScreen(
                NearbyUiState.Loaded(
                    settings =
                        activeNearbySettings.copy(
                            source = NearbySource.Device,
                            expired = true,
                            expiresAt = Instant.parse("2026-10-02T20:00:00Z"),
                        ),
                    problem = UiText.Res(R.string.nearby_location_unavailable),
                ),
                NearbyActions(onOpenOffers = {}),
            )
        ScreenState.NearbyOffersNoArea ->
            NearbyOffersScreen(
                NearbyOffersUiState.Loaded(NearbyOffers(NearbyOffersState.NoArea, emptyList())),
                NearbyOffersActions(),
            )
        ScreenState.NearbyOffersNone ->
            NearbyOffersScreen(
                NearbyOffersUiState.Loaded(NearbyOffers(NearbyOffersState.Ready, emptyList())),
                NearbyOffersActions(),
            )
        ScreenState.NotificationsMoreError ->
            InboxWith(
                PagedState(
                    items = sampleInbox().take(3),
                    nextCursor = "c1",
                    loading = false,
                    moreError = UiText.Res(R.string.error_offline),
                )
            )
        ScreenState.ComponentsEmpty ->
            CatalogWith(ComponentsUiState(page = PagedState(loading = false)))
        ScreenState.ComponentsNone ->
            CatalogWith(
                ComponentsUiState(
                    typed = "кассета 13",
                    query = ComponentQuery(text = "кассета 13"),
                    page = PagedState(loading = false),
                )
            )
        ScreenState.ComponentsError ->
            CatalogWith(
                ComponentsUiState(
                    page = PagedState(loading = false, error = UiText.Res(R.string.error_offline))
                )
            )
        ScreenState.ComponentsMoreError ->
            CatalogWith(
                ComponentsUiState(
                    filters = catalogFilters,
                    page =
                        PagedState(
                            items = componentModels(1, 3),
                            nextCursor = "c1",
                            loading = false,
                            moreError = UiText.Res(R.string.error_offline),
                        ),
                )
            )
        ScreenState.ComponentsFiltered ->
            CatalogWith(
                ComponentsUiState(
                    query = ComponentQuery(category = "Тормоза", sort = ComponentSort.Popular),
                    filters = catalogFilters,
                    page =
                        PagedState(
                            items =
                                listOf(
                                    componentModel(5, brand = "SRAM", category = "Тормоза"),
                                    componentModel(
                                        6,
                                        brand = "Shimano",
                                        category = "Тормоза",
                                        archived = true,
                                    ),
                                ),
                            loading = false,
                        ),
                )
            )
        ScreenState.ComponentArchived ->
            ComponentScreen(
                ComponentUiState.Loaded(componentModel(9, archived = true), photos = emptyList()),
                onBack = {},
            )
        ScreenState.ComponentNotFound ->
            ComponentScreen(
                ComponentUiState.Failed(UiText.Res(R.string.error_not_found), notFound = true),
                onBack = {},
            )
        ScreenState.ComponentFailed ->
            ComponentScreen(
                ComponentUiState.Failed(UiText.Res(R.string.error_offline)),
                onBack = {},
            )
        ScreenState.ComponentNoPhotos ->
            ComponentScreen(
                ComponentUiState.Loaded(componentModel(2), photos = emptyList()),
                onBack = {},
            )
        ScreenState.MarketEmpty -> MarketWith(MarketUiState(page = PagedState(loading = false)))
        ScreenState.MarketNone ->
            MarketWith(
                MarketUiState(
                    fields = MarketFields(text = "рама 61", city = "Тверь"),
                    query =
                        MarketQuery(
                            text = "рама 61",
                            category = ListingCategory.Bikes,
                            city = "Тверь",
                        ),
                    page = PagedState(loading = false),
                ),
                filtersOpen = true,
            )
        ScreenState.MarketError ->
            MarketWith(
                MarketUiState(
                    page = PagedState(loading = false, error = UiText.Res(R.string.error_offline))
                )
            )
        ScreenState.MarketMoreError ->
            MarketWith(
                MarketUiState(
                    page =
                        PagedState(
                            items = listingBriefs(1, 3),
                            nextCursor = "c1",
                            loading = false,
                            moreError = UiText.Res(R.string.error_offline),
                        )
                )
            )
        ScreenState.MarketFiltered ->
            MarketWith(
                MarketUiState(
                    fields = MarketFields(priceMin = "1000", priceMax = "5000"),
                    query =
                        MarketQuery(
                            category = ListingCategory.Components,
                            type = ListingType.Sale,
                            priceMin = 1000,
                            priceMax = 5000,
                            sort = ListingSort.PriceAsc,
                        ),
                    page =
                        PagedState(
                            items = listingBriefs(1, 3),
                            loading = false,
                        ),
                ),
                filtersOpen = true,
            )
        ScreenState.MarketSeller ->
            MarketWith(
                MarketUiState(
                    query = MarketQuery(seller = "test-rider"),
                    page = PagedState(items = listingBriefs(1, 3), loading = false),
                ),
                seller = "test-rider",
            )
        ScreenState.ListingSold ->
            ListingWith(ListingLoaded(listingModel(1, status = ListingStatus.Sold)))
        ScreenState.ListingExpired -> ListingWith(ListingLoaded(listingModel(1, expired = true)))
        ScreenState.ListingDraft ->
            ListingWith(
                ListingLoaded(listingModel(1, status = ListingStatus.Draft, isOwner = true))
            )
        ScreenState.ListingNotFound ->
            ListingWith(ListingUiState.Failed(UiText.Res(R.string.error_not_found), true))
        ScreenState.ListingNotFoundGuest ->
            ListingWith(
                ListingUiState.Failed(UiText.Res(R.string.error_not_found), true),
                onSignIn = {},
                signedIn = false,
            )
        ScreenState.ListingFailed ->
            ListingWith(ListingUiState.Failed(UiText.Res(R.string.error_offline)))
        ScreenState.ListingFree ->
            ListingWith(
                ListingLoaded(
                    listingModel(
                        3,
                        condition = ListingCondition.New,
                        brief =
                            listingBrief(
                                3,
                                title = "Отдам детское кресло",
                                price = 0.0,
                                type = "free",
                                category = "accessories",
                            ),
                    )
                )
            )
        ScreenState.ListingNoContact ->
            ListingWith(ListingLoaded(listingModel(1, hasContact = false)))
        ScreenState.ListingGuest -> ListingWith(ListingLoaded(listingModel(1)), signedIn = false)
        ScreenState.ListingContactUnverified ->
            ListingWith(
                ListingLoaded(
                    listingModel(1),
                    contact = ContactState.Failed(UiText.Res(R.string.listing_contact_unverified)),
                )
            )
        ScreenState.ListingContactLimited ->
            ListingWith(
                ListingLoaded(
                    listingModel(1),
                    contact =
                        ContactState.Failed(
                            DataError.RateLimited(retryAfterSeconds = 600).toUiText()
                        ),
                )
            )
        ScreenState.ListingContactShown ->
            ListingWith(
                ListingLoaded(
                    listingModel(1),
                    contact = ContactState.Shown("+7 900 111-22-33, Telegram @seller"),
                )
            )
        ScreenState.SavedMarketEmpty ->
            SavedMarketScreen(page = PagedState(loading = false), onBack = {})
        ScreenState.SavedMarketError ->
            SavedMarketScreen(
                page = PagedState(loading = false, error = UiText.Res(R.string.error_offline)),
                onBack = {},
            )
        ScreenState.ChatConnecting -> ChatStatusWith(ChatConnection.Connecting)
        ScreenState.ChatFailed ->
            ChatStatusWith(
                ChatConnection.Failed(ChatFailure.Offline, UiText.Res(R.string.error_offline))
            )
        ScreenState.ChatEmailUnconfirmed ->
            ChatStatusWith(
                ChatConnection.Failed(
                    ChatFailure.EmailUnconfirmed,
                    UiText.Res(R.string.chat_email_unconfirmed),
                )
            )
        ScreenState.ChatUnavailable ->
            ChatStatusWith(
                ChatConnection.Failed(
                    ChatFailure.Unavailable,
                    UiText.Res(R.string.chat_unavailable),
                )
            )
        ScreenState.ChatGuest -> GuestMessages()
        ScreenState.ChatNewPeople ->
            NewConversationWith(NewConversationUiState(people = chatPeople, loading = false))
        ScreenState.ChatNewSearch ->
            NewConversationWith(
                NewConversationUiState(
                    typed = "Райдер 1",
                    searching = true,
                    people = chatPeople.take(1),
                    loading = false,
                )
            )
        ScreenState.ChatNewGroup ->
            NewConversationWith(
                NewConversationUiState(
                    people = chatPeople,
                    loading = false,
                    group = true,
                    selected = chatPeople.take(2),
                    groupName = "Субботний заезд",
                )
            )
        ScreenState.ChatNewEmpty -> NewConversationWith(NewConversationUiState(loading = false))
        ScreenState.ChatNewError ->
            NewConversationWith(
                NewConversationUiState(loading = false, error = UiText.Res(R.string.error_offline))
            )
        ScreenState.ChatNewRefused ->
            NewConversationWith(
                NewConversationUiState(
                    people = chatPeople,
                    loading = false,
                    openError = UiText.Res(R.string.chat_cannot_write),
                )
            )
        ScreenState.BikeDetailBare ->
            BikeDetailScreen(
                BikeDetailUiState.Loaded(bareBike),
                showBack = true,
                onBack = {},
                onRetry = {},
            )
    }
}

private val discussionNodes: List<CommentNode> =
    sampleDiscussion().threads.map {
        CommentNode(it.root, it.replies, repliesComplete = it.root.replyCount <= it.replies.size)
    }

private fun discussion(
    composer: Composer,
    nodes: List<CommentNode> = discussionNodes,
    focus: String? = null,
) =
    CommentsUiState(
        page = PagedState(items = nodes, loading = false),
        focus = focus,
        composer = composer,
    )

@Composable
private fun CommentsWith(state: CommentsUiState) =
    CommentsScreen(
        state = state,
        title = "Городской Трэвел",
        meId = PreviewData.rider.id,
        signedIn = true,
        actions = CommentsActions(),
    )

private val chatPeople = people(0, 4).map { it.person }

private val catalogFilters =
    ComponentFilters(listOf("Трансмиссия", "Тормоза"), listOf("Shimano", "SRAM"))

@Composable
private fun MarketWith(
    state: MarketUiState,
    seller: String? = null,
    filtersOpen: Boolean = false,
) = MarketScreen(state = state, seller = seller, onBack = {}, filtersOpen = filtersOpen)

private fun ListingLoaded(
    listing: Listing,
    contact: ContactState = ContactState.Hidden,
) =
    ListingUiState.Loaded(
        listing = listing,
        saved = listing.saved,
        others = SellerListings(listingBriefs(11, 2), total = 6),
        contact = contact,
    )

@Composable
private fun ListingWith(
    state: ListingUiState,
    onSignIn: (() -> Unit)? = null,
    signedIn: Boolean = true,
) =
    ListingScreen(
        state = state,
        actions = ListingActions(onBack = {}),
        onSignIn = onSignIn,
        signedIn = signedIn,
    )

@Composable
private fun CatalogWith(state: ComponentsUiState) = ComponentsScreen(state = state, onBack = {})

@Composable
private fun ChatStatusWith(connection: ChatConnection) =
    ChatStatusScreen(
        connection = connection,
        title = "Сообщения",
        onBack = null,
        onRetry = {},
        onOpenSite = {},
    )

@Composable
private fun NewConversationWith(state: NewConversationUiState) =
    NewConversationScreen(
        state = state,
        onBack = {},
        onQuery = {},
        onClear = {},
        onRetry = {},
        onGroup = {},
        onGroupName = {},
        onPerson = {},
        onCreate = {},
    )

@Composable
private fun InboxWith(state: PagedState<AppNotification>, ui: InboxUi = InboxUi()) =
    NotificationsScreen(
        state = state,
        ui = ui,
        opens = { true },
        onBack = {},
        onRefresh = {},
        onRetry = {},
        onLoadMore = {},
        onToggleUnread = {},
        onCategory = {},
        onReadAll = {},
        onMarkRead = {},
        onDismissMessage = {},
        onOpen = {},
    )

@Composable
private fun RidesWith(state: RidesUiState) =
    RidesScreen(
        state = state,
        personal = true,
        onSegment = {},
        onRefresh = {},
        onRetry = {},
        onLoadMore = {},
        onOpen = {},
    )

@Composable
private fun FeedWith(state: FeedUiState) =
    FeedScreen(
        state = state,
        actions = FeedActions({}, {}, {}, {}, {}),
        onFilter = {},
        onRefresh = {},
        onRetry = {},
        onLoadMore = {},
    )

@Composable
private fun JournalListWith(page: PagedState<JournalSummary>, saved: Boolean) =
    JournalListScreen(
        title = if (saved) "Сохранённое" else "Журнал",
        subtitle = if (saved) null else "Городской Трэвел",
        saved = saved,
        state = page,
        onBack = {},
        onRefresh = {},
        onRetry = {},
        onLoadMore = {},
        onOpen = {},
    )

@Composable
private fun JournalWith(state: JournalUiState, onSignIn: (() -> Unit)? = null) =
    JournalScreen(
        state = state,
        actions = JournalActions({}, {}, {}),
        onRetry = {},
        onSignIn = onSignIn,
    )

@Composable
private fun PersonWith(state: PersonUiState) =
    PersonScreen(
        state = state,
        actions = PersonActions({}, {}, {}, {}, {}),
        onRetry = {},
        onLoadMore = {},
        onToggleFollow = {},
    )

@Composable
private fun PeopleWith(state: PeopleListUiState) =
    PeopleListScreen(
        kind = PeopleListKind.Following,
        state = state,
        onBack = {},
        onRetry = {},
        onLoadMore = {},
        onOpenPerson = {},
    )

@Composable
private fun SearchWith(state: SearchUiState) =
    SearchScreen(
        state = state,
        onBack = {},
        onTab = {},
        onText = {},
        onClear = {},
        onCategory = {},
        onSuspension = {},
        onElectric = {},
        onFatbike = {},
        onRetry = {},
        onLoadMore = {},
        onOpenBike = {},
        onOpenPerson = {},
    )

@Composable
private fun DevicesWith(state: DevicesUiState) =
    DevicesScreen(
        state = state,
        now = Instant.parse("2026-10-03T20:00:00Z"),
        onBack = {},
        onRetry = {},
        onEnd = {},
        onConfirmEnd = {},
        onDismissQuestion = {},
    )

@Composable
private fun BikesWith(state: BikesUiState) =
    BikesScreen(
        state = state,
        onScope = {},
        onRefresh = {},
        onRetry = {},
        onLoadMore = {},
        onOpen = {},
    )
