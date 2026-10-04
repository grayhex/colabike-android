package ru.colabike.app.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.BrandMark
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AppNotice
import ru.colabike.core.model.NoticeAction
import ru.colabike.core.model.NoticeKind
import ru.colabike.core.model.UpdateState

/**
 * The server's message, as one band above the content. The kind sets how loud it is (a promo is
 * quiet, a service note is plain, technical works are the most visible); it informs and blocks
 * nothing, and the person closes it (the app remembers the revision that was closed, so a changed
 * message comes back). Its button goes through the same checks as every address from the server.
 */
@Composable
fun NoticeBanner(
    notice: AppNotice,
    image: File?,
    onAction: (NoticeAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind =
        stringResource(
            when (notice.kind) {
                NoticeKind.Promo -> R.string.notice_promo
                NoticeKind.Service -> R.string.notice_service
                NoticeKind.Maintenance -> R.string.notice_maintenance
            }
        )
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (notice.kind) {
            NoticeKind.Promo -> scheme.surfaceContainerHigh to scheme.onSurface
            NoticeKind.Service -> scheme.secondaryContainer to scheme.onSecondaryContainer
            NoticeKind.Maintenance -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        }
    Band(
        container = container,
        content = content,
        kind = kind,
        title = notice.title,
        body = notice.body,
        image = image,
        actionLabel = notice.action?.label,
        onAction = notice.action?.let { action -> { onAction(action) } },
        closeLabel = stringResource(R.string.notice_close),
        onClose = onClose,
        loud = notice.kind == NoticeKind.Maintenance,
        modifier = modifier.testTag("notice"),
    )
}

/**
 * The offer to update: quiet when a newer build exists, firmer when this one is below the minimum
 * the server supports but is not blocked. "Later" closes it; the app does not block anything here.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onUpdate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (url, message, firm) =
        when (state) {
            is UpdateState.Available -> Triple(state.url, state.message, false)
            is UpdateState.Recommended -> Triple(state.url, state.message, true)
            else -> return
        }
    val scheme = MaterialTheme.colorScheme
    Band(
        container = if (firm) scheme.tertiaryContainer else scheme.secondaryContainer,
        content = if (firm) scheme.onTertiaryContainer else scheme.onSecondaryContainer,
        kind = stringResource(if (firm) R.string.update_firm_kind else R.string.update_kind),
        title = stringResource(if (firm) R.string.update_firm_title else R.string.update_title),
        body =
            message
                ?: stringResource(if (firm) R.string.update_firm_body else R.string.update_body),
        image = null,
        actionLabel = url?.let { stringResource(R.string.update_action) },
        onAction = url?.let { { onUpdate(it) } },
        closeLabel = stringResource(R.string.update_later),
        onClose = onClose,
        loud = firm,
        modifier = modifier.testTag("update_offer"),
    )
}

@Composable
private fun Band(
    container: Color,
    content: Color,
    kind: String,
    title: String,
    body: String?,
    image: File?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    closeLabel: String,
    onClose: () -> Unit,
    loud: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier.padding(start = Spacing.l, top = Spacing.m, bottom = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                )
            }
            Column(
                Modifier.weight(1f)
                    // Technical works are worth interrupting TalkBack for; the rest wait for it.
                    .semantics { if (loud) liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Eyebrow(kind, color = content, maxLines = 2)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        modifier = Modifier.heightIn(min = Spacing.touch),
                    ) {
                        Text(actionLabel)
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("banner:close")) {
                Icon(painterResource(ColaIcons.Close), contentDescription = closeLabel)
            }
        }
    }
}

/**
 * The screen that replaces the app when the server blocks this build ("hard"): what to do, the way
 * to the update and a check again, since the server may have lifted the block. Nothing else of the
 * app is behind it; Back leaves the app as usual.
 */
@Composable
fun UpdateRequiredScreen(
    state: UpdateState.Required,
    refreshing: Boolean,
    refreshFailed: Boolean,
    onUpdate: (String) -> Unit,
    onCheckAgain: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl)
            .testTag("update_required"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
    ) {
        BrandMark(size = 72.dp)
        Text(
            stringResource(R.string.update_required_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            state.message ?: stringResource(R.string.update_required_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { onUpdate(state.url) },
            modifier =
                Modifier.widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .testTag("update:open"),
        ) {
            Text(stringResource(R.string.update_action))
        }
        OutlinedButton(
            onClick = onCheckAgain,
            enabled = !refreshing,
            modifier =
                Modifier.widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .testTag("update:check"),
        ) {
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.update_check_again))
            }
        }
        if (refreshFailed && !refreshing) {
            Text(
                stringResource(R.string.update_check_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
