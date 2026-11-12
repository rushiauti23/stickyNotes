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

    /**
     * Ticks or unticks the item at [index] (counting items the way [parse] does) directly in the
     * stored text, keeping every other line exactly as it was. [expectedText] guards against the
     * note having changed since the widget drew it: if the item at [index] no longer matches,
     * the first item with that text is toggled instead, and if none matches nothing changes.
     */
    fun toggleItem(content: String, index: Int, expectedText: String): String {
        val lines = content.lines().toMutableList()
        val itemLines = lines.indices.filter { lines[it].isNotBlank() }
        val target = itemLines.getOrNull(index)?.takeIf { itemText(lines[it]) == expectedText }
            ?: itemLines.firstOrNull { itemText(lines[it]) == expectedText }
            ?: return content
        val line = lines[target]
        lines[target] = when {
            line.startsWith("[x] ", ignoreCase = true) -> "[ ] " + line.drop(4)
            line.startsWith("[ ] ") -> "[x] " + line.drop(4)
            else -> "[x] " + line.trim()
        }
        return lines.joinToString("\n")
    }

    /** Unticks every item, e.g. to start a daily to-do list fresh. */
    fun uncheckAll(content: String): String =
        content.lines().joinToString("\n") { line ->
            if (line.startsWith("[x] ", ignoreCase = true)) "[ ] " + line.drop(4) else line
        }

    private fun itemText(line: String): String = when {
        line.startsWith("[x] ", ignoreCase = true) || line.startsWith("[ ] ") -> line.drop(4)
        else -> line.trim()
    }
}
