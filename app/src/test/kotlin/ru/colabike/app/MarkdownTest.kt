package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.colabike.app.markdown.Block
import ru.colabike.app.markdown.Inline
import ru.colabike.app.markdown.Markdown

/** What other people write is shown as text with a little structure, never as markup that acts. */
class MarkdownTest {
    private fun blocks(source: String) = Markdown.parse(source)

    private fun inlines(source: String): List<Inline> =
        (blocks(source).single() as Block.Paragraph).text

    @Test
    fun `headings, paragraphs and rules are blocks`() {
        val result = blocks("# Один\n\nТекст\nвторая строка\n\n---\n\n### Три ###")

        assertThat(result[0]).isEqualTo(Block.Heading(1, listOf(Inline.Text("Один"))))
        val paragraph = result[1] as Block.Paragraph
        assertThat(Markdown.plain(paragraph.text)).isEqualTo("Текст\nвторая строка")
        assertThat(result[2]).isEqualTo(Block.Rule)
        assertThat(result[3]).isEqualTo(Block.Heading(3, listOf(Inline.Text("Три"))))
    }

    @Test
    fun `lists keep their numbers and nesting, a continuation line stays with its item`() {
        val result = blocks("- раз\n  и ещё\n- два\n  - вложенный\n\n1. первый\n2) второй")

        val items = result.filterIsInstance<Block.Item>()
        assertThat(items.map { it.depth }).containsExactly(0, 0, 1, 0, 0).inOrder()
        assertThat(items.map { it.number }).containsExactly(null, null, null, 1, 2).inOrder()
        assertThat(Markdown.plain(items[0].text)).isEqualTo("раз\nи ещё")
    }

    @Test
    fun `a list starts a block even right under a paragraph, a quote collects its lines`() {
        val result = blocks("Вступление\n- пункт\n> цитата\n> дальше")

        assertThat(result.map { it::class.simpleName })
            .containsExactly("Paragraph", "Item", "Quote")
            .inOrder()
        assertThat(Markdown.plain((result[2] as Block.Quote).text)).isEqualTo("цитата\nдальше")
    }

    @Test
    fun `fenced code is kept as it is, even with markup inside and no closing fence`() {
        val closed = blocks("```kotlin\nval a = **b**\n# not a heading\n```\nПосле")
        assertThat(closed[0]).isEqualTo(Block.Code("val a = **b**\n# not a heading"))
        assertThat(closed[1]).isInstanceOf(Block.Paragraph::class.java)

        val open = blocks("```\nбез конца")
        assertThat(open.single()).isEqualTo(Block.Code("без конца"))
    }

    @Test
    fun `bold, italic and code nest and plain text stays`() {
        val result = inlines("Это **важно и *очень*** и _наклон_, а `код` тоже")

        assertThat(result.filterIsInstance<Inline.Strong>()).hasSize(1)
        assertThat(result.filterIsInstance<Inline.Emphasis>()).hasSize(1)
        assertThat(result.filterIsInstance<Inline.Code>().single().text).isEqualTo("код")
        assertThat(Markdown.plain(result)).isEqualTo("Это важно и очень и наклон, а код тоже")
    }

    @Test
    fun `an underscore inside a word and a lone star are not emphasis`() {
        assertThat(Markdown.plain(inlines("snake_case_name и 2 * 3 * 4")))
            .isEqualTo("snake_case_name и 2 * 3 * 4")
        assertThat(inlines("snake_case_name").filterIsInstance<Inline.Emphasis>()).isEmpty()
        assertThat(Markdown.plain(inlines("**не закрыто"))).isEqualTo("**не закрыто")
    }

    @Test
    fun `an https link opens, anything else is only its label`() {
        val links =
            inlines(
                "[сайт](https://example.test/a?b=1) и [плохо](javascript:alert(1)) и [x](http://example.test)"
            )

        val link = links.filterIsInstance<Inline.Link>().single()
        assertThat(link.url).isEqualTo("https://example.test/a?b=1")
        assertThat(Markdown.plain(links)).isEqualTo("сайт и плохо и x")
    }

    @Test
    fun `an address with credentials is not a link`() {
        val result = inlines("[вход](https://user:pass@example.test/) и https://a:b@example.test/x")

        assertThat(result.filterIsInstance<Inline.Link>()).isEmpty()
    }

    @Test
    fun `bare https addresses and autolinks are links without the dot that ends the sentence`() {
        val result =
            inlines(
                "Смотри https://example.test/page. И <https://example.test/b> ещё (https://example.test/c)"
            )

        val urls = result.filterIsInstance<Inline.Link>().map { it.url }
        assertThat(urls)
            .containsExactly(
                "https://example.test/page",
                "https://example.test/b",
                "https://example.test/c",
            )
            .inOrder()
        assertThat(Markdown.plain(result)).contains("page. И")
    }

    @Test
    fun `a picture is not loaded, its description stays as text`() {
        val result = inlines("Вот ![рама сбоку](https://example.test/a.png) она")

        assertThat(result.filterIsInstance<Inline.Link>()).isEmpty()
        assertThat(Markdown.plain(result)).isEqualTo("Вот рама сбоку она")
    }

    @Test
    fun `raw html and script stay as text`() {
        val result =
            inlines(
                "<script>alert(1)</script> <b>жирный</b> <a href=\"https://example.test\">x</a>"
            )

        assertThat(Markdown.plain(result))
            .isEqualTo(
                "<script>alert(1)</script> <b>жирный</b> <a href=\"https://example.test\">x</a>"
            )
        assertThat(result.filterIsInstance<Inline.Link>()).isEmpty()
    }

    @Test
    fun `an escaped marker is a letter and a backslash at the end of a line breaks it`() {
        val result = inlines("\\*не курсив\\*\\\nновая строка")

        assertThat(Markdown.plain(result)).isEqualTo("*не курсив*\nновая строка")
        assertThat(result).contains(Inline.Break)
    }

    @Test
    fun `hostile or huge input is linear and bounded`() {
        val started = System.nanoTime()
        Markdown.parse("[".repeat(50_000))
        Markdown.parse("*a ".repeat(30_000))
        Markdown.parse("_".repeat(60_000))
        Markdown.parse("`".repeat(60_000))
        Markdown.parse("(".repeat(200_000))
        Markdown.parse("-".repeat(100_000))
        Markdown.parse("- ".repeat(50_000))
        Markdown.parse("# " + "#".repeat(100_000))
        Markdown.parse("> ".repeat(50_000))
        Markdown.parse("1. ".repeat(30_000))
        Markdown.parse("```".repeat(30_000))
        Markdown.parse("[a](".repeat(20_000))
        Markdown.parse("\n".repeat(100_000))
        val nested = "**".repeat(500) + "x" + "**".repeat(500)
        Markdown.parse(nested)

        // About a second on a quiet machine. A quadratic parser needs minutes for these inputs, so
        // the bound is far above what a busy CI machine adds (it failed at 5 s while four test
        // JVMs shared the CPU) and still far below what super-linear growth costs.
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(30_000)
        assertThat(Markdown.parse("я".repeat(Markdown.MAX_LENGTH + 10_000)).size).isEqualTo(1)
        val text =
            Markdown.plain(
                (Markdown.parse("я".repeat(Markdown.MAX_LENGTH + 10)).single() as Block.Paragraph)
                    .text
            )
        assertThat(text).hasLength(Markdown.MAX_LENGTH)
    }
}
