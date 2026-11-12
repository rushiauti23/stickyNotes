package dev.rushi.stickynotes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.rushi.stickynotes.data.text.Markup

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    /** Plain text, or for checklists one item per line: "[ ] todo" / "[x] done". */
    val content: String = "",
    val colorIndex: Int = 0,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Added in DB version 2 (see NoteDatabase.MIGRATION_1_2).
    @ColumnInfo(defaultValue = "0") val isChecklist: Boolean = false,
    /** When to show a reminder notification (epoch millis), or null. */
    val reminderAt: Long? = null,
    /** When this note deletes itself (epoch millis), or null. */
    val expiresAt: Long? = null,
    // Added in DB version 3 (see NoteDatabase.MIGRATION_2_3).
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    /** When the note was moved to Trash (epoch millis), or null if it isn't in Trash. */
    val deletedAt: Long? = null,
) {
    companion object {
        /** Number of paper colours; keep in sync with ui.theme.NoteColors. */
        const val COLOR_COUNT = 8
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** Title + body without checklist or formatting markers, for search, tags and previews. */
fun Note.plainBody(): String =
    if (isChecklist) Checklist.toPlainText(Checklist.parse(content)) else Markup.strip(content)

fun Note.searchableText(): String = "$title\n${plainBody()}"

private val TagRegex = Regex("""(?<![\p{L}\p{M}\p{N}_])#([\p{L}\p{M}\p{N}_]{1,30})""")

/** Lower-cased #hashtags found in the title or body. */
fun Note.tags(): Set<String> =
    TagRegex.findAll(searchableText()).map { it.groupValues[1].lowercase() }.toSet()

fun Note.isExpired(now: Long = System.currentTimeMillis()): Boolean =
    expiresAt != null && expiresAt <= now

val Note.isTrashed: Boolean get() = deletedAt != null

/** Shown on the main board: not archived, not in Trash, not vanished. */
fun Note.isOnBoard(now: Long = System.currentTimeMillis()): Boolean =
    !archived && !isTrashed && !isExpired(now)
