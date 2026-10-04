package ru.colabike.app

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
import ru.colabike.app.links.PendingNavigation
import ru.colabike.app.links.PreferencesPendingNavigation
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.settings.AppSettings
import ru.colabike.app.settings.PreferencesSettings
import ru.colabike.core.auth.AuthInterceptor
import ru.colabike.core.auth.DeviceInfo
import ru.colabike.core.auth.DeviceSession
import ru.colabike.core.auth.EncryptedFileStore
import ru.colabike.core.auth.KeystoreTokenCipher
import ru.colabike.core.auth.YandexSignIn
import ru.colabike.core.auth.signOuts
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.AccountSessionsRepository
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.CommentsRepository
import ru.colabike.core.model.FeedRepository
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.network.ApiConfig
import ru.colabike.core.network.ColaBikeApi
import ru.colabike.core.network.HttpClients
import ru.colabike.core.network.MediaUrls
import ru.colabike.core.network.NetworkAccountRepository
import ru.colabike.core.network.NetworkAccountSessionsRepository
import ru.colabike.core.network.NetworkBikesRepository
import ru.colabike.core.network.NetworkCommentsRepository
import ru.colabike.core.network.NetworkFeedRepository
import ru.colabike.core.network.NetworkJournalRepository
import ru.colabike.core.network.NetworkPeopleRepository

/** What screens get: repositories and auth actions, never HTTP clients (AGENTS.md). */
interface AppDependencies {
    val bikes: BikesRepository
    val account: AccountRepository
    val people: PeopleRepository
    val feed: FeedRepository
    val journal: JournalRepository
    val comments: CommentsRepository

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
    override val drafts: CommentDrafts = InMemoryCommentDrafts()
    override val sessions: AccountSessionsRepository =
        NetworkAccountSessionsRepository(api.sessions)
    override val settings: AppSettings =
        PreferencesSettings(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
    override val links = SiteLinks(config.siteUrl)
    override val clock: Clock = Clock.systemUTC()
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
            yandexEnabled = BuildConfig.YANDEX_SIGN_IN,
            revoke = { api.sessions.revokeCurrentSession() },
            scope = scope,
        )

    init {
        scope.launch { session.restore() }
        // Only a person leaving clears what they leave behind: a start without a session is no
        // sign-out, and a guest's cached pictures survive it.
        scope.launch {
            session.state.signOuts().collect {
                // What the session knew about saved entries is the person's, not the next one's.
                journalRepository.forget()
                drafts.clear()
                onSignedOut()
            }
        }
    }
}
