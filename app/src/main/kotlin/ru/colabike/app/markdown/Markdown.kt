package ru.colabike.app.markdown

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A small, safe Markdown for entries written by other people. What it understands: headings,
 * paragraphs, bullet and numbered lists, quotes, fenced and inline code, rules, bold, italic, links
 * and bare `https` addresses. What it never does: build HTML, load pictures, or open anything but
 * an `https` link (which goes to the browser without the account's token). Everything else, a raw
 * `<div>` or an unknown construct included, stays plain text.
 */
sealed interface Block {
    data class Heading(val level: Int, val text: List<Inline>) : Block

    data class Paragraph(val text: List<Inline>) : Block

    /** [depth] is 0 for a top-level item; [number] is set for numbered ones. */
    data class Item(val depth: Int, val number: Int?, val text: List<Inline>) : Block

    data class Quote(val text: List<Inline>) : Block

    data class Code(val text: String) : Block

    data object Rule : Block
}

sealed interface Inline {
    data class Text(val text: String) : Inline

    data class Strong(val content: List<Inline>) : Inline

    data class Emphasis(val content: List<Inline>) : Inline

    data class Code(val text: String) : Inline

    /** [url] is a plain `https` address, always. */
    data class Link(val content: List<Inline>, val url: String) : Inline

    data object Break : Inline
}

object Markdown {
    /** Longest text parsed; the rest is cut, so a hostile body cannot stall the screen. */
    const val MAX_LENGTH = 100_000

    fun parse(source: String): List<Block> =
        BlockParser(source.take(MAX_LENGTH).replace("\r\n", "\n").replace('\r', '\n')).parse()

    /** The text as a person would read it without markup: for tests and for TalkBack fallbacks. */
    fun plain(inlines: List<Inline>): String = buildString {
        inlines.forEach {
            when (it) {
                is Inline.Text -> append(it.text)
                is Inline.Strong -> append(plain(it.content))
                is Inline.Emphasis -> append(plain(it.content))
                is Inline.Code -> append(it.text)
                is Inline.Link -> append(plain(it.content))
                Inline.Break -> append('\n')
            }
        }
    }

    /** A link the app will open: `https`, a host, no credentials in it. */
    internal fun safeUrl(candidate: String): String? =
        candidate
            .trim()
            .toHttpUrlOrNull()
            ?.takeIf { it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty() }
            ?.toString()
}

private val HEADING = Regex("^(#{1,6})[ \t]+(.*?)[ \t]*#*[ \t]*$")
private val ITEM = Regex("^( *)([-*+]|\\d{1,9}[.)])[ \t]+(.*)$")
private val FENCE = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")

/**
 * Three or more of one of `-`, `*`, `_`, spaces between them allowed. By hand: a repeated regex
 * group recurses once per repeat and a long line of text from outside would overflow the stack.
 */
private fun isRule(line: String): Boolean {
    val text = line.trim()
    val marker = text.firstOrNull() ?: return false
    if (marker != '-' && marker != '*' && marker != '_') return false
    if (line.length - line.trimStart().length > 3) return false
    var count = 0
    for (c in text) {
        when (c) {
            marker -> count++
            ' ',
            '\t' -> Unit
            else -> return false
        }
    }
    return count >= 3
}

private class BlockParser(source: String) {
    private val lines = source.split('\n')
    private var at = 0
    private val blocks = mutableListOf<Block>()

    fun parse(): List<Block> {
        while (at < lines.size) {
            val line = lines[at]
            when {
                line.isBlank() -> at++
                FENCE.matches(line) -> fenced()
                HEADING.matches(line) -> heading(line)
                isRule(line) -> {
                    blocks += Block.Rule
                    at++
                }
                line.trimStart().startsWith(">") -> quote()
                ITEM.matches(line) -> item(line)
                else -> paragraph()
            }
        }
        return blocks
    }

    private fun fenced() {
        val open = FENCE.matchEntire(lines[at])!!.groupValues[1]
        at++
        val body = mutableListOf<String>()
        while (at < lines.size && !lines[at].trimStart().startsWith(open.take(3))) {
            body += lines[at]
            at++
        }
        if (at < lines.size) at++ // the closing fence
        blocks += Block.Code(body.joinToString("\n"))
    }

    private fun heading(line: String) {
        val match = HEADING.matchEntire(line)!!
        blocks += Block.Heading(match.groupValues[1].length, Inlines.parse(match.groupValues[2]))
        at++
    }

    private fun quote() {
        val text = mutableListOf<String>()
        while (at < lines.size && lines[at].trimStart().startsWith(">")) {
            text += lines[at].trimStart().removePrefix(">").removePrefix(" ")
            at++
        }
        blocks += Block.Quote(Inlines.parse(text.joinToString("\n")))
    }

    private fun item(line: String) {
        val match = ITEM.matchEntire(line)!!
        val indent = match.groupValues[1].length
        val marker = match.groupValues[2]
        val number = marker.takeIf { it.first().isDigit() }?.dropLast(1)?.toIntOrNull()
        val text = StringBuilder(match.groupValues[3])
        at++
        // A line indented under the item and not itself an item continues it.
        while (
            at < lines.size &&
                lines[at].isNotBlank() &&
                lines[at].startsWith("  ") &&
                !ITEM.matches(lines[at])
        ) {
            text.append('\n').append(lines[at].trim())
            at++
        }
        blocks +=
            Block.Item((indent / 2).coerceAtMost(MAX_DEPTH), number, Inlines.parse(text.toString()))
    }

    private fun paragraph() {
        val text = mutableListOf<String>()
        while (
            at < lines.size && lines[at].isNotBlank() && !startsBlock(lines[at], text.isEmpty())
        ) {
            text += lines[at].trim()
            at++
        }
        blocks += Block.Paragraph(Inlines.parse(text.joinToString("\n")))
    }

    /**
     * Whether a line ends the paragraph above it. The first line of a paragraph always starts it.
     */
    private fun startsBlock(line: String, first: Boolean) =
        !first &&
            (FENCE.matches(line) ||
                HEADING.matches(line) ||
                isRule(line) ||
                line.trimStart().startsWith(">") ||
                ITEM.matches(line))

    private companion object {
        const val MAX_DEPTH = 3
    }
}

/** Inline parsing by hand: linear, no backtracking regexes on text from outside. */
internal object Inlines {
    private const val MAX_DEPTH = 6

    fun parse(text: String): List<Inline> = Scanner(text).run(0)

    private class Scanner(val s: String) {
        var i = 0

        fun run(depth: Int): List<Inline> {
            val out = mutableListOf<Inline>()
            val plain = StringBuilder()

            fun flush() {
                if (plain.isNotEmpty()) {
                    out += Inline.Text(plain.toString())
                    plain.setLength(0)
                }
            }

            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' && i + 1 < s.length && s[i + 1] == '\n' -> {
                        flush()
                        out += Inline.Break
                        i += 2
                    }
                    c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE -> {
                        plain.append(s[i + 1])
                        i += 2
                    }
                    c == '\n' -> {
                        flush()
                        out += Inline.Break
                        i++
                    }
                    c == '`' -> {
                        val code = codeSpan()
                        if (code != null) {
                            flush()
                            out += code
                        } else {
                            plain.append(takeRun('`'))
                        }
                    }
                    (c == '*' || c == '_') && depth < MAX_DEPTH -> {
                        val styled = emphasis(c, depth)
                        if (styled != null) {
                            flush()
                            out += styled
                        } else {
                            plain.append(takeRun(c))
                        }
                    }
                    c == '!' && s.startsWith("![", i) -> {
                        val alt = image()
                        if (alt != null) {
                            flush()
                            if (alt.isNotEmpty()) out += Inline.Text(alt)
                        } else {
                            plain.append(c)
                            i++
                        }
                    }
                    c == '[' && depth < MAX_DEPTH -> {
                        val link = link(depth)
                        if (link != null) {
                            flush()
                            out += link
                        } else {
                            plain.append(c)
                            i++
                        }
                    }
                    c == '<' -> {
                        val auto = autolink()
                        if (auto != null) {
                            flush()
                            out += auto
                        } else {
                            plain.append(c)
                            i++
                        }
                    }
                    c == 'h' && s.startsWith("https://", i) && atWordStart() -> {
                        val bare = bareUrl()
                        if (bare != null) {
                            flush()
                            out += bare
                        } else {
                            plain.append(c)
                            i++
                        }
                    }
                    else -> {
                        plain.append(c)
                        i++
                    }
                }
            }
            flush()
            return out
        }

        /** An address starts after a space or an opening bracket, not inside an attribute. */
        /** Like `indexOf`, but gives up after [WINDOW] characters. */
        private fun find(needle: String, from: Int): Int {
            val last = minOf(s.length - needle.length, from + WINDOW)
            var j = from
            while (j <= last) {
                if (s.startsWith(needle, j)) return j
                j++
            }
            return -1
        }

        private fun atWordStart() = i == 0 || s[i - 1].isWhitespace() || s[i - 1] in "([,;:"

        private fun takeRun(ch: Char): String {
            val start = i
            while (i < s.length && s[i] == ch) i++
            return s.substring(start, i)
        }

        private fun codeSpan(): Inline.Code? {
            val start = i
            var ticks = 0
            while (start + ticks < s.length && s[start + ticks] == '`') ticks++
            val fence = "`".repeat(ticks)
            val close = find(fence, start + ticks)
            if (close < 0) return null
            // The closing run must be exactly as long as the opening one.
            var end = close
            while (end < s.length && s[end] == '`') end++
            if (end - close != ticks) return null
            i = end
            return Inline.Code(s.substring(start + ticks, close).trim(' '))
        }

        private fun emphasis(marker: Char, depth: Int): Inline? {
            val start = i
            val double = s.startsWith("$marker$marker", i)
            val width = if (double) 2 else 1
            val contentStart = start + width
            if (
                contentStart >= s.length ||
                    s[contentStart].isWhitespace() ||
                    s[contentStart] == marker
            ) {
                return null
            }
            // Inside a word an underscore is just an underscore (snake_case).
            if (marker == '_' && start > 0 && s[start - 1].isLetterOrDigit()) return null
            // One pass over the window: the first closer that has text before it and no space
            // right before it.
            val last = minOf(s.length - width, contentStart + WINDOW)
            var close = contentStart + 1
            while (close <= last) {
                if (
                    s[close] == marker &&
                        (width == 1 || s[close + 1] == marker) &&
                        !s[close - 1].isWhitespace() &&
                        s.getOrNull(close + width) != marker &&
                        !(marker == '_' && s.getOrNull(close + width)?.isLetterOrDigit() == true)
                ) {
                    val inner = Scanner(s.substring(contentStart, close)).run(depth + 1)
                    i = close + width
                    return if (double) Inline.Strong(inner) else Inline.Emphasis(inner)
                }
                close++
            }
            return null
        }

        /** The matching `]` of the `[` at [open], with one level of nesting allowed. */
        private fun closingBracket(open: Int): Int {
            var level = 0
            var j = open
            while (j < s.length) {
                when (s[j]) {
                    '\\' -> j++
                    '[' -> level++
                    ']' -> {
                        level--
                        if (level == 0) return j
                    }
                }
                if (j - open > WINDOW) return -1
                j++
            }
            return -1
        }

        /**
         * `(address "title")` right after `]`: the address up to the first space or the `)` that
         * closes it, parentheses inside the address counted.
         */
        private fun destination(from: Int): Pair<String, Int>? {
            if (from >= s.length || s[from] != '(') return null
            var j = from + 1
            val start = j
            var level = 0
            while (j < s.length && !s[j].isWhitespace() && j - start <= WINDOW) {
                if (s[j] == '(') level++
                if (s[j] == ')') {
                    if (level == 0) break
                    level--
                }
                j++
            }
            val address = s.substring(start, j)
            // Skip an optional title: up to the closing parenthesis on the same line.
            val titleStart = j
            while (j < s.length && s[j] != ')' && s[j] != '\n' && j - titleStart <= WINDOW) j++
            if (j >= s.length || s[j] != ')') return null
            return address to j + 1
        }

        private fun link(depth: Int): Inline? {
            val close = closingBracket(i)
            if (close < 0) return null
            val (address, end) = destination(close + 1) ?: return null
            val label = s.substring(i + 1, close)
            val url = Markdown.safeUrl(address)
            val content = Scanner(label).run(depth + 1)
            i = end
            // An address the app will not open leaves the label as plain text.
            return if (url != null && content.isNotEmpty()) Inline.Link(content, url)
            else Inline.Text(Markdown.plain(content))
        }

        private fun image(): String? {
            val close = closingBracket(i + 1)
            if (close < 0) return null
            val (_, end) = destination(close + 1) ?: return null
            val alt = s.substring(i + 2, close)
            i = end
            return alt
        }

        private fun autolink(): Inline? {
            val close = find(">", i)
            if (close < 0) return null
            val inner = s.substring(i + 1, close)
            if (inner.any { it.isWhitespace() }) return null
            val url = Markdown.safeUrl(inner) ?: return null
            if (!inner.startsWith("https://")) return null
            i = close + 1
            return Inline.Link(listOf(Inline.Text(inner)), url)
        }

        private fun bareUrl(): Inline? {
            var j = i
            while (j < s.length && !s[j].isWhitespace() && s[j] !in "<>\"" && j - i <= WINDOW) j++
            var raw = s.substring(i, j)
            // Punctuation that ends a sentence is not part of the address.
            while (raw.isNotEmpty() && raw.last() in ".,;:!?") raw = raw.dropLast(1)
            while (raw.endsWith(")") && raw.count { it == ')' } > raw.count { it == '(' }) {
                raw = raw.dropLast(1)
            }
            val url = Markdown.safeUrl(raw) ?: return null
            i += raw.length
            return Inline.Link(listOf(Inline.Text(raw)), url)
        }
    }

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!<>~|"

    /**
     * How far a marker looks for its partner. Without a limit every unmatched `*` or `[` in a long
     * text would scan to the end of it, and a text made of them would take quadratic time.
     */
    private const val WINDOW = 2_000
}
