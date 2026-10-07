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
import kotlin.math.sin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.BikeCardSkeleton
import ru.colabike.core.designsystem.component.BrandMark
import ru.colabike.core.designsystem.component.ChartPoint
import ru.colabike.core.designsystem.component.ChartSeries
import ru.colabike.core.designsystem.component.ColaDropdownChip
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaNavItem
import ru.colabike.core.designsystem.component.ColaNavigationBar
import ru.colabike.core.designsystem.component.ColaNavigationRail
import ru.colabike.core.designsystem.component.ColaRadioRow
import ru.colabike.core.designsystem.component.ColaSwitchRow
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ComponentCard
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.HaloTone
import ru.colabike.core.designsystem.component.IconHalo
import ru.colabike.core.designsystem.component.JournalCard
import ru.colabike.core.designsystem.component.LikeButton
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.component.ListingCard
import ru.colabike.core.designsystem.component.NotificationRow
import ru.colabike.core.designsystem.component.PhotoTile
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.component.RideCard
import ru.colabike.core.designsystem.component.SeriesChart
import ru.colabike.core.designsystem.component.StatTile
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.RideStatus

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

    private fun rideVariants(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                RideCard(
                    PreviewData.plannedRide,
                    onClick = {},
                    badges = listOf("Организатор"),
                    note = "Изменено после вашего ответа",
                )
                RideCard(PreviewData.ride, badges = listOf("Частная"))
                RideCard(
                    PreviewData.ride.copy(status = RideStatus.Cancelled),
                    badges = listOf("Частная"),
                )
            }
        }

    @Test fun rideVariantsLight() = rideVariants(dark = false, name = "ride_variants_light")

    @Test fun rideVariantsDark() = rideVariants(dark = true, name = "ride_variants_dark")

    @Test
    fun rideVariantsLargeText() =
        rideVariants(dark = false, fontScale = 2f, name = "ride_variants_font_200")

    private fun notificationRows(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                NotificationRow(
                    title = "Новый комментарий",
                    body = "Тестовый Райдер · Городской Трэвел",
                    whenText = "3 окт. 2026 г., 18:30",
                    unread = true,
                    actor = PreviewData.rider,
                    onClick = {},
                )
                NotificationRow(
                    title = "Новый подписчик",
                    body = "Тестовый Райдер (@test-rider)",
                    whenText = "3 окт. 2026 г., 12:00",
                    unread = false,
                    actor = PreviewData.rider,
                    onClick = {},
                )
                NotificationRow(
                    title = "Объявление",
                    body =
                        "Втулка Shimano Deore. Срок объявления скоро выйдет. Действует до 9 окт. 2026 г., 09:00",
                    whenText = "2 окт. 2026 г., 09:00",
                    unread = true,
                    actor = null,
                )
            }
        }

    @Test
    fun notificationRowsLight() = notificationRows(dark = false, name = "notification_rows_light")

    @Test
    fun notificationRowsDark() = notificationRows(dark = true, name = "notification_rows_dark")

    @Test
    fun notificationRowsLargeText() =
        notificationRows(dark = false, fontScale = 2f, name = "notification_rows_font_200")

    private fun componentCards(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                ComponentCard(
                    name = "Deore M6100 кассета 12 ск.",
                    brand = "Shimano",
                    category = "Трансмиссия",
                    builds = 42,
                    coverUrl = null,
                    archived = false,
                    onClick = {},
                )
                ComponentCard(
                    name = "Level T",
                    brand = "SRAM",
                    category = "Тормоза",
                    builds = 1,
                    coverUrl = null,
                    archived = true,
                    onClick = {},
                )
                ComponentCard(
                    name =
                        "Очень длинное название модели компонента, которое не помещается в одну строку",
                    brand = "",
                    category = "",
                    builds = 0,
                    coverUrl = null,
                    archived = false,
                    onClick = {},
                )
            }
        }

    @Test fun componentCardsLight() = componentCards(dark = false, name = "component_cards_light")

    @Test fun componentCardsDark() = componentCards(dark = true, name = "component_cards_dark")

    @Test
    fun componentCardsLargeText() =
        componentCards(dark = false, fontScale = 2f, name = "component_cards_font_200")

    private fun charts(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            val hills = (0..30).map { ChartPoint(it * 1.0, 130.0 + 24 * sin(it / 4.0)) }.chunked(16)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                SeriesChart(
                    ChartSeries(
                        title = "Высота",
                        runs = hills,
                        minLabel = "106 м",
                        maxLabel = "154 м",
                        startLabel = "0 км",
                        endLabel = "30 км",
                        summary = "от 106 до 154 м на 30 км пути; есть разрыв, где участок скрыт",
                    )
                )
                SeriesChart(
                    ChartSeries(
                        title = "Пульс",
                        runs =
                            listOf(
                                listOf(ChartPoint(0.0, 120.0), ChartPoint(5.0, 150.0)),
                                listOf(ChartPoint(8.0, 140.0)),
                                listOf(ChartPoint(12.0, 133.0), ChartPoint(20.0, 128.0)),
                            ),
                        minLabel = "120 уд/мин",
                        maxLabel = "150 уд/мин",
                        startLabel = "0 км",
                        endLabel = "20 км",
                        summary = "от 120 до 150 уд/мин",
                    )
                )
            }
        }

    @Test fun chartsLight() = charts(dark = false, name = "charts_light")

    @Test fun chartsDark() = charts(dark = true, name = "charts_dark")

    @Test fun chartsLargeText() = charts(dark = false, fontScale = 2f, name = "charts_font_200")

    private fun feedCards(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                JournalCard(PreviewData.journal, onClick = {})
                JournalCard(
                    PreviewData.journal.copy(
                        kind = "other",
                        status = JournalStatus.Draft,
                        title = "Без метки и без текста",
                        excerpt = "",
                        eventDate = null,
                        mileageKm = null,
                        liked = true,
                    ),
                    onClick = {},
                )
                ListingCard(PreviewData.listing)
                ListingCard(PreviewData.listing.copy(type = "wanted", price = null, location = ""))
                RideCard(PreviewData.ride)
            }
        }

    @Test fun feedCardsLight() = feedCards(dark = false, name = "feed_cards_light")

    @Test fun feedCardsDark() = feedCards(dark = true, name = "feed_cards_dark")

    @Test
    fun feedCardsLargeText() = feedCards(dark = false, fontScale = 2f, name = "feed_cards_font_200")

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
                // Filters that open a list: the usual value, a chosen one, a long one cut to a
                // line.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ColaDropdownChip(label = "Все") {}
                    ColaDropdownChip(label = "Мои", active = true) {}
                    ColaDropdownChip(
                        label = "Шоссе, Гравийные, Городские и ещё 3",
                        active = true,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {}
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

    private fun choices(dark: Boolean, fontScale: Float = 1f, name: String) =
        compose.snapshot(name, dark, fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ColaSwitchRow(
                    title = "Тихие часы",
                    supporting = "С 22:00 до 07:00 письма и push ждут утра",
                    checked = true,
                    onCheckedChange = {},
                )
                ColaSwitchRow(
                    title = "Напоминания о покатушке",
                    checked = false,
                    onCheckedChange = {},
                )
                ColaSwitchRow(
                    title = "Push-уведомления",
                    supporting = "Сервер пока не принимает устройства",
                    checked = false,
                    onCheckedChange = {},
                    enabled = false,
                )
                ColaSwitchRow(
                    title = "Только показывает состояние",
                    checked = true,
                    onCheckedChange = null,
                )
                ColaRadioRow(
                    title = "Друзья",
                    supporting = "Взаимные подписки — по умолчанию",
                    selected = true,
                    onSelect = {},
                )
                ColaRadioRow(title = "Все, на кого я подписан", selected = false, onSelect = {})
                ColaRadioRow(
                    title = "Выбранные люди",
                    selected = false,
                    onSelect = {},
                    enabled = false,
                )
            }
        }

    @Test fun choicesLight() = choices(dark = false, name = "choices_light")

    @Test fun choicesDark() = choices(dark = true, name = "choices_dark")

    @Test fun choicesLargeText() = choices(dark = false, fontScale = 2f, name = "choices_font_200")
}
