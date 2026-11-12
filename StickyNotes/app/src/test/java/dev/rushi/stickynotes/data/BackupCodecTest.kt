package dev.rushi.stickynotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `round trip keeps the content that matters and never keeps ids`() {
        val original = Note(
            id = 42,
            title = "Groceries",
            content = "[ ] milk\n[x] eggs",
            colorIndex = 3,
            pinned = true,
            isChecklist = true,
            archived = true,
            createdAt = now - 10 * day,
            updatedAt = now - day,
            reminderAt = now + day,
            expiresAt = now + 2 * day,
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(listOf(original), now), now).single()
        assertEquals(original.copy(id = 0), decoded)
    }

    @Test
    fun `rejects files that are not our backup`() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json", now) }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"notes":[]}""", now) }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("""{"format":"sticky-notes-backup","version":99,"notes":[]}""", now)
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("""{"format":"sticky-notes-backup","version":1}""", now)
        }
    }

    @Test
    fun `drops vanished and empty notes, past reminders and trash state`() {
        val json = """
            {"format":"sticky-notes-backup","version":1,"notes":[
              {"title":"gone","content":"x","expiresAt":${now - 1}},
              {"title":"","content":"   "},
              {"title":"keep","content":"y","reminderAt":${now - 5},"deletedAt":${now - 1}}
            ]}
        """.trimIndent()
        val notes = BackupCodec.decode(json, now)
        assertEquals(listOf("keep"), notes.map { it.title })
        assertNull(notes.single().reminderAt)
        assertNull(notes.single().deletedAt)
    }

    @Test
    fun `hostile values are clamped instead of trusted`() {
        val longTitle = "t".repeat(BackupCodec.MAX_TITLE + 500)
        val json = """
            {"format":"sticky-notes-backup","version":1,"notes":[
              {"id":7,"title":"$longTitle","content":null,"colorIndex":999,
               "createdAt":${now + 50 * day},"updatedAt":-5,"pinned":"yes"},
              {"title":12345,"content":"numbers are not text"},
              "not an object"
            ]}
        """.trimIndent()
        val notes = BackupCodec.decode(json, now)
        assertEquals(2, notes.size)
        val first = notes[0]
        assertEquals(0L, first.id)
        assertEquals(BackupCodec.MAX_TITLE, first.title.length)
        assertEquals("", first.content)
        assertEquals(Note.COLOR_COUNT - 1, first.colorIndex)
        assertTrue(first.createdAt <= now)
        assertTrue(first.updatedAt >= first.createdAt)
        assertEquals("", notes[1].title)
    }
}
