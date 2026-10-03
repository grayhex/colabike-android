package ru.colabike.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.component.RideCard
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.theme.Spacing

/** Shared components in both themes and at 200 % text (DESIGN.md, "Definition of done"). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h900dp-xhdpi")
class ComponentScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun bikeCards(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                BikeCard(PreviewData.bike, onClick = {})
                BikeCard(PreviewData.bikeWithoutPhoto, onClick = {})
            }
        }

    @Test fun bikeCardsLight() = bikeCards(dark = false, name = "bike_cards_light")

    @Test fun bikeCardsDark() = bikeCards(dark = true, name = "bike_cards_dark")

    @Test
    fun bikeCardsLargeText() = bikeCards(dark = false, fontScale = 2f, name = "bike_cards_font_200")

    @Test
    fun ridesAndPeople() =
        compose.snapshot("rides_people_light", dark = false) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                RideCard(PreviewData.ride, onClick = {})
                RideCard(PreviewData.plannedRide, onClick = {})
                UserRow(PreviewData.rider, onClick = {})
            }
        }

    @Test
    fun ridesAndPeopleDark() =
        compose.snapshot("rides_people_dark", dark = true) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                RideCard(PreviewData.ride, onClick = {})
                RideCard(PreviewData.plannedRide, onClick = {})
                UserRow(PreviewData.rider, onClick = {})
            }
        }

    @Test
    fun statesLight() =
        compose.snapshot("states_light", dark = false) {
            Column {
                EmptyState("Пока пусто", "Здесь появятся велосипеды.")
                ErrorState("Нет соединения с сервером.", onRetry = {})
                BikeCardSkeleton()
            }
        }

    @Test
    fun statesDark() =
        compose.snapshot("states_dark", dark = true) {
            Column {
                EmptyState("Пока пусто", "Здесь появятся велосипеды.")
                ErrorState("Нет соединения с сервером.", onRetry = {})
                BikeCardSkeleton()
            }
        }
}
