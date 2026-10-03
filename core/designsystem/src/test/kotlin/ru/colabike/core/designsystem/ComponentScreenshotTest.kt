package ru.colabike.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.BrandMark
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaNavItem
import ru.colabike.core.designsystem.component.ColaNavigationBar
import ru.colabike.core.designsystem.component.ColaNavigationRail
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.HaloTone
import ru.colabike.core.designsystem.component.IconHalo
import ru.colabike.core.designsystem.component.LikeButton
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.component.PhotoTile
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.component.RideCard
import ru.colabike.core.designsystem.component.StatTile
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

    private val sections =
        listOf(
            ColaNavItem("Лента", ColaIcons.Feed, ColaIcons.FeedFilled),
            ColaNavItem("Велосипеды", ColaIcons.Bike, ColaIcons.BikeFilled),
            ColaNavItem("Покатушки", ColaIcons.Route, ColaIcons.Route),
            ColaNavItem("Сообщения", ColaIcons.Chat, ColaIcons.ChatFilled),
            ColaNavItem("Профиль", ColaIcons.Person, ColaIcons.PersonFilled),
        )

    private fun navigation(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                // All five sections of the target shell, and the two shipped today.
                ColaNavigationBar(sections, selectedIndex = 1, onSelect = {})
                ColaNavigationBar(sections.take(2), selectedIndex = 0, onSelect = {})
            }
        }

    @Test fun navigationLight() = navigation(dark = false, name = "navigation_light")

    @Test fun navigationDark() = navigation(dark = true, name = "navigation_dark")

    @Test
    fun navigationLargeText() =
        navigation(dark = false, fontScale = 2f, name = "navigation_font_200")

    @Test
    fun railLight() =
        compose.snapshot("rail_light", dark = false) {
            Row(Modifier.height(420.dp)) {
                ColaNavigationRail(
                    sections.take(2),
                    selectedIndex = 0,
                    onSelect = {},
                    header = { BrandMark(size = 40.dp) },
                )
            }
        }

    @Test
    fun railDark() =
        compose.snapshot("rail_dark", dark = true) {
            Row(Modifier.height(420.dp)) {
                ColaNavigationRail(
                    sections.take(2),
                    selectedIndex = 1,
                    onSelect = {},
                    header = { BrandMark(size = 40.dp) },
                )
            }
        }

    private fun parts(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                ColaTopBar(title = "Велосипеды", subtitle = "Гараж сообщества", onBack = {})
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ColaFilterChip(selected = true, onClick = {}, label = "Все")
                    ColaFilterChip(selected = false, onClick = {}, label = "Мои")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    PillBadge("Москва", icon = ColaIcons.Location)
                    PillBadge("Бывший")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    IconHalo(ColaIcons.MailUnread)
                    IconHalo(ColaIcons.Bike, tone = HaloTone.Secondary)
                    BrandMark(size = 48.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    StatTile("Вес", "14,2 кг", Modifier.weight(1f))
                    StatTile("Пробег", "1 200 км", Modifier.weight(1f))
                }
            }
        }

    @Test fun partsLight() = parts(dark = false, name = "parts_light")

    @Test fun partsDark() = parts(dark = true, name = "parts_dark")

    @Test fun partsLargeText() = parts(dark = false, fontScale = 2f, name = "parts_font_200")

    private fun listItems(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ColaListItem(
                    title = "Устройства и входы",
                    supporting = "Где выполнен вход в ваш аккаунт",
                    icon = ColaIcons.Devices,
                    onClick = {},
                )
                ColaListItem(
                    title = "Управление аккаунтом",
                    supporting = "Почта, пароль и удаление аккаунта: на сайте, в браузере",
                    icon = ColaIcons.Person,
                    tone = HaloTone.Secondary,
                    action = ListItemAction.External,
                    onClick = {},
                )
                ColaListItem(
                    title = "Google Pixel 9",
                    supporting = "Приложение для Android 0.2.0 · сейчас в сети",
                    icon = ColaIcons.Smartphone,
                    trailing = { PillBadge("Новое") },
                )
                ColaListItem(title = "Только название")
            }
        }

    @Test fun listItemsLight() = listItems(dark = false, name = "list_items_light")

    @Test fun listItemsDark() = listItems(dark = true, name = "list_items_dark")

    @Test
    fun listItemsLargeText() = listItems(dark = false, fontScale = 2f, name = "list_items_font_200")

    private fun likes(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    LikeButton(liked = false, count = 4, onToggle = {})
                    LikeButton(liked = true, count = 5, onToggle = {})
                    // One's own bike: the count, no switch.
                    LikeButton(liked = false, count = 12)
                }
                Row(
                    Modifier.height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    PhotoTile(
                        "Нет фото",
                        Modifier.weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    PhotoTile(
                        "Фото недоступно",
                        Modifier.weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
        }

    @Test fun likesLight() = likes(dark = false, name = "likes_light")

    @Test fun likesDark() = likes(dark = true, name = "likes_dark")

    @Test fun likesLargeText() = likes(dark = false, fontScale = 2f, name = "likes_font_200")
}
