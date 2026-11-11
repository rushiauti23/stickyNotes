package dev.rushi.stickynotes.data

import java.util.concurrent.atomic.AtomicLong

data class ChecklistItem(val id: Long, val text: String, val checked: Boolean)

/** Stores checklist items in [Note.content], one per line: "[ ] text" or "[x] text". */
object Checklist {
    private val ids = AtomicLong(1)

    fun newItem(text: String = "", checked: Boolean = false) =
        ChecklistItem(ids.getAndIncrement(), text, checked)

    fun parse(content: String): List<ChecklistItem> =
        content.lines().filter { it.isNotBlank() }.map { line ->
            when {
                line.startsWith("[x] ", ignoreCase = true) -> newItem(line.drop(4), checked = true)
                line.startsWith("[ ] ") -> newItem(line.drop(4))
                else -> newItem(line.trim())
            }
        }

    /** Empty rows are dropped on save. */
    fun serialize(items: List<ChecklistItem>): String =
        items.filter { it.text.isNotBlank() }.joinToString("\n") { item ->
            (if (item.checked) "[x] " else "[ ] ") + item.text.replace('\n', ' ').trim()
        }

    fun fromPlainText(text: String): List<ChecklistItem> =
        text.lines().filter { it.isNotBlank() }.map { newItem(it.trim()) }

    fun toPlainText(items: List<ChecklistItem>): String =
        items.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
}
