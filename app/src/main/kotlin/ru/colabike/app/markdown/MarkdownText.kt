package ru.colabike.app.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.theme.Spacing

/**
 * [source] as text with structure. Links open through [onOpenLink] (the app passes the opener that
 * takes `https` only and sends no token). Nothing in the text can make the screen load or run
 * anything.
 */
@Composable
fun MarkdownText(source: String, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(source) { Markdown.parse(source) }
    Column(modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                val tight = block is Block.Item && blocks[index - 1] is Block.Item
                Spacer(Modifier.height(if (tight) Spacing.xs else Spacing.m))
            }
            BlockContent(block, onOpenLink)
        }
    }
}

@Composable
private fun BlockContent(block: Block, onOpenLink: (String) -> Unit) {
    when (block) {
        is Block.Heading ->
            Text(
                annotated(block.text, onOpenLink),
                style =
                    when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        else -> MaterialTheme.typography.titleMedium
                    },
                modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
            )
        is Block.Paragraph ->
            Text(annotated(block.text, onOpenLink), style = MaterialTheme.typography.bodyLarge)
        is Block.Item ->
            Row(
                Modifier.padding(start = (block.depth * 16).dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                val marker = block.number?.let { "$it." } ?: "•"
                // A bullet is decoration; a number is part of what is read.
                Text(
                    marker,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier.widthIn(min = 20.dp).let {
                            if (block.number == null) it.clearAndSetSemantics {} else it
                        },
                )
                Text(
                    annotated(block.text, onOpenLink),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        is Block.Quote ->
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Spacer(
                    Modifier.width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Text(
                    annotated(block.text, onOpenLink),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            }
        is Block.Code ->
            Text(
                block.text,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier =
                    Modifier.fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.shapes.small,
                        )
                        .padding(Spacing.m),
            )
        Block.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun annotated(inlines: List<Inline>, onOpenLink: (String) -> Unit): AnnotatedString {
    val link = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    return remember(inlines, link, codeBackground, onOpenLink) {
        buildAnnotatedString {
            val styles =
                TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
            fun add(items: List<Inline>) {
                items.forEach { item ->
                    when (item) {
                        is Inline.Text -> append(item.text)
                        is Inline.Strong ->
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { add(item.content) }
                        is Inline.Emphasis ->
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { add(item.content) }
                        is Inline.Code ->
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = codeBackground,
                                )
                            ) {
                                append(item.text)
                            }
                        is Inline.Link ->
                            withLink(
                                LinkAnnotation.Clickable(item.url, styles) { onOpenLink(item.url) }
                            ) {
                                add(item.content)
                            }
                        Inline.Break -> append('\n')
                    }
                }
            }
            add(inlines)
        }
    }
}
