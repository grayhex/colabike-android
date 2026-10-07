package ru.colabike.app

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.unit.Density
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import java.io.File
import java.time.Instant
import java.time.LocalDate
import ru.colabike.app.about.BuildInfo
import ru.colabike.app.about.LocalBuildInfo
import ru.colabike.app.bikes.BikeForm
import ru.colabike.app.bikes.BikePhotosScreen
import ru.colabike.app.bikes.BikePhotosUiState
import ru.colabike.app.bikes.BikeWizardActions
import ru.colabike.app.bikes.BikeWizardScreen
import ru.colabike.app.bikes.PendingPhoto
import ru.colabike.app.bikes.PendingState
import ru.colabike.app.bikes.PhotosActions
import ru.colabike.app.bikes.SearchFailure
import ru.colabike.app.bikes.SearchPhase
import ru.colabike.app.bikes.WizardBuild
import ru.colabike.app.bikes.WizardOffer
import ru.colabike.app.bikes.WizardPart
import ru.colabike.app.bikes.WizardQuestion
import ru.colabike.app.bikes.WizardStep
import ru.colabike.app.bikes.WizardUiState
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
import ru.colabike.core.designsystem.theme.ColaCanvas
import ru.colabike.core.model.AccountDeletion
import ru.colabike.core.model.AgreementChange
import ru.colabike.core.model.AppNotice
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeProblem
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.BuildQuery
import ru.colabike.core.model.Compatibility
import ru.colabike.core.model.Feature
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.IntentReadiness
import ru.colabike.core.model.IntentStatus
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalStatus
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
import ru.colabike.core.model.ParticipationResponse
import ru.colabike.core.model.ParticipationState
import ru.colabike.core.model.Person
import ru.colabike.core.model.PersonSummary
import ru.colabike.core.model.Photo
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.RequestedDateStatus
import ru.colabike.core.model.ResolveRequest
import ru.colabike.core.model.RideId
import ru.colabike.core.model.ServiceLinks
import ru.colabike.core.model.SourceKind
import ru.colabike.core.model.SourcesChecked
import ru.colabike.core.model.UpdateMode
import ru.colabike.core.model.UpdateState
import ru.colabike.core.model.UserId
import ru.colabike.core.model.toDraft

/** Every screen of the app, as AGENTS.md requires it in screenshots. */
enum class Screen(val file: String) {
    Login("login"),
    Bikes("bikes"),
    BikeDetail("bike_detail"),

    /**
     * The same page as the owner has it (the photo's actions, the pencil, "+ Запись", an empty
     * discussion with its box), the build opened in place, and the discussion opened in place with
     * the replies of its first comment (docs/design/reference/2026-10-07-bicycle-detail-ux).
     */
    BikeDetailOwner("bike_detail_owner"),
    BikeDetailBuild("bike_detail_build"),
    BikeDetailComments("bike_detail_comments"),
    Profile("profile"),

    /** What a guest sees on the profile tab, and the rest of the profile below the fold. */
    ProfileGuest("profile_guest"),
    ProfileMore("profile_more"),
    Devices("devices"),

    /**
     * Profile → Delete account: for an account with a password, for one made through Yandex ID, and
     * for an administrator who has to hand the rights over first.
     */
    DeleteAccount("delete_account"),
    DeleteAccountYandex("delete_account_yandex"),
    DeleteAccountAdmin("delete_account_admin"),

    /**
     * Reporting and blocking (docs/adr/0021): the list of the blocked, a person the viewer blocked,
     * and the question of a report opened from a bike.
     */
    Blocked("blocked"),
    PersonBlocked("person_blocked"),
    ReportDialog("report_dialog"),
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

    /**
     * The Rides section as it opens (what took place), a plan's page, and a completed ride's page.
     */
    Rides("rides"),
    RidePlan("ride_plan"),
    RideCompleted("ride_completed"),

    /**
     * The person's part in a ride's date, opened from a notification: an invitation, terms that
     * changed after an answer, and a cancelled date.
     */
    Participation("participation"),
    ParticipationChanged("participation_changed"),
    ParticipationCancelled("participation_cancelled"),

