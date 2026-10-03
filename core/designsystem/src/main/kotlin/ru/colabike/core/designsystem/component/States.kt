package ru.colabike.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.Spacing

/** Nothing to show yet: what this place is for and, if there is one, the next step. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: Int = ColaIcons.Bike,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(modifier) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null)
            Button(onClick = onAction) { Text(actionLabel) }
    }
}

/** Something failed: say what in words and offer a retry. TalkBack hears it when it appears. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StateLayout(modifier) {
        Icon(
            painterResource(ColaIcons.Error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        OutlinedButton(onClick = onRetry) {
            Icon(
                painterResource(ColaIcons.Refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.cola_retry),
                modifier = Modifier.padding(start = Spacing.s),
            )
        }
    }
}

/** A short wait with nothing to sketch yet (sign-in, a single action). */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    StateLayout(modifier) { CircularProgressIndicator() }
}

@Composable
private fun StateLayout(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        content()
    }
}
