package ru.colabike.app

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.bikes.BikeDetailScreen
import ru.colabike.app.bikes.BikeDetailUiState
import ru.colabike.app.bikes.BikesScreen
import ru.colabike.app.bikes.BikesUiState
import ru.colabike.app.devices.DevicesScreen
import ru.colabike.app.devices.DevicesUiState
import ru.colabike.app.people.PeopleListKind
import ru.colabike.app.people.PeopleListScreen
import ru.colabike.app.people.PeopleListUiState
import ru.colabike.app.people.PersonActions
import ru.colabike.app.people.PersonScreen
import ru.colabike.app.people.PersonUiState
import ru.colabike.app.profile.ProfileScreen
import ru.colabike.app.profile.ProfileUiState
import ru.colabike.app.search.Results
import ru.colabike.app.search.SearchScreen
import ru.colabike.app.search.SearchTab
import ru.colabike.app.search.SearchUiState
import ru.colabike.app.settings.ThemeMode
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.designsystem.theme.ColaCanvas
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.Relationship

/** The states a screen has besides its content (DESIGN.md): loading, empty, error, an odd bike. */
enum class ScreenState(val file: String) {
    BikesLoading("bikes_loading"),
    BikesEmptyMine("bikes_empty_mine"),
    BikesError("bikes_error"),
    BikeDetailError("bike_detail_error"),
    BikeDetailBare("bike_detail_bare"),
    BikeDetailPrivateGuest("bike_detail_private_guest"),
    BikesSearchEmpty("bikes_search_empty"),
    BikesFiltered("bikes_filtered"),
    DevicesLoading("devices_loading"),
    DevicesError("devices_error"),
    ProfileFailed("profile_failed"),
    PersonFollowing("person_following"),
    PersonOwn("person_own"),
    PersonNoBikes("person_no_bikes"),
    PersonNotFound("person_not_found"),
    PeopleFollowing("people_following"),
    PeopleEmpty("people_empty"),
    SearchBikes("search_bikes"),
    SearchBikesEmpty("search_bikes_empty"),
    SearchPeople("search_people"),
    SearchPeopleEmpty("search_people_empty"),
    SearchError("search_error"),
}

@OptIn(ExperimentalCoilApi::class)
private val photos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

private val bareBike =
    PreviewData.bikeDetail.copy(
        summary =
            PreviewData.bikeWithoutPhoto.copy(
                name = "Старый шоссейник",
                year = 1992,
                isPublic = false,
                author = PreviewData.rider,
            ),
        trim = "",
        description = "",
        weightKg = null,
        mileageKm = 0,
        manufacturerUrl = null,
        priceRub = null,
        groupOrder = emptyList(),
        photos = emptyList(),
        components =
            listOf(
                BikeComponent("c1", "build", "Рама", "Reynolds 531, сталь", ""),
                BikeComponent("c2", "build", "Вилка", "Жёсткая, сталь", ""),
                BikeComponent("c3", "accessories", "Звонок", "Латунный", ""),
            ),
    )

