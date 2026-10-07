package ru.colabike.app.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.SiteLinks
import ru.colabike.core.designsystem.component.BrandMark
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.ServiceLinks

@Composable
fun AboutRoute(
    links: SiteLinks,
    service: ServiceLinks,
    onBack: () -> Unit,
    onLicenses: () -> Unit,
) {
    val opener = LocalLinkOpener.current
    val info = LocalBuildInfo.current
    // The pages the server names (checked to be plain https); the site's own when it names none.
    // Support has no page of ours: without one the row is not there.
    val support = service.support
    AboutScreen(
        versionName = info.versionName,
        build = stringResource(R.string.about_build, info.versionCode),
        onBack = onBack,
        onTerms = { opener.open(service.terms ?: links.terms) },
        onPrivacy = { opener.open(service.privacy ?: links.privacy) },
        onHelp = { opener.open(service.help ?: links.help) },
        onAbout = { opener.open(service.about ?: links.about) },
        onSupport = support?.let { { opener.open(it) } },
        onLicenses = onLicenses,
    )
}

/** The app's name and version, the documents (on the site) and the licences of what it ships. */
@Composable
fun AboutScreen(
    versionName: String,
    build: String,
    onBack: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
    onHelp: () -> Unit = {},
    onAbout: () -> Unit = {},
    onSupport: (() -> Unit)? = null,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.about_title), onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
                    .padding(top = Spacing.s, bottom = Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.section),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    BrandMark(size = 72.dp)
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // What support asks for first: the build, in one short line.
                    Text(
                        build,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    ColaListItem(
                        title = stringResource(R.string.about_help),
                        supporting = stringResource(R.string.about_opens_in_browser),
                        icon = ColaIcons.Info,
                        action = ListItemAction.External,
                        onClick = onHelp,
                    )
                    if (onSupport != null) {
                        ColaListItem(
                            title = stringResource(R.string.about_support),
                            supporting = stringResource(R.string.about_opens_in_browser),
                            icon = ColaIcons.Mail,
                            action = ListItemAction.External,
                            onClick = onSupport,
                        )
                    }
                    ColaListItem(
                        title = stringResource(R.string.about_site),
                        supporting = stringResource(R.string.about_opens_in_browser),
                        icon = ColaIcons.OpenInNew,
                        action = ListItemAction.External,
                        onClick = onAbout,
                    )
                    ColaListItem(
                        title = stringResource(R.string.about_terms),
                        supporting = stringResource(R.string.about_opens_in_browser),
                        icon = ColaIcons.Description,
                        action = ListItemAction.External,
                        onClick = onTerms,
                    )
                    ColaListItem(
                        title = stringResource(R.string.about_privacy),
                        supporting = stringResource(R.string.about_opens_in_browser),
                        icon = ColaIcons.Policy,
                        action = ListItemAction.External,
                        onClick = onPrivacy,
                    )
                    ColaListItem(
                        title = stringResource(R.string.about_licenses),
                        supporting = stringResource(R.string.about_licenses_hint),
                        icon = ColaIcons.Info,
                        onClick = onLicenses,
                    )
                }
            }
        }
    }
}
