package dev.rushi.stickynotes.data.text

import dev.rushi.stickynotes.data.text.Markup.InlineKind
import dev.rushi.stickynotes.data.text.Markup.LineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupTest {

    @Test
    fun `line prefixes are recognised`() {
        assertEquals(LineKind.Heading1, Markup.linePrefix("# Title").kind)
        assertEquals(LineKind.Heading2, Markup.linePrefix("## Sub").kind)
        assertEquals(LineKind.Bullet, Markup.linePrefix("- item").kind)
        assertEquals(LineKind.Bullet, Markup.linePrefix("  • item").kind)
        assertEquals(7, Markup.linePrefix("12. item").number?.let { 7 })
        assertEquals(LineKind.Numbered, Markup.linePrefix("3) item").kind)
        assertEquals(LineKind.Quote, Markup.linePrefix("> said").kind)
        assertEquals(LineKind.Plain, Markup.linePrefix("#tag is not a heading").kind)
        assertEquals(LineKind.Plain, Markup.linePrefix("-not a bullet").kind)
    }

    @Test
    fun `inline spans are found and must hug their text`() {
        val spans = Markup.inlineSpans("**b** _i_ ++u++ ~~s~~ ==h== `c` ** no **")
        assertEquals(
            setOf(InlineKind.Bold, InlineKind.Italic, InlineKind.Underline, InlineKind.Strike, InlineKind.Highlight, InlineKind.Code),
            spans.map { it.kind }.toSet(),
        )
        assertEquals(6, spans.size)
    }

    @Test
    fun `underscores inside words and tags are not italic`() {
        assertTrue(Markup.inlineSpans("snake_case_name and #ideas_2026_x").isEmpty())
    }

    @Test
    fun `spans never cross lines`() {
        assertTrue(Markup.inlineSpans("**start\nend**").isEmpty())
    }

    @Test
    fun `strip gives readable text`() {
        val text = "# Plan\n- **milk**\n2. _eggs_\n> ==quiet==\nplain ++u++ ~~x~~ `y`"
        assertEquals("Plan\n• milk\n2. eggs\nquiet\nplain u x y", Markup.strip(text))
    }

    @Test
    fun `nested markers are all removed`() {
        assertEquals("both", Markup.stripInline("**_both_**"))
    }
}
