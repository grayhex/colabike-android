package ru.colabike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.settings.MapProvider
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
    fun `the map under a route is OpenStreetMap until the person chooses another`() {
        assertThat(PreferencesSettings(preferences).mapProvider.value)
            .isEqualTo(MapProvider.OpenStreetMap)
    }

    @Test
    fun `the chosen map is kept over a restart, and the way back is kept too`() {
        PreferencesSettings(preferences).setMapProvider(MapProvider.Yandex)
        assertThat(PreferencesSettings(preferences).mapProvider.value).isEqualTo(MapProvider.Yandex)

        PreferencesSettings(preferences).setMapProvider(MapProvider.OpenStreetMap)
        assertThat(PreferencesSettings(preferences).mapProvider.value)
            .isEqualTo(MapProvider.OpenStreetMap)
    }

    @Test
    fun `a map this version does not know is the default one, not a crash`() {
        preferences.edit().putString("map_provider", "Google").commit()

        assertThat(PreferencesSettings(preferences).mapProvider.value)
            .isEqualTo(MapProvider.OpenStreetMap)
    }

    @Test
    fun `nothing of the server's announcements is seen on a fresh install`() {
        val settings = PreferencesSettings(preferences)

        assertThat(settings.onboardingSeen.value).isNull()
        assertThat(settings.noticeClosed.value).isNull()
        assertThat(settings.updateOfferClosed.value).isNull()
    }

    @Test
    fun `the revisions seen and closed survive a restart, each on its own`() {
        PreferencesSettings(preferences).apply {
            setOnboardingSeen(7)
            setNoticeClosed(8)
        }

        val restarted = PreferencesSettings(preferences)

        assertThat(restarted.onboardingSeen.value).isEqualTo(7)
        assertThat(restarted.noticeClosed.value).isEqualTo(8)
        assertThat(restarted.updateOfferClosed.value).isNull()

        restarted.setUpdateOfferClosed(42)
        assertThat(PreferencesSettings(preferences).updateOfferClosed.value).isEqualTo(42)
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
