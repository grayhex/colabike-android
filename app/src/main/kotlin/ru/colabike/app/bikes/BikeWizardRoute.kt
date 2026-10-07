package ru.colabike.app.bikes

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.catalog.CatalogSource
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.core.model.BikeWizardRepository

/**
 * The wizard of a new bike. It lives in its ViewModel, so a turn of the phone keeps the line, the
 * variants and the parts that were edited; [onCreated] gets the id of the bike that was made, and
 * [onBack] leaves the wizard (after asking, when something was typed in it).
 */
@Composable
fun BikeWizardRoute(
    repository: BikeWizardRepository,
    catalog: CatalogSource,
    onBack: () -> Unit,
    onCreated: (id: String) -> Unit,
) {
    // The dictionaries are asked for when the wizard opens: the copy on the device shows at once.
    LaunchedEffect(catalog) { catalog.load() }
    val dictionaries by catalog.state.collectAsStateWithLifecycle()
    val viewModel =
        viewModel(key = "bike-wizard") {
            BikeWizardViewModel(repository, catalog = { catalog.state.value.catalog })
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val created = state.created
    LaunchedEffect(created?.summary?.id) { created?.let { onCreated(it.summary.id.value) } }
    val opener = LocalLinkOpener.current
    // One step back, or out of the wizard: asked first when something was typed in it.
    val leave: () -> Unit = {
        if (!viewModel.back()) {
            if (viewModel.state.value.dirty) viewModel.askLeave() else onBack()
        }
    }
    BackHandler(onBack = leave)
    val actions =
        remember(viewModel, onBack) {
            BikeWizardActions(
                onBack = leave,
                onSearchText = viewModel::setSearchText,
                onGarageName = viewModel::setGarageName,
                onPageUrl = viewModel::setPageUrl,
                onTogglePage = viewModel::togglePage,
                onSearch = viewModel::search,
                onSearchPage = viewModel::searchPage,
                onRetry = viewModel::retry,
                onChoose = viewModel::choose,
                onStop = viewModel::stopSearch,
                onManual = viewModel::continueByHand,
                onOpenSource = opener::open,
                onAddPart = viewModel::addPart,
                onRemovePart = viewModel::removePart,
                onPartCategory = viewModel::setPartCategory,
                onPartName = viewModel::setPartName,
                onPartPrice = viewModel::setPartPrice,
                onNext = viewModel::next,
                onConfirm = viewModel::confirm,
                onDecline = viewModel::decline,
                onLeave = onBack,
                onSaveWithoutSource = viewModel::saveWithoutSource,
                onSearchAgain = viewModel::searchAgain,
                onUseWeight = viewModel::useSuggestedWeight,
                onUseColor = viewModel::useSuggestedColor,
                onUseLink = viewModel::useSuggestedLink,
                onRetryCatalog = catalog::refreshNow,
                form =
                    BikeEditorActions(
                        onName = viewModel::setGarageName,
                        onBrand = viewModel::setBrand,
                        onModel = viewModel::setModel,
                        onTrim = viewModel::setTrim,
                        onYear = viewModel::setYear,
                        onCategory = viewModel::setCategory,
                        onSubtype = viewModel::setSubtype,
                        onSuspension = viewModel::setSuspension,
                        onConstruction = viewModel::setConstruction,
                        onUse = viewModel::toggleUse,
                        onElectric = viewModel::setElectric,
                        onFatbike = viewModel::setFatbike,
                        onDescription = viewModel::setDescription,
                        onColor = viewModel::setColor,
                        onSize = viewModel::setSize,
                        onWeight = viewModel::setWeight,
                        onMileage = viewModel::setMileage,
                        onLink = viewModel::setManufacturerUrl,
                        onPrice = viewModel::setPrice,
                        onPriceVisibility = viewModel::setPriceVisibility,
                        onFormer = viewModel::setFormer,
                        onPublic = viewModel::setPublic,
                    ),
            )
        }
    BikeWizardScreen(
        state = state,
        actions = actions,
        catalog = dictionaries.catalog,
        catalogFailed = dictionaries.refreshFailed && !dictionaries.fromSite,
    )
}
