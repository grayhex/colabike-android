package ru.colabike.app.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app picks its colours: the system's choice or the person's own. */
enum class ThemeMode {
    System,
    Light,
    Dark;

    /** Whether the dark palette is on, given what the system asks for. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            System -> systemDark
            Light -> false
            Dark -> true
        }
}

/**
 * Whose map lies under a route. OpenStreetMap is the default and the way back; the Yandex map is
 * the person's choice and exists only in a build with the owner's key (docs/adr/0025).
 */
enum class MapProvider {
    OpenStreetMap,
    Yandex,
}

/**
 * Choices of the person that stay on this device. An interface, so tests and previews fake it.
 * Nothing secret lives here: tokens are in `core:auth`.
 */
interface AppSettings {
    val themeMode: StateFlow<ThemeMode>

    fun setThemeMode(mode: ThemeMode)

    /** The map under every route, on every screen; changed without a restart. */
    val mapProvider: StateFlow<MapProvider>

    fun setMapProvider(provider: MapProvider)

    /**
     * The person chose to look around without signing in. It lasts until they sign in; after a
     * sign-out the app asks again.
     */
    val browsingAsGuest: StateFlow<Boolean>

    fun setBrowsingAsGuest(value: Boolean)

    /**
     * What the person has already seen of what the server announces, so that it is not shown again:
     * the revision of the introduction they went through (or skipped), the revision of the notice
     * they closed, and the version whose update offer they closed. Null is "never". These are about
     * the device, not the account: they stay through a sign-out.
     */
    val onboardingSeen: StateFlow<Int?>

    fun setOnboardingSeen(revision: Int)

    val noticeClosed: StateFlow<Int?>

    fun setNoticeClosed(revision: Int)

    val updateOfferClosed: StateFlow<Int?>

    fun setUpdateOfferClosed(versionCode: Int)
}

/** [AppSettings] in SharedPreferences (never backed up: `allowBackup` is off). */
class PreferencesSettings(private val preferences: SharedPreferences) : AppSettings {
    private val mutableTheme = MutableStateFlow(readTheme())
    override val themeMode: StateFlow<ThemeMode> = mutableTheme.asStateFlow()

    private val mutableMap = MutableStateFlow(readMapProvider())
    override val mapProvider: StateFlow<MapProvider> = mutableMap.asStateFlow()

    private val mutableGuest = MutableStateFlow(preferences.getBoolean(KEY_GUEST, false))
    override val browsingAsGuest: StateFlow<Boolean> = mutableGuest.asStateFlow()

    private val mutableOnboarding = MutableStateFlow(readInt(KEY_ONBOARDING))
    override val onboardingSeen: StateFlow<Int?> = mutableOnboarding.asStateFlow()

    private val mutableNotice = MutableStateFlow(readInt(KEY_NOTICE))
    override val noticeClosed: StateFlow<Int?> = mutableNotice.asStateFlow()

    private val mutableUpdate = MutableStateFlow(readInt(KEY_UPDATE))
    override val updateOfferClosed: StateFlow<Int?> = mutableUpdate.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME, mode.name) }
        mutableTheme.value = mode
    }

    override fun setMapProvider(provider: MapProvider) {
        preferences.edit { putString(KEY_MAP, provider.name) }
        mutableMap.value = provider
    }

    override fun setOnboardingSeen(revision: Int) {
        preferences.edit { putInt(KEY_ONBOARDING, revision) }
        mutableOnboarding.value = revision
    }

    override fun setNoticeClosed(revision: Int) {
        preferences.edit { putInt(KEY_NOTICE, revision) }
        mutableNotice.value = revision
    }

    override fun setUpdateOfferClosed(versionCode: Int) {
        preferences.edit { putInt(KEY_UPDATE, versionCode) }
        mutableUpdate.value = versionCode
    }

    private fun readInt(key: String): Int? =
        if (preferences.contains(key)) preferences.getInt(key, 0) else null

    override fun setBrowsingAsGuest(value: Boolean) {
        preferences.edit { putBoolean(KEY_GUEST, value) }
        mutableGuest.value = value
    }

    /** A value this version does not know (a downgrade) is the system's choice, not a crash. */
    private fun readTheme(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == preferences.getString(KEY_THEME, null) }
            ?: ThemeMode.System

    /** A value this version does not know is the default map, not a crash. */
    private fun readMapProvider(): MapProvider =
        MapProvider.entries.firstOrNull { it.name == preferences.getString(KEY_MAP, null) }
            ?: MapProvider.OpenStreetMap

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_MAP = "map_provider"
        const val KEY_GUEST = "browsing_as_guest"
        const val KEY_ONBOARDING = "onboarding_seen"
        const val KEY_NOTICE = "notice_closed"
        const val KEY_UPDATE = "update_offer_closed"
    }
}