    /**
     * "I want to ride": the community's list, the person's own, someone else's page, an own page
     * with its actions, and the form with its findings shown.
     */
    Intents("intents"),
    IntentsMine("intents_mine"),
    Intent("intent"),
    IntentOwn("intent_own"),
    IntentEditor("intent_editor"),

    /** The form of one's own bike, with its findings shown, and the question before a deletion. */
    BikeEditorEdit("bike_editor_edit"),
    BikeEditorProblems("bike_editor_problems"),
    BikeEditorDelete("bike_editor_delete"),

    /**
     * The wizard of a new bike (docs/adr/0028): the search, a search on its way, a failure, the
     * variants, the build to check, the details with their findings, and the question before a page
     * of another model is used.
     */
    BikeWizardSearch("bike_wizard_search"),
    BikeWizardResolving("bike_wizard_resolving"),
    BikeWizardFailed("bike_wizard_failed"),
    BikeWizardOffer("bike_wizard_offer"),
    BikeWizardBuild("bike_wizard_build"),
    BikeWizardDetails("bike_wizard_details"),
    BikeWizardProblems("bike_wizard_problems"),
    BikeWizardIdentity("bike_wizard_identity"),

    /**
     * The build of one's own bike: the parts, the order of groups; a part's form, with findings.
     */
    BikeParts("bike_parts"),
    BikePartEditor("bike_part_editor"),
    BikePartProblems("bike_part_problems"),

    /** A journal entry: the empty form, its findings, a form of one's own entry, the question. */
    JournalOwn("journal_own"),
    JournalPhotos("journal_photos"),
    JournalEditorNew("journal_editor_new"),
    JournalEditorProblems("journal_editor_problems"),
    JournalEditorEdit("journal_editor_edit"),
    JournalEditorDelete("journal_editor_delete"),

    /**
     * The pictures of one's own bike: the list, pictures on their way, the question before a
     * removal.
     */
    BikePhotos("bike_photos"),
    BikePhotosPending("bike_photos_pending"),
    BikePhotosDelete("bike_photos_delete"),

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
    reveal(onNodeWithTag("bike:journal-all")).performClick()
}

/** From the list to the first bike's page and from there to its author. */
private fun ComposeContentTestRule.openAuthor() {
    onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
    reveal(onNodeWithTag("bike:author")).performClick()
}

/** What the viewer is to a person they blocked. */
private val blockedByMe =
    Relationship(
        isSelf = false,
        following = false,
        followedBy = false,
        friends = false,
        blockedByMe = true,
    )

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

private val ReferenceBuild =
    BuildInfo(versionName = "1.2.3", versionCode = 4, contractVersion = "1.0.0 (abcd1234)")

@OptIn(ExperimentalCoilApi::class)
private val photos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * Scrolls to [node] once the screen has stopped changing, and lets the scroll come to rest. The
 * distance is worked out from the layout of the moment: findings of a form or a card that arrives a
 * frame later move the node by a line, and the same screen comes out scrolled by a different amount
 * from one run to the next.
 */
