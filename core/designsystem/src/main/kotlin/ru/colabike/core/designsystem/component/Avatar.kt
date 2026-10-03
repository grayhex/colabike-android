package ru.colabike.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ru.colabike.core.designsystem.theme.PillShape

/**
 * A round profile picture, or the first letter of the name on the quiet secondary surface (the
 * reference avatar). Decorative: the name next to it is what TalkBack reads.
 */
@Composable
fun Avatar(name: String, url: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontSize = (size.value * 0.42f).sp,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