/** Phone width, both themes and 200 % text. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class StateScreenshotTest(private val state: ScreenState, private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capture() {
        // No pulsing skeletons: the picture must not depend on the virtual clock.
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides photos,
                LocalRippleConfiguration provides null,
                LocalDensity provides Density(LocalDensity.current.density, look.fontScale),
            ) {
                ColaBikeTheme(darkTheme = look.dark) { ColaCanvas { Content(state) } }
            }
        }
        // A capture right after waitForIdle() can come out as the bare window, before the first
        // frame is drawn (seen on CI as an all-white image); the clock lets that frame happen.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.captureWhenDrawn(
            "src/test/screenshots/state_${state.file}_compact_${look.file}.png"
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> =
            ScreenState.entries.flatMap { state -> Look.entries.map { arrayOf(state, it) } }
    }
}

@Composable
private fun Content(state: ScreenState) {
    when (state) {
        ScreenState.BikesLoading -> BikesWith(BikesUiState(loading = true))
        ScreenState.BikesEmptyMine ->
            BikesWith(
                BikesUiState(
                    query = BikeQuery(BikeScope.Mine),
                    loading = false,
                    bikes = emptyList(),
                )
            )
        ScreenState.BikesSearchEmpty ->
            BikesWith(
                BikesUiState(
                    query = BikeQuery(text = "Бромптон", categories = setOf("urban_touring")),
                    typed = "Бромптон",
                    loading = false,
                    bikes = emptyList(),
                )
            )
        ScreenState.BikesFiltered ->
            BikesWith(
                BikesUiState(
                    query = BikeQuery(text = "cube", categories = setOf("mtb", "urban_touring")),
                    typed = "cube",
                    loading = false,
                    bikes = listOf(PreviewData.bike, PreviewData.bikeWithoutPhoto),
                )
            )
        ScreenState.BikesError ->
            BikesWith(BikesUiState(loading = false, error = UiText.Res(R.string.error_offline)))
        ScreenState.BikeDetailError ->
            BikeDetailScreen(
                BikeDetailUiState.Failed(UiText.Res(R.string.error_not_found)),
                showBack = true,
                onBack = {},
                onRetry = {},
            )
        ScreenState.DevicesLoading -> DevicesWith(DevicesUiState.Loading)
        ScreenState.DevicesError ->
            DevicesWith(DevicesUiState.Failed(UiText.Res(R.string.error_offline)))
        ScreenState.ProfileFailed ->
            ProfileScreen(
                ProfileUiState.Failed(UiText.Res(R.string.error_offline)),
                themeMode = ThemeMode.System,
                onThemeMode = {},
                onRetry = {},
                onSignOut = {},
                onSignIn = {},
                onRegister = {},
                onOpenDevices = {},
                onManageOnWeb = {},
                onOpenAbout = {},
            )
        ScreenState.BikeDetailPrivateGuest ->
            BikeDetailScreen(
                BikeDetailUiState.Failed(UiText.Res(R.string.error_not_found), notFound = true),
                showBack = true,
                onBack = {},
                onRetry = {},
                onSignIn = {},
            )
        ScreenState.PersonFollowing ->
            PersonWith(
                PersonUiState(
                    profile =
                        profileOf(
                            PreviewData.rider,
                            Relationship(
                                isSelf = false,
                                following = true,
                                followedBy = true,
                                friends = true,
                            ),
                        ),
                    loading = false,
                    bikes = listOf(PreviewData.bike, PreviewData.bikeWithoutPhoto),
                )
            )
        ScreenState.PersonOwn ->
            PersonWith(
                PersonUiState(
                    profile =
                        profileOf(
                            PreviewData.rider,
                            Relationship(
                                isSelf = true,
                                following = false,
                                followedBy = false,
                                friends = false,
                            ),
                        ),
                    loading = false,
                    bikes = listOf(PreviewData.bike),
                )
            )
        ScreenState.PersonNoBikes ->
            PersonWith(
                PersonUiState(
                    profile = profileOf(PreviewData.rider).copy(bio = "", location = ""),
                    loading = false,
                )
            )
        ScreenState.PersonNotFound ->
            PersonWith(
                PersonUiState(
                    loading = false,
                    error = UiText.Res(R.string.error_not_found),
                    notFound = true,
                )
            )
        ScreenState.PeopleFollowing ->
            PeopleWith(PeopleListUiState(people = people(10, 4), loading = false))
        ScreenState.PeopleEmpty ->
            PeopleWith(PeopleListUiState(people = emptyList(), loading = false))
        ScreenState.SearchBikes ->
            SearchWith(
                SearchUiState(
                    typed = "cube",
                    text = "cube",
                    category = "mtb",
                    bikes =
                        Results(
                            asked = true,
                            items = listOf(PreviewData.bike, PreviewData.bikeWithoutPhoto),
                        ),
                )
            )
        ScreenState.SearchBikesEmpty ->
            SearchWith(
                SearchUiState(
                    typed = "Бромптон",
                    text = "Бромптон",
                    electric = true,
                    bikes = Results(asked = true),
                )
            )
        ScreenState.SearchPeople ->
            SearchWith(
                SearchUiState(
                    tab = SearchTab.People,
                    typed = "райдер",
                    text = "райдер",
                    people = Results(asked = true, items = people(0, 4)),
                )
            )
        ScreenState.SearchPeopleEmpty ->
            SearchWith(
                SearchUiState(
                    tab = SearchTab.People,
                    typed = "zzzz",
                    text = "zzzz",
                    people = Results(asked = true),
                )
            )
        ScreenState.SearchError ->
            SearchWith(
                SearchUiState(
                    typed = "cube",
                    text = "cube",
                    bikes = Results(asked = true, error = UiText.Res(R.string.error_offline)),
                )
            )
        ScreenState.BikeDetailBare ->
            BikeDetailScreen(
                BikeDetailUiState.Loaded(bareBike),
                showBack = true,
                onBack = {},
                onRetry = {},
            )
    }
}

@Composable
private fun PersonWith(state: PersonUiState) =
    PersonScreen(
        state = state,
        actions = PersonActions({}, {}, {}, {}, {}),
        onRetry = {},
        onLoadMore = {},
        onToggleFollow = {},
    )

@Composable
private fun PeopleWith(state: PeopleListUiState) =
    PeopleListScreen(
        kind = PeopleListKind.Following,
        state = state,
        onBack = {},
        onRetry = {},
        onLoadMore = {},
        onOpenPerson = {},
    )

@Composable
private fun SearchWith(state: SearchUiState) =
    SearchScreen(
        state = state,
        onBack = {},
        onTab = {},
        onText = {},
        onClear = {},
        onCategory = {},
        onSuspension = {},
        onElectric = {},
        onFatbike = {},
        onRetry = {},
        onLoadMore = {},
        onOpenBike = {},
        onOpenPerson = {},
    )

@Composable
private fun DevicesWith(state: DevicesUiState) =
    DevicesScreen(
        state = state,
        now = Instant.parse("2026-10-03T20:00:00Z"),
        onBack = {},
        onRetry = {},
        onEnd = {},
        onConfirmEnd = {},
        onDismissQuestion = {},
    )

@Composable
private fun BikesWith(state: BikesUiState) =
    BikesScreen(
        state = state,
        onScope = {},
        onRefresh = {},
        onRetry = {},
        onLoadMore = {},
        onOpen = {},
    )
