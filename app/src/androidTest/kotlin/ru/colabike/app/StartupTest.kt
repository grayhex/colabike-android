package ru.colabike.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs without the smoke account: a fresh install starts on the device with no saved session and
 * opens sign-in, without touching the network.
 */
@RunWith(AndroidJUnit4::class)
class StartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aFreshInstallOpensSignIn() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Вход в ColaBike").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
