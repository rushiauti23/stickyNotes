package dev.rushi.stickynotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {

    @Test
    fun `tags are found in title and body, lower-cased and de-duplicated`() {
        val note = Note(title = "Plan #Work", content = "call bank #work #ideas_2026")
        assertEquals(setOf("work", "ideas_2026"), note.tags())
    }

    @Test
    fun `hash inside a word or an email is not a tag`() {
        val note = Note(content = "issue a#b, C# code, mail me@site.com#top")
        assertTrue(note.tags().isEmpty())
    }

    @Test
    fun `tags support non-English letters`() {
        assertEquals(setOf("खरेदी", "café"), Note(content = "#खरेदी and #Café").tags())
    }

    @Test
    fun `checklist markers are not part of searchable text`() {
        val note = Note(content = "[ ] milk\n[x] eggs", isChecklist = true)
        assertEquals("\nmilk\neggs", note.searchableText())
        assertFalse(note.searchableText().contains("[x]"))
    }

    @Test
    fun `board shows only live notes`() {
        val now = 1_000_000L
        assertTrue(Note().isOnBoard(now))
        assertFalse(Note(archived = true).isOnBoard(now))
        assertFalse(Note(deletedAt = now - 1).isOnBoard(now))
        assertFalse(Note(expiresAt = now).isOnBoard(now))
        assertTrue(Note(expiresAt = now + 1).isOnBoard(now))
    }
}
