package ru.colabike.app

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.auth.AuthController
import ru.colabike.app.comments.CommentDrafts
import ru.colabike.app.comments.InMemoryCommentDrafts
import ru.colabike.app.config.AppConfigController
import ru.colabike.app.config.AppConfigSource
import ru.colabike.app.links.PendingNavigation
import ru.colabike.app.links.PreferencesPendingNavigation
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.messages.ChatScreens
import ru.colabike.app.messages.ChatSession
import ru.colabike.app.messages.StreamChatGateway
import ru.colabike.app.messages.StreamChatScreens
import ru.colabike.app.nearby.AndroidCoarseLocation
import ru.colabike.app.nearby.CoarseLocation
import ru.colabike.app.notifications.settings.AndroidDeviceNotifications
import ru.colabike.app.notifications.settings.DeviceNotifications
import ru.colabike.app.push.DeliveryLedger
import ru.colabike.app.push.NoPushProvider
import ru.colabike.app.push.PreferencesLedgerStore
import ru.colabike.app.push.PushHandler
import ru.colabike.app.push.PushOpener
import ru.colabike.app.push.PushPolicy
import ru.colabike.app.push.PushProvider
import ru.colabike.app.push.PushRegistrar
import ru.colabike.app.push.PushRenderer
import ru.colabike.app.push.PushSync
import ru.colabike.app.push.RuStorePushProvider
import ru.colabike.app.push.StoredPushBinding
import ru.colabike.app.push.VisibleConversation
import ru.colabike.app.rides.map.MapLibreRouteMaps
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.settings.AppSettings
import ru.colabike.app.settings.PreferencesSettings
import ru.colabike.core.auth.AuthInterceptor
import ru.colabike.core.auth.AuthState
import ru.colabike.core.auth.DeviceInfo
import ru.colabike.core.auth.DeviceSession
import ru.colabike.core.auth.EncryptedFileStore
import ru.colabike.core.auth.KeystoreTokenCipher
import ru.colabike.core.auth.YandexSignIn
import ru.colabike.core.auth.signOuts
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.CommentsRepository
import ru.colabike.core.model.ComponentsRepository
import ru.colabike.core.model.ConfigAssets
import ru.colabike.core.model.Feature
import ru.colabike.core.model.FeedRepository
import ru.colabike.core.model.IntentsRepository
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.MarketRepository
import ru.colabike.core.model.NearbyRepository
import ru.colabike.core.model.NotificationSettingsRepository
import ru.colabike.core.model.NotificationsRepository
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.RidesRepository
import ru.colabike.core.network.ApiConfig
import ru.colabike.core.network.AppConfigCache
import ru.colabike.core.network.ColaBikeApi
import ru.colabike.core.network.FileConfigAssets
import ru.colabike.core.network.HttpClients
import ru.colabike.core.network.MediaUrls
import ru.colabike.core.network.NetworkAccountRepository
import ru.colabike.core.network.NetworkAccountSessionsRepository
import ru.colabike.core.network.NetworkAppConfigRepository
import ru.colabike.core.network.NetworkBikesRepository
import ru.colabike.core.network.NetworkChatRepository
import ru.colabike.core.network.NetworkCommentsRepository
import ru.colabike.core.network.NetworkComponentsRepository
import ru.colabike.core.network.NetworkFeedRepository
import ru.colabike.core.network.NetworkIntentsRepository
import ru.colabike.core.network.NetworkJournalRepository
import ru.colabike.core.network.NetworkMarketRepository
import ru.colabike.core.network.NetworkNearbyRepository
import ru.colabike.core.network.NetworkNotificationSettingsRepository
import ru.colabike.core.network.NetworkNotificationsRepository
import ru.colabike.core.network.NetworkPeopleRepository
import ru.colabike.core.network.NetworkPushDeviceRepository
import ru.colabike.core.network.NetworkRidesRepository

/** What screens get: repositories and auth actions, never HTTP clients (AGENTS.md). */
interface AppDependencies {
    val bikes: BikesRepository
    val account: AccountRepository
    val people: PeopleRepository
    val feed: FeedRepository
    val journal: JournalRepository
    val comments: CommentsRepository
    val rides: RidesRepository
    val notifications: NotificationsRepository

    /** The account's notification settings: the same object the site shows. */
    val notificationSettings: NotificationSettingsRepository

    /** This phone's side of notifications: the permission, the channels, the provider. */
    val deviceNotifications: DeviceNotifications

    /** "I want to ride": intentions of the community and the person's own (docs/adr/0019). */
    val intents: IntentsRepository

    /** The private area of "rides near me" and the rides on now in it (docs/adr/0018). */
    val nearby: NearbyRepository

