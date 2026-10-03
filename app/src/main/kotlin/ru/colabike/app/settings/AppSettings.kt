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
 * Choices of the person that stay on this device. An interface, so tests and previews fake it.
 * Nothing secret lives here: tokens are in `core:auth`.
 */
interface AppSettings {
    val themeMode: StateFlow<ThemeMode>

    fun setThemeMode(mode: ThemeMode)

    /**
     * The person chose to look around without signing in. It lasts until they sign in; after a
     * sign-out the app asks again.
     */
    val browsingAsGuest: StateFlow<Boolean>

    fun setBrowsingAsGuest(value: Boolean)
}

/** [AppSettings] in SharedPreferences (never backed up: `allowBackup` is off). */
class PreferencesSettings(private val preferences: SharedPreferences) : AppSettings {
    private val mutableTheme = MutableStateFlow(readTheme())
    override val themeMode: StateFlow<ThemeMode> = mutableTheme.asStateFlow()

    private val mutableGuest = MutableStateFlow(preferences.getBoolean(KEY_GUEST, false))
    override val browsingAsGuest: StateFlow<Boolean> = mutableGuest.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        preferences.edit { putString(KEY_THEME, mode.name) }
        mutableTheme.value = mode
    }

    override fun setBrowsingAsGuest(value: Boolean) {
        preferences.edit { putBoolean(KEY_GUEST, value) }
        mutableGuest.value = value
    }

    /** A value this version does not know (a downgrade) is the system's choice, not a crash. */
    private fun readTheme(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == preferences.getString(KEY_THEME, null) }
            ?: ThemeMode.System

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_GUEST = "browsing_as_guest"
    }
}
