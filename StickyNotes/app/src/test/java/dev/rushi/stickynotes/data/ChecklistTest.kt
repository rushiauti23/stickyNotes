package dev.rushi.stickynotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {

    @Test
    fun `parse reads checked and unchecked items`() {
        val items = Checklist.parse("[ ] milk\n[x] eggs\n[X] bread")
        assertEquals(listOf("milk", "eggs", "bread"), items.map { it.text })
        assertEquals(listOf(false, true, true), items.map { it.checked })
    }

    @Test
    fun `parse treats lines without a marker as unchecked items and skips blank lines`() {
        val items = Checklist.parse("plain line\n\n   \n[ ] real")
        assertEquals(listOf("plain line", "real"), items.map { it.text })
        assertTrue(items.none { it.checked })
    }

    @Test
    fun `serialize then parse round-trips and drops empty rows`() {
        val items = listOf(
            Checklist.newItem("milk"),
            Checklist.newItem("", checked = true),
            Checklist.newItem("eggs", checked = true),
        )
        val text = Checklist.serialize(items)
        assertEquals("[ ] milk\n[x] eggs", text)
        val back = Checklist.parse(text)
        assertEquals(listOf("milk" to false, "eggs" to true), back.map { it.text to it.checked })
    }

    @Test
    fun `serialize flattens newlines inside an item so one item stays one line`() {
        val text = Checklist.serialize(listOf(Checklist.newItem("two\nlines")))
        assertEquals("[ ] two lines", text)
    }

    @Test
    fun `plain text conversion both ways`() {
        val items = Checklist.fromPlainText("a\n\nb\n")
        assertEquals(listOf("a", "b"), items.map { it.text })
        assertEquals("a\nb", Checklist.toPlainText(items))
    }

    @Test
    fun `new items get unique ids`() {
        val ids = List(100) { Checklist.newItem().id }
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(ids.contains(0L))
    }
}