    /** The phone's approximate place, read once and only when the person asks for it. */
    val coarseLocation: CoarseLocation

    /** Makes the phone's push registration follow the person's choices (docs/adr/0017). */
    val pushSync: PushSync

    /** The conversation in front, whose new messages make no notification. */
    val visibleConversation: VisibleConversation

    /** The public component catalog: models, their photos and filters. */
    val components: ComponentsRepository

    /** The market: listings, the saved ones and the seller's contact on request. */
    val market: MarketRepository

    /** ColaBike's side of the chat: the token, new channels, who one may write to. */
    val chat: ChatRepository

    /** The person's connection to the chat provider, for as long as the app is open. */
    val chatSession: ChatSession

    /** The provider's screens: the SDK's in the app, drawings in tests. */
    val chatScreens: ChatScreens

    /** The `versionCode` of this build, against which the server's version policy is read. */
    val versionCode: Int

    /**
     * The server-managed config: the flags of the app's functions, the service links, the policy.
     */
    val appConfig: AppConfigSource

    /** The pictures of the config, kept on the device apart from the image cache. */
    val configAssets: ConfigAssets

    /** Unsent comment text, in memory for this session only. */
    val drafts: CommentDrafts
    val sessions: AccountSessionsRepository
    val auth: AuthActions
    val settings: AppSettings
    val links: SiteLinks

    /** A place a link pointed at that the shell has not reached yet. */
    val pending: PendingNavigation

    /** What time it is; a fixed clock in tests, so a screenshot does not age. */
    val clock: Clock

    /** Where a ride's route is drawn: the real map in the app, a drawing in tests. */
    val maps: RouteMaps
}

/**
 * The object graph, built by hand: few enough parts that a DI framework would only hide them. One
 * instance per process, owned by [ColaBikeApplication].
 */
