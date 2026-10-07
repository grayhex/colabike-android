package ru.colabike.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.about.LicensesRoute
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** What the licences screen names: every library whose terms ask to be shown with the app. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h1600dp-xhdpi")
class LicensesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the map and the chat are named with their licences, beside the fonts and the Apache libraries`() {
        compose.setContent { ColaBikeTheme { LicensesRoute(onBack = {}) } }
        // The texts are read off the main thread.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Inter").fetchSemanticsNodes().isNotEmpty()
        }

        val list = compose.onNodeWithTag("licenses:list")
        listOf(
                "Шрифт приложения",
                "Apache License 2.0",
                "MapLibre Native",
                "Redistribution and use in source and binary forms",
                "OpenStreetMap, OpenMapTiles и OpenFreeMap",
                "openstreetmap.org/copyright",
                "Яндекс Карты (MapKit)",
                "yandex.ru/legal/maps_api",
                "Stream Chat SDK",
                "github.com/GetStream/stream-chat-android/blob/main/LICENSE",
            )
            .forEach {
                list.performScrollToNode(hasText(it, substring = true))
                compose.onNode(hasText(it, substring = true)).assertExists()
            }
    }
}