private fun ComposeContentTestRule.reveal(
    node: SemanticsNodeInteraction
): SemanticsNodeInteraction {
    settle()
    return node.performScrollTo().also { settle() }
}

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
            // Not the build's own numbers: raising the version must not change a reference picture.
            LocalBuildInfo provides ReferenceBuild,
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
                    Screen.Participation,
                    Screen.ParticipationChanged,
                    Screen.ParticipationCancelled ->
                        AppShell(
                            FakeDependencies(
                                participation =
                                    FakeParticipation(
                                        when (screen) {
                                            Screen.ParticipationChanged ->
                                                sampleParticipation(
                                                    state = ParticipationState.Reconfirm,
                                                    previous = ParticipationResponse.Accepted,
                                                    changed = true,
                                                    revision = 4,
                                                    changes =
                                                        setOf(
                                                            AgreementChange.Start,
                                                            AgreementChange.Place,
                                                        ),
                                                )
                                            Screen.ParticipationCancelled ->
                                                sampleParticipation(
                                                    requested = RequestedDateStatus.Cancelled,
                                                    allowed = setOf(ParticipationResponse.Declined),
                                                    meetingHidden = true,
                                                    state = ParticipationState.None,
                                                    scheduledAt =
                                                        Instant.parse("2026-10-17T07:00:00Z"),
                                                )
                                            else -> sampleParticipation()
                                        }
                                    ),
                                pending =
                                    FakePending().apply {
                                        offer(
                                            Destination.Participation(
                                                "b2000000-0000-4000-8000-0000000000b2",
                                                "2026-10-10T07:00:00Z",
                                            )
                                        )
                                    },
                            )
                        )
                    Screen.Intents,
                    Screen.IntentsMine,
                    Screen.Intent,
                    Screen.IntentOwn,
                    Screen.IntentEditor ->
                        AppShell(
                            FakeDependencies(
                                intents =
                                    FakeIntents(
                                        community =
                                            listOf(
                                                sampleIntent(1),
                                                sampleIntent(
                                                    2,
                                                    area = "Лосиный остров",
                                                    readiness = IntentReadiness.Considering,
                                                ),
                                            ),
                                        mine =
                                            listOf(
                                                sampleIntent(
                                                    3,
                                                    own = true,
                                                    visibility = IntentVisibility.Private,
                                                ),
                                                sampleIntent(
                                                    4,
                                                    own = true,
                                                    status = IntentStatus.Cancelled,
                                                    area = "Измайловский парк",
                                                ),
                                            ),
                                    )
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
                    Screen.DeleteAccountYandex ->
                        AppShell(
                            FakeDependencies(
                                accountDeletion =
                                    FakeAccountDeletion(
                                        AccountDeletion(AccountDeletion.Method.Yandex, true, null)
                                    )
                            )
                        )
                    Screen.DeleteAccountAdmin ->
                        AppShell(
                            FakeDependencies(
                                accountDeletion =
                                    FakeAccountDeletion(
                                        AccountDeletion(null, false, AccountDeletion.Reason.Admin)
                                    )
                            )
                        )
                    Screen.Blocked ->
                        AppShell(
                            FakeDependencies(
                                safety =
                                    FakeSafety(
                                        listOf(
                                            PersonSummary(PreviewData.rider, blockedByMe),
                                            PersonSummary(
                                                Person(
                                                    UserId("u7"),
                                                    "anna-gravel",
                                                    "Анна Гравийная",
                                                    null,
                                                ),
                                                blockedByMe,
                                            ),
                                        )
                                    )
                            )
                        )
                    Screen.PersonBlocked ->
                        AppShell(
                            FakeDependencies(
                                bikes = FakeBikes(mapOf(null to Page(bikes(0, 6), "c1"))),
                                people =
                                    FakePeople(
                                        profiles =
                                            mapOf(
                                                PreviewData.rider.id.value to
                                                    profileOf(PreviewData.rider, blockedByMe)
                                            )
                                    ),
                            )
                        )
                    Screen.ReportDialog ->
                        AppShell(
                            FakeDependencies(
                                bikes = FakeBikes(mapOf(null to Page(bikes(0, 6), "c1"))),
                                // Somebody else: the author of a bike reports nothing of their own.
                                auth =
                                    FakeAuth(
                                        AuthState.SignedIn(
                                            account.copy(
                                                id = UserId("viewer-1"),
                                                username = "viewer",
                                            )
                                        )
                                    ),
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
                    Screen.BikePhotosPending ->
                        // The shell paints the canvas under a screen; here nothing else does.
                        ColaCanvas {
                            BikePhotosScreen(
                                BikePhotosUiState.Ready(
                                    bike = ownBike,
                                    pending =
                                        listOf(
                                            PendingPhoto(1, PendingState.Sending(0.4f)),
                                            PendingPhoto(2, PendingState.Waiting),
                                            PendingPhoto(
                                                3,
                                                PendingState.Failed(
                                                    UiText.Res(R.string.photos_import_too_small),
                                                    retry = false,
                                                ),
                                            ),
                                            PendingPhoto(
                                                4,
                                                PendingState.Failed(
                                                    UiText.Res(R.string.error_offline),
                                                    retry = true,
                                                ),
                                            ),
                                        ),
                                ),
                                PhotosActions(),
                            )
                        }
                    Screen.BikeDetailOwner,
                    Screen.JournalOwn,
                    Screen.JournalPhotos,
                    Screen.JournalEditorNew,
                    Screen.JournalEditorProblems,
                    Screen.JournalEditorEdit,
                    Screen.JournalEditorDelete ->
                        AppShell(
                            FakeDependencies(
                                bikes =
                                    FakeBikes(mapOf(null to Page(listOf(ownBike.summary), null)))
                                        .apply { details = mapOf("b-own" to ownBike) },
                                journal =
                                    FakeJournal(
                                        pages = mapOf(null to Page(listOf(ownEntry.summary), null)),
                                        entries = mapOf("j-own" to ownEntry),
                                    ),
                            )
                        )
                    Screen.BikeWizardSearch,
                    Screen.BikeWizardResolving,
                    Screen.BikeWizardFailed,
                    Screen.BikeWizardOffer,
                    Screen.BikeWizardBuild,
                    Screen.BikeWizardDetails,
                    Screen.BikeWizardProblems,
                    Screen.BikeWizardIdentity ->
                        // The shell paints the canvas under a screen; here nothing else does.
                        ColaCanvas {
                            BikeWizardScreen(
                                state = wizardState(screen),
                                actions = BikeWizardActions(),
                                catalog = SiteCatalogFixtures.catalog,
                            )
                        }
                    Screen.BikeEditorEdit,
                    Screen.BikeEditorProblems,
                    Screen.BikeEditorDelete,
                    Screen.BikePhotos,
                    Screen.BikePhotosDelete,
                    Screen.BikeParts,
                    Screen.BikePartEditor,
                    Screen.BikePartProblems ->
                        AppShell(
                            FakeDependencies(
                                bikes =
                                    FakeBikes(mapOf(null to Page(listOf(ownBike.summary), null)))
                                        .apply { details = mapOf("b-own" to ownBike) }
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
        Screen.BikeDetailOwner ->
            onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        Screen.BikeDetailBuild -> {
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
            // The build is shut when the page opens; the title row opens it in place.
            reveal(onNodeWithText("Комплектация")).performClick()
            reveal(onNodeWithText("Shimano Deore 10-speed"))
        }
        Screen.BikeDetailComments -> {
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
            reveal(onNodeWithTag("discussion:all")).performClick()
            reveal(onNodeWithText("3 ответа")).performClick()
            reveal(onNodeWithText("Согласен"))
        }
        Screen.Profile,
        Screen.ProfileGuest -> section("Профиль").performClick()
        Screen.ProfileMore -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("О приложении"))
        }
        Screen.AboutConfigured -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("О приложении")).performClick()
        }
        Screen.Devices -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("Устройства и входы")).performClick()
        }
        Screen.DeleteAccount,
        Screen.DeleteAccountYandex,
        Screen.DeleteAccountAdmin -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("Удалить аккаунт")).performClick()
        }
        Screen.Blocked -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("Заблокированные")).performClick()
        }
        Screen.PersonBlocked -> openAuthor()
        Screen.ReportDialog -> {
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
            onNodeWithContentDescription("Ещё").performClick()
            onNodeWithText("Пожаловаться").performClick()
        }
        Screen.About -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("О приложении")).performClick()
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
            // The full screen of a discussion is reached from an entry of the journal; the page of
            // a bike has its discussion in place.
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
            reveal(onNodeWithTag("bike:journal-entry")).performClick()
            reveal(onNodeWithText("Комментарии")).performClick()
        }
        Screen.Rides -> section("Покатушки").performClick()
        Screen.RidePlan -> {
            section("Покатушки").performClick()
            choose("rides:segment", "Ближайшие")
            onNodeWithContentDescription("Воскресный выезд за город", substring = true)
                .performClick()
        }
        Screen.RideCompleted -> openCompletedRide()
        Screen.RideCompletedAnalysis -> {
            openCompletedRide()
            // The last chart (heart rate, with its gap) brings the others into the picture too.
            reveal(onNodeWithContentDescription("Пульс: от", substring = true))
        }
        Screen.RideMap -> {
            openCompletedRide()
            // On a wide window the map is already beside the page; a phone opens it by a button.
            if (onAllNodesWithText("Открыть карту").fetchSemanticsNodes().isNotEmpty()) {
                reveal(onNodeWithText("Открыть карту")).performClick()
            }
        }
        Screen.Notifications ->
            onNode(hasContentDescription("Уведомления", substring = true)).performClick()
        Screen.NotificationSettings,
        Screen.NotificationSettingsBusy,
        Screen.NotificationSettingsPhone -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("Что, когда и от кого присылать")).performClick()
            when (screen) {
                Screen.NotificationSettingsBusy ->
                    reveal(onNodeWithTag("notif-settings:mute:ride-1"))
                Screen.NotificationSettingsPhone -> reveal(onNodeWithTag("notif-settings:os-off"))
                else -> Unit
            }
        }
        Screen.BikeEditorEdit -> {
            onNodeWithContentDescription("Мой трейл", substring = true).performClick()
            onNodeWithTag("bike:edit").performClick()
        }
        Screen.BikeEditorProblems -> {
            onNodeWithContentDescription("Мой трейл", substring = true).performClick()
            onNodeWithTag("bike:edit").performClick()
            // The findings are shown once the person tried to save a form with no name.
            reveal(onNodeWithTag("bike-editor:name")).performTextClearance()
            reveal(onNodeWithTag("bike-editor:save")).performClick()
            // The findings pushed the button down: bring it, and what is above it, back.
            reveal(onNodeWithTag("bike-editor:save"))
        }
        Screen.BikeEditorDelete -> {
            onNodeWithContentDescription("Мой трейл", substring = true).performClick()
            onNodeWithTag("bike:edit").performClick()
            reveal(onNodeWithTag("bike-editor:delete")).performClick()
        }
        Screen.JournalEditorNew,
        Screen.JournalEditorProblems -> {
            onNodeWithContentDescription("Мой трейл", substring = true).performClick()
            reveal(onNodeWithTag("bike:journal-new")).performClick()
            if (screen == Screen.JournalEditorProblems) {
                reveal(onNodeWithTag("journal-editor:status:published")).performClick()
                // The findings are shown once the person tried to save, above the button.
                reveal(onNodeWithTag("journal-editor:save")).performClick()
                reveal(onNodeWithTag("journal-editor:save"))
            }
        }
        Screen.JournalOwn,
        Screen.JournalPhotos,
        Screen.JournalEditorEdit,
        Screen.JournalEditorDelete -> {
            onNodeWithContentDescription("Мой трейл", substring = true).performClick()
            reveal(onNodeWithTag("bike:journal-all")).performClick()
            onNodeWithTag("journal:j-own").performClick()
            if (screen == Screen.JournalPhotos) {
                reveal(onNodeWithTag("journal:photos")).performClick()
            } else if (screen != Screen.JournalOwn) {
                onNodeWithTag("journal:edit").performClick()
                if (screen == Screen.JournalEditorDelete) {
                    reveal(onNodeWithTag("journal-editor:delete")).performClick()
                }
            }
        }
        Screen.BikePhotos,
        Screen.BikePhotosDelete -> {
            onNodeWithContentDescription("Мой трейл", substring = true).performClick()
            reveal(onNodeWithTag("bike:photos")).performClick()
            if (screen == Screen.BikePhotosDelete) {
                reveal(onNodeWithTag("photos:delete:p2")).performClick()
            }
        }
        Screen.BikeParts,
        Screen.BikePartEditor,
        Screen.BikePartProblems -> {
            onNodeWithContentDescription("Мой трейл", substring = true).performClick()
            reveal(onNodeWithTag("bike:parts")).performClick()
            when (screen) {
                Screen.BikePartEditor -> reveal(onNodeWithTag("parts:part:c2")).performClick()
                Screen.BikePartProblems -> {
                    onNodeWithTag("parts:add").performClick()
                    reveal(onNodeWithTag("part-editor:save")).performClick()
                    reveal(onNodeWithTag("part-editor:save"))
                }
                else -> Unit
            }
        }
        Screen.Intents,
        Screen.IntentsMine,
        Screen.Intent,
        Screen.IntentOwn,
        Screen.IntentEditor -> {
            section("Покатушки").performClick()
            onNodeWithTag("rides:intents").performClick()
            when (screen) {
                Screen.IntentsMine -> onNodeWithTag("intents:segment:mine").performClick()
                Screen.Intent ->
                    onNodeWithTag("intent:b3000000-0000-4000-8000-000000000001").performClick()
                Screen.IntentOwn -> {
                    onNodeWithTag("intents:segment:mine").performClick()
                    onNodeWithTag("intent:b3000000-0000-4000-8000-000000000003").performClick()
                }
                Screen.IntentEditor -> {
                    onNodeWithTag("intents:create").performClick()
                    // The findings are shown once the person tried to save without an area.
                    // The main action is pinned to the bottom edge: nothing to scroll to.
                    onNodeWithTag("intent-editor:save").performClick()
                }
                else -> Unit
            }
        }
        Screen.Nearby,
        Screen.NearbyDraft,
        Screen.NearbyOffers -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("Что, когда и от кого присылать")).performClick()
            reveal(onNodeWithTag("notif-settings:nearby")).performClick()
            when (screen) {
                Screen.NearbyDraft -> {
                    reveal(onNodeWithTag("nearby:locate")).performClick()
                    onNodeWithTag("nearby:locate-allow").performClick()
                    reveal(onNodeWithTag("nearby:confirm"))
                }
                Screen.NearbyOffers -> reveal(onNodeWithTag("nearby:offers")).performClick()
                else -> Unit
            }
        }
        Screen.Components -> onNodeWithContentDescription("Каталог компонентов").performClick()
        Screen.Component,
        Screen.ComponentCredits -> {
            onNodeWithContentDescription("Каталог компонентов").performClick()
            onNodeWithTag("component:c1").performClick()
            if (screen == Screen.ComponentCredits) {
                settle()
                onNodeWithTag("component:page").performScrollToNode(hasText("Фотографии"))
                settle()
            }
        }
        Screen.Market -> section("Рынок").performClick()
        Screen.MarketFilters -> {
            section("Рынок").performClick()
            onNodeWithTag("market:filters").performClick()
        }
        Screen.Listing,
        Screen.ListingContact -> {
            section("Рынок").performClick()
            onNodeWithTag("listing:l1").performClick()
            if (screen == Screen.ListingContact) {
                reveal(onNodeWithTag("listing:contact_show")).performClick()
            }
        }
        Screen.SavedMarket -> {
            section("Профиль").performClick()
            reveal(onNodeWithText("Объявления")).performClick()
        }
        Screen.Messages,
        Screen.MessagesGuest -> openChats()
        Screen.Conversation -> {
            openChats()
            // Not a touch: on a wide window the row stays in the picture, and a pressed or
            // focused row is drawn at a moment of the platform's own clock.
            onNodeWithTag("chat:open:dm-1").performSemanticsAction(SemanticsActions.OnClick)
        }
        Screen.NewConversation -> {
            openChats()
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
    settle()
    captureWhenDrawn("src/test/screenshots/${screen.file}_${window}_${look.file}.png")
}

