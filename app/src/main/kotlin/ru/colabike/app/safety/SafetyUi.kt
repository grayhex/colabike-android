package ru.colabike.app.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.ReportReason
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.SafetyRepository
import ru.colabike.core.model.UserId

/** What a menu offers beyond the report: blocking or unblocking the person the page is about. */
class BlockOffer(val blocked: Boolean, val onToggle: () -> Unit)

/**
 * The "more" menu of a top bar: report what the page shows and, on a person's page, block or
 * unblock them. A guest is asked to sign in first; nothing is sent for them afterwards. Reports and
 * blocks are the stores' requirement for an app with user content (docs/adr/0021).
 */
@Composable
fun SafetyMenu(
    target: ReportTarget?,
    safety: SafetyRepository?,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    block: BlockOffer? = null,
) {
    if ((target == null || safety == null) && block == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var reporting by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = Spacing.touch)) {
        Icon(
            painterResource(ColaIcons.MoreVert),
            contentDescription = stringResource(R.string.safety_more),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (target != null && safety != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.safety_report)) },
                leadingIcon = { Icon(painterResource(ColaIcons.Flag), contentDescription = null) },
                onClick = {
                    expanded = false
                    if (signedIn) reporting = true else onSignIn()
                },
            )
        }
        if (block != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (block.blocked) R.string.safety_unblock else R.string.safety_block
                        )
                    )
                },
                leadingIcon = { Icon(painterResource(ColaIcons.Block), contentDescription = null) },
                onClick = {
                    expanded = false
                    if (signedIn) block.onToggle() else onSignIn()
                },
            )
        }
    }
    if (reporting && target != null && safety != null) {
        ReportDialog(target, safety, onDismiss = { reporting = false })
    }
}

/** The reasons, in the order the site shows them, with the words the person reads. */
private val Reasons =
    listOf(
        ReportReason.Spam to R.string.report_reason_spam,
        ReportReason.Abuse to R.string.report_reason_abuse,
        ReportReason.Inappropriate to R.string.report_reason_inappropriate,
        ReportReason.Copyright to R.string.report_reason_copyright,
        ReportReason.Other to R.string.report_reason_other,
    )

/** A report of one thing: the reason, one button, and a thanks. */
@Composable
fun ReportDialog(target: ReportTarget, safety: SafetyRepository, onDismiss: () -> Unit) {
    val viewModel =
        viewModel(key = "report:${target.kind}:${target.id}") { ReportViewModel(safety, target) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close = {
        viewModel.reset()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = { if (!state.sending) close() },
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (state.sent) {
                    Text(
                        stringResource(R.string.report_thanks),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    Text(
                        stringResource(R.string.report_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.selectableGroup()) {
                        Reasons.forEach { (reason, label) ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = Spacing.touch)
                                    .selectable(
                                        selected = state.reason == reason,
                                        enabled = !state.sending,
                                        onClick = { viewModel.choose(reason) },
                                        role = Role.RadioButton,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                            ) {
                                RadioButton(selected = state.reason == reason, onClick = null)
                                Text(stringResource(label))
                            }
                        }
                    }
                    state.error?.let {
                        Text(
                            it.resolve(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.sent) {
                TextButton(onClick = close, modifier = Modifier.heightIn(min = Spacing.touch)) {
                    Text(stringResource(R.string.report_done))
                }
            } else {
                TextButton(
                    onClick = viewModel::send,
                    enabled = state.canSend,
                    modifier = Modifier.heightIn(min = Spacing.touch),
                ) {
                    Text(
                        stringResource(
                            if (state.sending) R.string.report_sending else R.string.report_send
                        )
                    )
                }
            }
        },
        dismissButton = {
            if (!state.sent) {
                TextButton(
                    onClick = close,
                    enabled = !state.sending,
                    modifier = Modifier.heightIn(min = Spacing.touch),
                ) {
                    Text(stringResource(R.string.report_cancel))
                }
            }
        },
    )
}

/**
 * The report menu of a page whose object has an author. Nothing for the author themselves (the
 * server refuses a report of one's own content, and a menu that can only fail is not offered); a
 * guest is asked to sign in first.
 */
@Composable
fun ReportMenu(
    target: ReportTarget,
    authorId: UserId?,
    safety: SafetyRepository?,
    auth: AuthActions,
) {
    val state by auth.state.collectAsStateWithLifecycle()
    val signedIn = state is AuthState.SignedIn
    val me = (state as? AuthState.SignedIn)?.account?.id
    if (authorId != null && authorId == me) return
    SafetyMenu(target, safety, signedIn, onSignIn = LocalSignInRequest.current)
}
