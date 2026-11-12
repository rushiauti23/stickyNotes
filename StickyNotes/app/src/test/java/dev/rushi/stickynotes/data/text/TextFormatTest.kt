package dev.rushi.stickynotes.data.text

import dev.rushi.stickynotes.data.text.Markup.InlineKind
import dev.rushi.stickynotes.data.text.Markup.LineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextFormatTest {

    @Test
    fun `bold wraps the selection and keeps it selected`() {
        val r = TextFormat.toggleInline(Edit("buy milk now", 4, 8), InlineKind.Bold)
        assertEquals("buy **milk** now", r.text)
        assertEquals("milk", r.text.substring(r.min, r.max))
    }

    @Test
    fun `bold again removes it`() {
        val once = TextFormat.toggleInline(Edit("buy milk now", 4, 8), InlineKind.Bold)
        val twice = TextFormat.toggleInline(once, InlineKind.Bold)
        assertEquals("buy milk now", twice.text)
        assertEquals("milk", twice.text.substring(twice.min, twice.max))
    }

    @Test
    fun `selection including the markers is unwrapped`() {
        val r = TextFormat.toggleInline(Edit("a ~~b~~ c", 2, 7), InlineKind.Strike)
        assertEquals("a b c", r.text)
    }

    @Test
    fun `caret inside a word formats the whole word`() {
        val r = TextFormat.toggleInline(Edit("hello world", 8), InlineKind.Italic)
        assertEquals("hello _world_", r.text)
    }

    @Test
    fun `caret on empty space inserts a pair and puts the caret inside`() {
        val r = TextFormat.toggleInline(Edit("a  b", 2), InlineKind.Highlight)
        assertEquals("a ==== b", r.text)
        assertEquals(4, r.start)
    }

    @Test
    fun `surrounding spaces stay outside the markers`() {
        val r = TextFormat.toggleInline(Edit("x  word  y", 1, 9), InlineKind.Bold)
        assertEquals("x  **word**  y", r.text)
    }

    @Test
    fun `multi-line selection is formatted per line`() {
        val r = TextFormat.toggleInline(Edit("one\ntwo", 0, 7), InlineKind.Underline)
        assertEquals("++one++\n++two++", r.text)
    }

    @Test
    fun `bullet list on several lines, and off again`() {
        val on = TextFormat.toggleLine(Edit("milk\neggs\nbread", 0, 15), LineKind.Bullet)
        assertEquals("- milk\n- eggs\n- bread", on.text)
        val off = TextFormat.toggleLine(on, LineKind.Bullet)
        assertEquals("milk\neggs\nbread", off.text)
    }

    @Test
    fun `numbered list counts lines and replaces bullets`() {
        val r = TextFormat.toggleLine(Edit("- a\n- b\n\n- c", 0, 12), LineKind.Numbered)
        assertEquals("1. a\n2. b\n\n3. c", r.text)
    }

    @Test
    fun `heading on the caret line only, caret moves with the text`() {
        val r = TextFormat.toggleLine(Edit("intro\nTitle here\nmore", 8), LineKind.Heading1)
        assertEquals("intro\n# Title here\nmore", r.text)
        assertEquals(10, r.start)
    }

    @Test
    fun `selection ending right after a newline does not touch the next line`() {
        val r = TextFormat.toggleLine(Edit("a\nb", 0, 2), LineKind.Quote)
        assertEquals("> a\nb", r.text)
    }

    @Test
    fun `enter continues bullet, number and quote lines`() {
        assertEquals(Edit("- a\n- ", 6), TextFormat.continueList("- a", Edit("- a\n", 4)))
        assertEquals(Edit("1. a\n2. ", 8), TextFormat.continueList("1. a", Edit("1. a\n", 5)))
        assertEquals(Edit("> a\n> ", 6), TextFormat.continueList("> a", Edit("> a\n", 4)))
        assertEquals(Edit("  * a\n  * ", 10), TextFormat.continueList("  * a", Edit("  * a\n", 6)))
    }

    @Test
    fun `enter on an empty item ends the list`() {
        val r = TextFormat.continueList("- a\n- ", Edit("- a\n- \n", 7))
        assertEquals(Edit("- a\n", 4), r)
    }

    @Test
    fun `plain lines, pastes and deletions are left alone`() {
        assertNull(TextFormat.continueList("hello", Edit("hello\n", 6)))
        assertNull(TextFormat.continueList("- a", Edit("- a\nb", 5)))
        assertNull(TextFormat.continueList("- a\n", Edit("- a", 3)))
    }

    @Test
    fun `clear formatting removes inline and line markers`() {
        val r = TextFormat.clearFormatting(Edit("# **Big** _idea_\n- ==x==", 0, 23))
        assertEquals("Big idea\nx", r.text)
    }
}
