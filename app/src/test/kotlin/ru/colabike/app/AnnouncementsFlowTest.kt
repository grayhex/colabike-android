package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.links.LinkOpener
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.AppNotice
import ru.colabike.core.model.Compatibility
import ru.colabike.core.model.LaunchConfig
import ru.colabike.core.model.LaunchFill
import ru.colabike.core.model.NoticeAction
import ru.colabike.core.model.NoticeKind
import ru.colabike.core.model.OnboardingConfig
import ru.colabike.core.model.OnboardingItem
import ru.colabike.core.model.UpdateMode

/**
 * What the server announces — the launch screen, the introduction, a message and the version policy
 * — as a person meets it from the first frame on, on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class AnnouncementsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val picture = File.createTempFile("launch", ".webp")

    private fun start(
        config: FakeAppConfig,
        settings: FakeSettings = FakeSettings(),
        auth: FakeAuth = FakeAuth(),
        assets: FakeConfigAssets = FakeConfigAssets(),
        versionCode: Int = 1,
        manualClock: Boolean = false,
    ): FakeDependencies {
        val dependencies =
            FakeDependencies(
                bikes = FakeBikes(mapOf(null to ru.colabike.core.model.Page(bikes(0, 3), null))),
                appConfig = config,
                settings = settings,
                auth = auth,
                configAssets = assets,
                versionCode = versionCode,
            )
        compose.mainClock.autoAdvance = !manualClock
        compose.setContent {
            ColaBikeTheme {
                CompositionLocalProvider(LocalLinkOpener provides LinkOpener { opened += it }) {
                    ColaBikeApp(dependencies)
                }
            }
        }
        compose.waitForIdle()
        return dependencies
    }

    private val launch =
        LaunchConfig(
            enabled = true,
            imageUrl = "https://colabike.test/api/assets/launch?width=1920",
            fill = LaunchFill.Fit,
            title = "Сезон открыт",
        )

    private val pages =
        OnboardingConfig(
            enabled = true,
            revision = 3,
            items =
                listOf(
                    OnboardingItem("Гараж", "Соберите свой велосипед.", null),
                    OnboardingItem("Покатушки", null, null),
                    OnboardingItem("Сообщения", "Пишите друг другу.", null),
                ),
        )

    private fun notice(revision: Int = 5, kind: NoticeKind = NoticeKind.Service) =
        AppNotice(
            revision = revision,
            kind = kind,
            title = "Плановые работы",
            body = "В воскресенье с 3 до 5 утра сайт может быть недоступен.",
            imageUrl = null,
            action = NoticeAction("Подробнее", "https://colabike.test/about#status"),
        )

    // --- the launch screen ---------------------------------------------------------------------

    @Test
    fun `the launch screen covers the start while the session is restored, and goes with it`() {
        val config = FakeAppConfig().apply { set(launch = launch) }
        val assets = FakeConfigAssets(mutableMapOf(launch.imageUrl!! to picture))
        val auth = FakeAuth(AuthState.Restoring)
        start(config, auth = auth, assets = assets, manualClock = true)

        compose.onNodeWithTag("launch").assertIsDisplayed()
        // TalkBack hears only the name; the title is there for the eyes (unmerged tree).
        compose.onNodeWithText("Сезон открыт", useUnmergedTree = true).assertIsDisplayed()

        // The start is over: no minimum time is kept for the picture.
        auth.state.value = AuthState.SignedIn(account)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("launch").assertDoesNotExist()
    }

    @Test
    fun `the launch screen leaves after a moment even if the start drags on`() {
        val config = FakeAppConfig().apply { set(launch = launch) }
        val assets = FakeConfigAssets(mutableMapOf(launch.imageUrl!! to picture))
        start(config, auth = FakeAuth(AuthState.Restoring), assets = assets, manualClock = true)
        compose.onNodeWithTag("launch").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(1_700)
        compose.waitForIdle()

        compose.onNodeWithTag("launch").assertDoesNotExist()
    }

    @Test
    fun `no picture on the device is no launch screen, and nothing is waited for`() {
        val config = FakeAppConfig().apply { set(launch = launch) }
        start(config, auth = FakeAuth(AuthState.Restoring), assets = FakeConfigAssets())

        compose.onNodeWithTag("launch").assertDoesNotExist()
    }

    @Test
    fun `a launch screen the server switched off is not shown even with its picture`() {
        val config = FakeAppConfig().apply { set(launch = launch.copy(enabled = false)) }
        val assets = FakeConfigAssets(mutableMapOf(launch.imageUrl!! to picture))
        start(config, auth = FakeAuth(AuthState.Restoring), assets = assets)

        compose.onNodeWithTag("launch").assertDoesNotExist()
    }

    @Test
    fun `a launch screen that arrives while the app runs does not appear in the middle of it`() {
        val config = FakeAppConfig()
        val assets = FakeConfigAssets(mutableMapOf(launch.imageUrl!! to picture))
        start(config, assets = assets)

        config.set(launch = launch)
        compose.waitForIdle()

        compose.onNodeWithTag("launch").assertDoesNotExist()
    }

    // --- the introduction ----------------------------------------------------------------------

    @Test
    fun `the introduction is shown once, page by page, and remembered when finished`() {
        val config = FakeAppConfig().apply { set(onboarding = pages) }
        val settings = FakeSettings()
        start(config, settings)

        compose.onNodeWithTag("onboarding").assertIsDisplayed()
        compose.onNodeWithText("Гараж").assertIsDisplayed()
        compose.onNodeWithTag("onboarding:next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Покатушки").assertIsDisplayed()
        compose.onNodeWithTag("onboarding:next").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Сообщения").assertIsDisplayed()
        // The last page has no "skip", and its button is "done".
        compose.onNodeWithTag("onboarding:skip").assertDoesNotExist()
        compose.onNodeWithText("Готово").performClick()
        compose.waitForIdle()

        assertThat(settings.onboardingSeen.value).isEqualTo(3)
        compose.onNodeWithTag("onboarding").assertDoesNotExist()
        compose.onNode(sectionTab("Велосипеды")).assertExists()
    }

    @Test
    fun `skipping is the same as finishing`() {
        val config = FakeAppConfig().apply { set(onboarding = pages) }
        val settings = FakeSettings()
        start(config, settings)

        compose.onNodeWithTag("onboarding:skip").performClick()
        compose.waitForIdle()

        assertThat(settings.onboardingSeen.value).isEqualTo(3)
        compose.onNodeWithTag("onboarding").assertDoesNotExist()
    }

    @Test
    fun `an introduction the person has seen is not shown again, a new revision is`() {
        val config = FakeAppConfig().apply { set(onboarding = pages) }
        val settings = FakeSettings().apply { onboardingSeen.value = 3 }
        start(config, settings)
        compose.onNodeWithTag("onboarding").assertDoesNotExist()

        config.set(onboarding = pages.copy(revision = 4))
        compose.waitForIdle()

        compose.onNodeWithTag("onboarding").assertIsDisplayed()
    }

    @Test
    fun `the introduction waits for the session to be restored, and does not need an account`() {
        val config = FakeAppConfig().apply { set(onboarding = pages) }
        val auth = FakeAuth(AuthState.Restoring)
        start(config, auth = auth)
        compose.onNodeWithTag("onboarding").assertDoesNotExist()

        auth.state.value = AuthState.SignedOut
        compose.waitForIdle()

        // Before the sign-in, as after it.
        compose.onNodeWithTag("onboarding").assertIsDisplayed()
    }

    @Test
    fun `an introduction with no pages, or switched off, is not shown`() {
        start(FakeAppConfig().apply { set(onboarding = pages.copy(items = emptyList())) })
        compose.onNodeWithTag("onboarding").assertDoesNotExist()
    }

    // --- the message -----------------------------------------------------------------------------

    @Test
    fun `a message is a band over the content, with its button, and can be closed for good`() {
        val config = FakeAppConfig().apply { set(notice = notice()) }
        val settings = FakeSettings()
        start(config, settings)

        compose.onNodeWithTag("notice").assertIsDisplayed()
        compose.onNodeWithText("Плановые работы").assertIsDisplayed()
        compose.onNodeWithText("Подробнее").performClick()
        assertThat(opened).containsExactly("https://colabike.test/about#status")

        compose.onNodeWithTag("banner:close").performClick()
        compose.waitForIdle()

        assertThat(settings.noticeClosed.value).isEqualTo(5)
        compose.onNodeWithTag("notice").assertDoesNotExist()
    }

    @Test
    fun `a closed message stays closed, a changed one comes back`() {
        val config = FakeAppConfig().apply { set(notice = notice(revision = 5)) }
        val settings = FakeSettings().apply { noticeClosed.value = 5 }
        start(config, settings)
        compose.onNodeWithTag("notice").assertDoesNotExist()

        config.set(notice = notice(revision = 6, kind = NoticeKind.Maintenance))
        compose.waitForIdle()

        compose.onNodeWithTag("notice").assertIsDisplayed()
    }

    @Test
    fun `the button of a message opens only what the app would open`() {
        val config =
            FakeAppConfig().apply {
                set(
                    notice =
                        notice()
                            .copy(
                                action = NoticeAction("К полям", "https://colabike.test/app/auth")
                            )
                )
            }
        start(config)

        compose.onNodeWithText("К полям").performClick()

        // The page that finishes a sign-in is the app's own business, never a link to a browser.
        assertThat(opened).isEmpty()
    }

    // --- the version policy ----------------------------------------------------------------------

    private fun policy(
        minimum: Int? = null,
        latest: Int? = 10,
        mode: UpdateMode = UpdateMode.Soft,
    ) =
        Compatibility(
            minimumSupportedVersionCode = minimum,
            latestVersionCode = latest,
            mode = mode,
            updateUrl = "https://store.example/colabike",
            message = null,
        )

    @Test
    fun `a newer build is a quiet offer that can be closed for that version`() {
        val config = FakeAppConfig().apply { set(compatibility = policy()) }
        val settings = FakeSettings()
        start(config, settings)

        compose.onNodeWithTag("update_offer").assertIsDisplayed()
        compose.onNodeWithText("Вышла новая версия").assertIsDisplayed()
        compose.onNodeWithText("Обновить").performClick()
        assertThat(opened).containsExactly("https://store.example/colabike")

        compose.onNodeWithTag("banner:close").performClick()
        compose.waitForIdle()

        assertThat(settings.updateOfferClosed.value).isEqualTo(10)
        compose.onNodeWithTag("update_offer").assertDoesNotExist()
    }

    @Test
    fun `an offer closed for a version does not come back for it, a newer version brings it back`() {
        val config = FakeAppConfig().apply { set(compatibility = policy()) }
        val settings = FakeSettings().apply { updateOfferClosed.value = 10 }
        start(config, settings)
        compose.onNodeWithTag("update_offer").assertDoesNotExist()

        config.set(compatibility = policy(latest = 11))
        compose.waitForIdle()

        compose.onNodeWithTag("update_offer").assertIsDisplayed()
    }

    @Test
    fun `a build below the minimum that is not blocked gets a firm offer, which beats a message`() {
        val config =
            FakeAppConfig().apply {
                set(compatibility = policy(minimum = 5), notice = notice())
            }
        start(config)

        compose.onNodeWithText("Эта версия скоро перестанет работать").assertIsDisplayed()
        compose.onNodeWithTag("notice").assertDoesNotExist()

        compose.onNodeWithTag("banner:close").performClick()
        compose.waitForIdle()
        // Later: the message that was waiting shows.
        compose.onNodeWithTag("notice").assertIsDisplayed()
    }

    @Test
    fun `a blocked build sees only the way to update, and can check again`() {
        val config =
            FakeAppConfig().apply {
                set(compatibility = policy(minimum = 5, mode = UpdateMode.Hard))
            }
        start(config)

        compose.onNodeWithTag("update_required").assertIsDisplayed()
        compose.onNodeWithText("Нужно обновить приложение").assertIsDisplayed()
        // No sign-in, no content, no tabs behind it.
        compose.onNodeWithText("Войти").assertDoesNotExist()
        compose.onNodeWithTag("update:open").performClick()
        assertThat(opened).containsExactly("https://store.example/colabike")

        compose.onNodeWithTag("update:check").performClick()
        assertThat(config.forced).isEqualTo(1)

        // The server lifted the block: the app is back.
        config.set(compatibility = policy(minimum = 1, mode = UpdateMode.Hard))
        compose.waitForIdle()
        compose.onNodeWithTag("update_required").assertDoesNotExist()
    }

    @Test
    fun `a block that nobody has confirmed for a day is no block any more`() {
        val config =
            FakeAppConfig().apply {
                set(
                    compatibility = policy(minimum = 5, mode = UpdateMode.Hard),
                    // The test clock is 2026-10-03T20:00Z: a day and a half later.
                    validatedAt = Instant.parse("2026-10-02T08:00:00Z"),
                )
            }
        start(config)

        compose.onNodeWithTag("update_required").assertDoesNotExist()
        compose.onNodeWithText("Эта версия скоро перестанет работать").assertIsDisplayed()
    }

    @Test
    fun `a failed check is said in words`() {
        val config =
            FakeAppConfig().apply {
                set(compatibility = policy(minimum = 5, mode = UpdateMode.Hard))
            }
        start(config)

        config.state.value = config.state.value.copy(refreshFailed = true)
        compose.waitForIdle()

        compose.onNodeWithText("Не удалось проверить", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a build at or above the minimum is not told anything it does not need`() {
        start(FakeAppConfig().apply { set(compatibility = policy(minimum = 1, latest = 1)) })

        compose.onNodeWithTag("update_offer").assertDoesNotExist()
        compose.onNodeWithTag("update_required").assertDoesNotExist()
    }
}