class AppGraph(context: Context, private val onSignedOut: () -> Unit = {}) : AppDependencies {
    private val config =
        ApiConfig(siteUrl = BuildConfig.SITE_URL, appVersion = BuildConfig.VERSION_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val media = MediaUrls(config.siteUrl)
    private val baseClient = HttpClients.base(config)
    private val secrets = File(context.noBackupFilesDir, "session")

    private val session =
        DeviceSession(
            plainSessions = ColaBikeApi(config, baseClient).sessions,
            store =
                EncryptedFileStore(
                    File(secrets, "refresh.bin"),
                    KeystoreTokenCipher("colabike.refresh.v1"),
                ),
            device =
                DeviceInfo(
                    name = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    appVersion = config.appVersion,
                ),
            media = media,
        )

    /** The client for everything after sign-in, images included. */
    val httpClient: OkHttpClient =
        baseClient
            .newBuilder()
            .addInterceptor(AuthInterceptor(session, config.siteUrl.toHttpUrl().host))
            .build()

    private val api = ColaBikeApi(config, httpClient)

    override val bikes: BikesRepository = NetworkBikesRepository(api.bikes, api.search, media)
    override val account: AccountRepository = NetworkAccountRepository(api.account, media)
    override val people: PeopleRepository = NetworkPeopleRepository(api.users, api.search, media)
    override val feed: FeedRepository = NetworkFeedRepository(api.personal, media)
    private val journalRepository = NetworkJournalRepository(api.journal, api.personal, media)
    override val journal: JournalRepository = journalRepository
    override val comments: CommentsRepository =
        NetworkCommentsRepository(api.comments, api::commentsWithKey, media)
    override val rides: RidesRepository = NetworkRidesRepository(api.rides, api.personal, media)
    override val notifications: NotificationsRepository =
        NetworkNotificationsRepository(api.personal, media)
    override val notificationSettings: NotificationSettingsRepository =
        NetworkNotificationSettingsRepository(api.personal, media)
    override val intents: IntentsRepository =
        NetworkIntentsRepository(api.planning, api::planningWithKey, media)
    override val nearby: NearbyRepository = NetworkNearbyRepository(api.planning, media)
    override val coarseLocation: CoarseLocation = AndroidCoarseLocation(context)
    override val components: ComponentsRepository =
        NetworkComponentsRepository(api.components, media)
    override val market: MarketRepository = NetworkMarketRepository(api.market, api.personal, media)

    // The config and its pictures live outside the image cache and outside the session: they are
    // the same for everybody, and a sign-out takes nothing of them (nor do they hold anything of a
    // person). The pictures are fetched without the session.
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val configAssets: ConfigAssets =
        FileConfigAssets(File(context.filesDir, "app-config/assets"), baseClient, config.siteUrl)
    private val appConfigController =
        AppConfigController(
            repository =
                NetworkAppConfigRepository(
                    api = api.app,
                    media = media,
                    cache = AppConfigCache(File(context.filesDir, "app-config/app-config.json")),
                    assets = configAssets,
                ),
            scope = scope,
        )
    override val appConfig: AppConfigSource = appConfigController
    override val chat: ChatRepository = NetworkChatRepository(api.chat, api::chatWithKey, media)
    override val chatSession: ChatSession = ChatSession(chat, StreamChatGateway(context), scope)
    override val chatScreens: ChatScreens = StreamChatScreens
    override val drafts: CommentDrafts = InMemoryCommentDrafts()
    override val sessions: AccountSessionsRepository =
        NetworkAccountSessionsRepository(api.sessions)
    override val settings: AppSettings =
        PreferencesSettings(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
    override val links = SiteLinks(config.siteUrl)
    override val clock: Clock = Clock.systemUTC()
    override val maps: RouteMaps = MapLibreRouteMaps(BuildConfig.MAP_STYLE_URL)
    override val pending: PendingNavigation =
        PreferencesPendingNavigation(
            context.getSharedPreferences("pending-navigation", Context.MODE_PRIVATE),
            clock,
        )
    override val auth: AuthController =
        AuthController(
            session = session,
            yandex =
                YandexSignIn(
                    siteUrl = config.siteUrl,
                    returnUrl = BuildConfig.NATIVE_AUTH_RETURN_URL,
                    pending =
                        EncryptedFileStore(
                            File(secrets, "pkce.bin"),
                            KeystoreTokenCipher("colabike.pkce.v1"),
                        ),
                ),
            // The build and the server must both be ready; the server can also switch it off.
            yandexReady = {
                BuildConfig.YANDEX_SIGN_IN &&
                    appConfigController.state.value.features.isEnabled(Feature.NativeYandexSignIn)
            },
            revoke = { api.sessions.revokeCurrentSession() },
            scope = scope,
        )

    // Push (docs/adr/0015, 0017). A build without a project of the owner's at RuStore has no
    // provider: push is not offered and nothing arrives. The binding is what the phone remembers of
    // its registration at the server; without it no message is shown.
    val pushProvider: PushProvider =
        if (BuildConfig.RUSTORE_PROJECT_ID.isBlank()) NoPushProvider
        else
            RuStorePushProvider(
                context.applicationContext as Application,
                BuildConfig.RUSTORE_PROJECT_ID,
            )
    override val deviceNotifications: DeviceNotifications =
        AndroidDeviceNotifications(
            context,
            pushProvider,
            context.getSharedPreferences("device-notifications", Context.MODE_PRIVATE),
        )
    private val pushBinding =
        StoredPushBinding(context.getSharedPreferences("push-binding", Context.MODE_PRIVATE))
    val pushRegistrar =
        PushRegistrar(
            provider = pushProvider,
            devices = NetworkPushDeviceRepository(api.personal),
            settings = notificationSettings,
            phone = deviceNotifications,
            store = pushBinding,
            account = { (session.state.value as? AuthState.SignedIn)?.account?.id?.value },
            scope = scope,
            clock = clock,
        )
    override val pushSync: PushSync = pushRegistrar
    override val visibleConversation = VisibleConversation()
    val pushHandler =
        PushHandler(
            binding = pushBinding,
            ledger =
                DeliveryLedger(
                    PreferencesLedgerStore(
                        context.getSharedPreferences("push-ledger", Context.MODE_PRIVATE)
                    )
                ),
            surface = PushRenderer(context),
            // A message of the conversation the person is reading is on their screen already.
            policy = PushPolicy { envelope, _ -> !visibleConversation.isShowing(envelope) },
            clock = clock,
        )
    val pushOpener = PushOpener(pending, notifications, auth, scope)

    init {
        scope.launch { session.restore() }
        // A signed-in person whose account is known gets the phone registered if they chose push
        // (and unregistered if they did not); nothing is asked of the network when it is whole.
        scope.launch {
            session.state.collect { state ->
                if (state is AuthState.SignedIn && state.account != null)
                    pushRegistrar.request(force = false)
            }
        }
        // Reads what the device kept and asks the server afterwards; nothing waits for the network.
        appConfigController.start()
        // Only a person leaving clears what they leave behind: a start without a session is no
        // sign-out, and a guest's cached pictures survive it.
        scope.launch {
            session.state.signOuts().collect {
                // What the session knew about saved entries is the person's, not the next one's.
                journalRepository.forget()
                drafts.clear()
                // The next person must find nothing of this one's conversations in the SDK, and
                // nothing of their notifications in the tray.
                chatSession.end()
                pushHandler.onSignedOut()
                pushRegistrar.onSignedOut()
                onSignedOut()
            }
        }
    }
}
