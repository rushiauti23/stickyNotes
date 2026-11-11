package dev.rushi.stickynotes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
)

/** Title + body without checklist markers, for search, tags and previews. */
fun Note.plainBody(): String =
    if (isChecklist) Checklist.toPlainText(Checklist.parse(content)) else content

fun Note.searchableText(): String = "$title\n${plainBody()}"

private val TagRegex = Regex("""(?<![\p{L}\p{N}_])#([\p{L}\p{N}_]{1,30})""")

/** Lower-cased #hashtags found in the title or body. */
fun Note.tags(): Set<String> =
    TagRegex.findAll(searchableText()).map { it.groupValues[1].lowercase() }.toSet()

fun Note.isExpired(now: Long = System.currentTimeMillis()): Boolean =
    expiresAt != null && expiresAt <= now
