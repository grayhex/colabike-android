package ru.colabike.core.model

import java.time.Duration
import java.time.Instant

/**
 * A function of the app that the server can switch off. Only what the app has is named here: a key
 * the server sends that is not in this list is dropped when the config is read, and a `true` for a
 * function the app does not have creates nothing. A flag is not a border of access either: what a
 * person may do is decided by the API.
 */
enum class Feature(val key: String) {
    Chat("chat"),
    Market("market"),
    ComponentCatalog("componentCatalog"),
    Rides("rides"),
    BikeEditor("bikeEditor"),
    JournalEditor("journalEditor"),
    NativeYandexSignIn("nativeYandexSignIn"),
}

/**
 * Which functions of the app are on. A function the server does not mention is on: the server
 * switches things off, it does not have to list what works.
 */
data class FeatureAvailability(val flags: Map<String, Boolean> = emptyMap()) {
    fun isEnabled(feature: Feature): Boolean = flags[feature.key] ?: true

    companion object {
        val AllOn = FeatureAvailability()
    }
}

enum class LaunchFill {
    /** The picture whole, with the page around it. */
    Fit,

    /** The picture fills the screen and is cropped. */
    Crop,
}

/** The launch screen after the system splash. Shown only with a picture of the site's own. */
data class LaunchConfig(
    val enabled: Boolean,
    val imageUrl: String?,
    val fill: LaunchFill,
    val title: String?,
) {
    companion object {
        val Off =
            LaunchConfig(enabled = false, imageUrl = null, fill = LaunchFill.Crop, title = null)
    }
}

data class OnboardingItem(val title: String, val body: String?, val imageUrl: String?)

/** The introduction pages; [revision] changes when the administrator publishes a new one. */
data class OnboardingConfig(
    val enabled: Boolean,
    val revision: Int,
    val items: List<OnboardingItem>,
) {
    companion object {
        val Off = OnboardingConfig(enabled = false, revision = 0, items = emptyList())
    }
}

enum class NoticeKind {
    Promo,
    Service,

    /** Technical works. It informs and blocks nothing. */
    Maintenance,
}

/** The button of a notice: a label and an `https` address. */
data class NoticeAction(val label: String, val url: String)

/** The message of the day. [revision] is what a person's "closed" is remembered by. */
data class AppNotice(
    val revision: Int,
    val kind: NoticeKind,
    val title: String,
    val body: String?,
    val imageUrl: String?,
    val action: NoticeAction?,
)

/**
 * The service pages, as `https` addresses. A page the server does not give, or gives in a form the
 * app does not accept, is null and the app uses its own address or hides the action.
 */
data class ServiceLinks(
    val help: String?,
    val privacy: String?,
    val terms: String?,
    val about: String?,
    val support: String?,
) {
    companion object {
        val None = ServiceLinks(null, null, null, null, null)
    }
}

enum class UpdateMode {
    Soft,
    Hard,
}

/** The versions of the build the server supports, by `versionCode`. */
data class Compatibility(
    val minimumSupportedVersionCode: Int?,
    val latestVersionCode: Int?,
    val mode: UpdateMode,
    val updateUrl: String?,
    val message: String?,
) {
    companion object {
        val None =
            Compatibility(
                minimumSupportedVersionCode = null,
                latestVersionCode = null,
                mode = UpdateMode.Soft,
                updateUrl = null,
                message = null,
            )
    }
}

/**
 * What the server lets the app change without a new build: content and availability of what the APK
 * has. No layout, style or code is here. [Builtin] is what the app does with no config at all
 * (first start, no network, a cache that cannot be read): everything on, nothing announced.
 */
data class AppConfig(
    val revision: Int,
    val launch: LaunchConfig,
    val onboarding: OnboardingConfig,
    val notice: AppNotice?,
    val links: ServiceLinks,
    val features: FeatureAvailability,
    val compatibility: Compatibility,
) {
    companion object {
        val Builtin =
            AppConfig(
                revision = 0,
                launch = LaunchConfig.Off,
                onboarding = OnboardingConfig.Off,
                notice = null,
                links = ServiceLinks.None,
                features = FeatureAvailability.AllOn,
                compatibility = Compatibility.None,
            )
    }
}

/** A config the device keeps, and when the server last confirmed it was still the current one. */
data class StoredAppConfig(val config: AppConfig, val validatedAt: Instant)

/** What a request for the config came to. */
sealed interface AppConfigRefresh {
    /** The server says nothing changed; the kept config is confirmed as of [validatedAt]. */
    data class NotModified(val validatedAt: Instant) : AppConfigRefresh

    /** A new config, with its pictures kept, replaced the old one as a whole. */
    data class Updated(val stored: StoredAppConfig) : AppConfigRefresh

    /** Nothing changed on the device. The old config, if any, stays in use. */
    data class Failed(val error: DataError) : AppConfigRefresh
}

/**
 * The server-managed config. Implementations throw nothing: a failure is a
 * [AppConfigRefresh.Failed] and the app goes on with what it has.
 */
interface AppConfigRepository {
    /** The last valid config kept on the device, or null if none, or it cannot be read. */
    suspend fun cached(): StoredAppConfig?

    /**
     * Asks the server with the validator of the kept config. A new config replaces the kept one
     * only when it is whole (read, and its pictures kept); otherwise the old one stays.
     */
    suspend fun refresh(): AppConfigRefresh
}

/**
 * The pictures of a config, kept on the device apart from the image cache (which leaves with the
 * account). A picture is either all there or not there: the screens use [fileOf] and show nothing
 * of what is missing, and never wait for a download.
 */
interface ConfigAssets {
    /** True if every address is now on the device. Never throws. */
    suspend fun prefetch(urls: List<String>): Boolean

    /** Deletes the kept pictures that are not among [urls]. */
    suspend fun retainOnly(urls: List<String>)

    /** The kept picture of [url], or null if it is not on the device. */
    fun fileOf(url: String): java.io.File?
}

/** What a build is told about its own version. */
sealed interface UpdateState {
    data object None : UpdateState

    /** A newer build exists. A quiet offer, nothing is demanded. */
    data class Available(val url: String?, val message: String?) : UpdateState

    /** This build is below the minimum, but the server does not block it: a firm offer. */
    data class Recommended(val url: String?, val message: String?) : UpdateState

    /** This build is below the minimum and the server blocks it: only the way to update. */
    data class Required(val url: String, val message: String?) : UpdateState
}

/** How long a "hard" verdict of a kept config is believed without the server saying it again. */
val HARD_UPDATE_TTL: Duration = Duration.ofHours(24)

/**
 * The update state of [versionCode] under [this] policy. A hard block needs a minimum version and a
 * way to update, and is believed for [HARD_UPDATE_TTL] after the server last confirmed it
 * ([validatedAt]): a rollback on the server, or a server that cannot be reached for long, never
 * locks a person in the app for good, and the block softens into a firm offer.
 */
fun Compatibility.updateState(versionCode: Int, validatedAt: Instant?, now: Instant): UpdateState {
    val minimum = minimumSupportedVersionCode
    val latest = latestVersionCode
    if (minimum != null && versionCode < minimum) {
        val fresh =
            validatedAt != null &&
                !validatedAt.isAfter(now) &&
                Duration.between(validatedAt, now) <= HARD_UPDATE_TTL
        return if (mode == UpdateMode.Hard && updateUrl != null && fresh) {
            UpdateState.Required(updateUrl, message)
        } else {
            UpdateState.Recommended(updateUrl, message)
        }
    }
    if (latest != null && versionCode < latest) return UpdateState.Available(updateUrl, message)
    return UpdateState.None
}