private val wizardQuery = BuildQuery("Giant", "Contend", "AR 1", 2024)

private val wizardParts =
    listOf(
        FakeBikeWizard.part("Рама", "ALUXX aluminium", "frame"),
        FakeBikeWizard.part("Вилка", "Giant Contend carbon", "frame"),
        FakeBikeWizard.part("Групсет", "Shimano 105", "drivetrain"),
        FakeBikeWizard.part("Кассета", "Shimano 11-34", "drivetrain"),
        FakeBikeWizard.part("Тормоза", "Shimano RS505", "brakes"),
        FakeBikeWizard.part("Покрышки", "Giant Gavia 28", "wheels"),
        FakeBikeWizard.part("Седло", "Giant Contact", "cockpit"),
        FakeBikeWizard.part("Флягодержатель", "Giant Gateway", "", "accessories"),
    )

/** What the wizard holds on each of its screens: built by hand, so a picture never waits. */
private fun wizardState(screen: Screen): WizardUiState {
    val search = WizardUiState(searchText = wizardQuery.line)
    val build = FakeBikeWizard.build(wizardQuery, parts = wizardParts)
    val parts =
        build.parts.mapIndexed { index, part ->
            WizardPart(
                key = index + 1L,
                section = part.section,
                category = part.category,
                name = part.name,
                notes = part.notes,
                groupId = part.groupId,
                price = if (index == 2) "28 900" else "",
            )
        }
    val building =
        search.copy(
            step = WizardStep.Build,
            query = wizardQuery,
            found = WizardBuild(build, "pv-1", false),
            parts = parts,
            form = BikeForm(brand = "Giant", model = "Contend", trim = "AR 1", year = "2024"),
        )
    return when (screen) {
        Screen.BikeWizardResolving ->
            search.copy(
                phase = SearchPhase.Resolving(ResolveRequest.Search(wizardQuery)),
                garageName = "Шоссейник",
            )
        Screen.BikeWizardFailed ->
            search.copy(phase = SearchPhase.Failed(SearchFailure.Unavailable("timeout", true)))
        Screen.BikeWizardOffer ->
            search.copy(
                offer =
                    WizardOffer(
                        wizardQuery,
                        listOf(
                            FakeBikeWizard.candidate("c-1", "Giant Contend AR 1 2024", year = 2024),
                            FakeBikeWizard.candidate(
                                "c-2",
                                "Giant Contend AR 1 2023",
                                year = 2023,
                                kind = SourceKind.Manufacturer,
                            ),
                            FakeBikeWizard.candidate(null, "Страница магазина без выбора"),
                        ),
                        SourcesChecked(asked = 3, answered = 2, complete = false),
                    )
            )
        Screen.BikeWizardBuild -> building
        Screen.BikeWizardDetails ->
            building.copy(
                step = WizardStep.Details,
                form = building.form.copy(color = "", weight = ""),
            )
        Screen.BikeWizardProblems ->
            building.copy(
                step = WizardStep.Details,
                problems = listOf(BikeProblem.NoCategory, BikeProblem.NoYear),
                form = building.form.copy(year = ""),
                problem = UiText.Res(R.string.bike_needs_email),
            )
        Screen.BikeWizardIdentity ->
            building.copy(
                question =
                    WizardQuestion.Identity(
                        FakeBikeWizard.build(
                            wizardQuery,
                            name = "Giant Contend AR 2",
                            sourceYear = 2023,
                            identityMismatch = true,
                        ),
                        asked = 2024,
                        onSave = false,
                    )
            )
        else -> search
    }
}

/** The viewer's own bike, for the screens that change or delete one. */
private val ownEntry =
    journalEntry(1).let {
        it.copy(
            summary =
                it.summary.copy(
                    id = JournalId("j-own"),
                    kind = "build",
                    title = "Новая цепь и кассета",
                    status = JournalStatus.Published,
                    isPublic = true,
                    eventDate = LocalDate.parse("2026-09-14"),
                    mileageKm = 4200,
                    bike = BikeRef(BikeId("b-own"), "Мой трейл"),
                ),
            photos = (1..3).map { n -> Photo("jq$n", "https://example.test/entry-$n.jpg") },
            version = "\"e0\"",
            installationResult = "modified",
        )
    }

private val ownBike =
    PreviewData.bikeDetail
        .fromDraft(
            BikeId("b-own"),
            PreviewData.bikeDetail.toDraft().copy(name = "Мой трейл"),
            "\"v1\"",
        )
        .let { it.copy(summary = it.summary.copy(comments = 0)) }
