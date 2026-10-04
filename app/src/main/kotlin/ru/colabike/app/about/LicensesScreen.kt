package ru.colabike.app.about

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.theme.Spacing

/** One licence text and what it covers. */
@Immutable data class LicenseEntry(val title: String, val covers: String, val text: String)

/** Reads the licence texts shipped in `res/raw` of the design system. */
@Composable
fun LicensesRoute(onBack: () -> Unit) {
    val resources = LocalResources.current
    val title = stringResource(R.string.licenses_apache_title)
    val covers = stringResource(R.string.licenses_apache_covers)
    val lora = stringResource(R.string.licenses_lora_title)
    val loraCovers = stringResource(R.string.licenses_lora_covers)
    val sans = stringResource(R.string.licenses_sans_title)
    val sansCovers = stringResource(R.string.licenses_sans_covers)
    val maplibre = stringResource(R.string.licenses_maplibre_title)
    val maplibreCovers = stringResource(R.string.licenses_maplibre_covers)
    val stream = stringResource(R.string.licenses_stream_title)
    val streamCovers = stringResource(R.string.licenses_stream_covers)
    val streamText = stringResource(R.string.licenses_stream_text)
    val entries by
        produceState(emptyList<LicenseEntry>(), title, covers, lora, sans, maplibre, stream) {
            value =
                withContext(Dispatchers.IO) {
                    fun raw(@RawRes id: Int) =
                        resources.openRawResource(id).bufferedReader().use { it.readText() }
                    listOf(
                        LicenseEntry(
                            lora,
                            loraCovers,
                            raw(ru.colabike.core.designsystem.R.raw.license_lora),
                        ),
                        LicenseEntry(
                            sans,
                            sansCovers,
                            raw(ru.colabike.core.designsystem.R.raw.license_source_sans_3),
                        ),
                        LicenseEntry(
                            title,
                            covers,
                            raw(ru.colabike.core.designsystem.R.raw.license_material_symbols),
                        ),
                        LicenseEntry(maplibre, maplibreCovers, raw(R.raw.license_maplibre)),
                        LicenseEntry(stream, streamCovers, streamText),
                    )
                }
        }
    LicensesScreen(entries, onBack)
}

@Composable
fun LicensesScreen(entries: List<LicenseEntry>, onBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.about_licenses), onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 560.dp).fillMaxSize().testTag("licenses:list"),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.screen,
                        vertical = Spacing.s,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                items(entries, key = { it.title }) { entry ->
                    ColaCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(Spacing.card),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                entry.covers,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(entry.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Box(Modifier.padding(bottom = Spacing.xxl)) }
            }
        }
    }
}
