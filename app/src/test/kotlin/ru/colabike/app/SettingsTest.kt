package ru.colabike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.settings.PreferencesSettings
import ru.colabike.app.settings.ThemeMode

@RunWith(RobolectricTestRunner::class)
class SettingsTest {
    private val preferences =
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @Test
    fun `a fresh install follows the system and is not a guest`() {
        val settings = PreferencesSettings(preferences)

        assertThat(settings.themeMode.value).isEqualTo(ThemeMode.System)
        assertThat(settings.browsingAsGuest.value).isFalse()
    }

    @Test
    fun `the choices survive a restart`() {
        PreferencesSettings(preferences).apply {
            setThemeMode(ThemeMode.Dark)
            setBrowsingAsGuest(true)
        }

        val restarted = PreferencesSettings(preferences)

        assertThat(restarted.themeMode.value).isEqualTo(ThemeMode.Dark)
        assertThat(restarted.browsingAsGuest.value).isTrue()
    }

    @Test
    fun `a value from a newer version is the system's choice, not a crash`() {
        preferences.edit().putString("theme_mode", "Sepia").apply()

        assertThat(PreferencesSettings(preferences).themeMode.value).isEqualTo(ThemeMode.System)
    }

    @Test
    fun `the person's choice beats the system's`() {
        assertThat(ThemeMode.System.isDark(systemDark = true)).isTrue()
        assertThat(ThemeMode.System.isDark(systemDark = false)).isFalse()
        assertThat(ThemeMode.Light.isDark(systemDark = true)).isFalse()
        assertThat(ThemeMode.Dark.isDark(systemDark = false)).isTrue()
    }

    @Test
    fun `site pages are built on the site's address, with or without a trailing slash`() {
        listOf("https://colabike.ru", "https://colabike.ru/").forEach {
            val links = SiteLinks(it)
            assertThat(links.register).isEqualTo("https://colabike.ru/register")
            assertThat(links.forgotPassword).isEqualTo("https://colabike.ru/forgot-password")
            assertThat(links.account).isEqualTo("https://colabike.ru/account?tab=account")
            assertThat(links.terms).isEqualTo("https://colabike.ru/legal/terms")
            assertThat(links.privacy).isEqualTo("https://colabike.ru/legal/privacy")
        }
    }
}
