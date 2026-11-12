package dev.rushi.stickynotes.data.text

import dev.rushi.stickynotes.data.text.Markup.InlineKind
import dev.rushi.stickynotes.data.text.Markup.LineKind

/** Text plus a selection (or caret when [start] == [end]). Plain Kotlin so it's unit-testable. */
data class Edit(val text: String, val start: Int, val end: Int = start) {
    val min: Int get() = minOf(start, end).coerceIn(0, text.length)
    val max: Int get() = maxOf(start, end).coerceIn(0, text.length)
}

/** The editing actions behind the formatting toolbar. Each returns the new text + selection. */
object TextFormat {

    /**
     * Bold / italic / … on the selection. No selection: applies to the word under the caret,
     * or inserts an empty pair with the caret between. Applying it again removes it.
     * Multi-line selections are formatted line by line, since spans can't cross lines.
     */
    fun toggleInline(e: Edit, kind: InlineKind): Edit {
        val m = kind.marker
        var s = e.min
        var t = e.max
        val text = e.text

        if (s == t) {
            val (ws, we) = wordAt(text, s)
            if (ws == we) {
                val inserted = text.substring(0, s) + m + m + text.substring(s)
                return Edit(inserted, s + m.length)
            }
            s = ws
            t = we
        }

        // Already wrapped just outside the selection → unwrap.
        if (s >= m.length && t + m.length <= text.length &&
            text.regionMatches(s - m.length, m, 0, m.length) && text.regionMatches(t, m, 0, m.length)
        ) {
            val out = text.substring(0, s - m.length) + text.substring(s, t) + text.substring(t + m.length)
            return Edit(out, s - m.length, t - m.length)
        }
        // Selection includes the markers → unwrap.
        val selected = text.substring(s, t)
        if (selected.length >= 2 * m.length && selected.startsWith(m) && selected.endsWith(m) && !selected.contains('\n')) {
            val inner = selected.substring(m.length, selected.length - m.length)
            return Edit(text.substring(0, s) + inner + text.substring(t), s, s + inner.length)
        }

        // Wrap each line's part of the selection, last line first so earlier offsets stay valid.
        val segments = lineSegments(text, s, t)
        var out = text
        var wrapped = 0
        var firstA = -1
        var firstB = -1
        for ((segStart, segEnd) in segments.asReversed()) {
            var a = segStart
            var b = segEnd
            while (a < b && text[a].isWhitespace()) a++
            while (b > a && text[b - 1].isWhitespace()) b--
            if (a == b) continue
            out = out.substring(0, b) + m + out.substring(b)
            out = out.substring(0, a) + m + out.substring(a)
            wrapped++
            firstA = a
            firstB = b
        }
        if (wrapped == 0) return e
        return if (wrapped == 1) {
            Edit(out, firstA + m.length, firstB + m.length)
        } else {
            Edit(out, s, t + wrapped * 2 * m.length)
        }
    }

    /** Heading / bullet / numbered / quote on every line the selection touches. Toggles off if all lines already have it. */
    fun toggleLine(e: Edit, kind: LineKind): Edit {
        val text = e.text
        val (blockStart, blockEnd) = lineBlock(text, e.min, e.max)
        val lines = text.substring(blockStart, blockEnd).split('\n')
        val nonBlank = lines.filter { it.isNotBlank() }
        val allHave = nonBlank.isNotEmpty() && nonBlank.all { Markup.linePrefix(it).kind == kind }

        var number = 0
        val newLines = lines.map { line ->
            val p = Markup.linePrefix(line)
            val indent = line.substring(0, p.indent)
            val body = line.substring(p.length)
            when {
                allHave -> indent + body
                line.isBlank() && lines.size > 1 -> line
                else -> {
                    number++
                    indent + prefix(kind, number) + body
                }
            }
        }
        val block = newLines.joinToString("\n")
        val out = text.substring(0, blockStart) + block + text.substring(blockEnd)

        return if (lines.size == 1) {
            val delta = block.length - (blockEnd - blockStart)
            val newPrefix = Markup.linePrefix(block).length
            Edit(out, (e.min + delta).coerceIn(blockStart + newPrefix, blockStart + block.length), (e.max + delta).coerceIn(blockStart + newPrefix, blockStart + block.length))
        } else {
            Edit(out, blockStart, blockStart + block.length)
        }
    }

    /** Removes all formatting from the lines the selection touches. */
    fun clearFormatting(e: Edit): Edit {
        val (blockStart, blockEnd) = lineBlock(e.text, e.min, e.max)
        val cleaned = Markup.removeAll(e.text.substring(blockStart, blockEnd))
        val out = e.text.substring(0, blockStart) + cleaned + e.text.substring(blockEnd)
        return Edit(out, blockStart, blockStart + cleaned.length)
    }

    /**
     * Called after every keystroke. When the user just pressed Enter on a list or quote line,
     * continues the list on the new line ("- ", "2. ", "> "). Enter on an empty item ends the
     * list instead. Returns null when nothing needs to change.
     */
    fun continueList(previousText: String, e: Edit): Edit? {
        val caret = e.max
        if (e.min != e.max || caret == 0 || e.text.length != previousText.length + 1) return null
        if (e.text[caret - 1] != '\n') return null
        if (e.text.removeRange(caret - 1, caret) != previousText) return null

        val lineStart = e.text.lastIndexOf('\n', caret - 2) + 1
        val line = e.text.substring(lineStart, caret - 1)
        val p = Markup.linePrefix(line)
        if (p.kind !in setOf(LineKind.Bullet, LineKind.Numbered, LineKind.Quote)) return null

        if (line.substring(p.length).isBlank()) {
            // Enter on an empty item: drop the marker and stay on that (now empty) line.
            val out = e.text.substring(0, lineStart) + e.text.substring(caret)
            return Edit(out, lineStart)
        }
        val indent = " ".repeat(p.indent)
        val next = when (p.kind) {
            LineKind.Bullet -> "$indent${p.bullet ?: '-'} "
            LineKind.Numbered -> "$indent${(p.number ?: 0) + 1}. "
            else -> "> "
        }
        val out = e.text.substring(0, caret) + next + e.text.substring(caret)
        return Edit(out, caret + next.length)
    }

    // --- helpers ------------------------------------------------------------------------------

    private fun prefix(kind: LineKind, number: Int): String = when (kind) {
        LineKind.Heading1 -> "# "
        LineKind.Heading2 -> "## "
        LineKind.Bullet -> "- "
        LineKind.Numbered -> "$number. "
        LineKind.Quote -> "> "
        LineKind.Plain -> ""
    }

    /** Start/end of the full lines touched by [min]..[max]. A selection ending right after a newline doesn't touch the next line. */
    private fun lineBlock(text: String, min: Int, max: Int): Pair<Int, Int> {
        val start = text.lastIndexOf('\n', (min - 1).coerceAtLeast(-1)).let { if (min == 0) 0 else it + 1 }
        val effectiveMax = if (max > min && text[max - 1] == '\n') max - 1 else max
        val end = text.indexOf('\n', effectiveMax).let { if (it < 0) text.length else it }
        return start to maxOf(start, end)
    }

    /** Splits [s]..[t] at line breaks. */
    private fun lineSegments(text: String, s: Int, t: Int): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var a = s
        while (a <= t) {
            val nl = text.indexOf('\n', a).let { if (it < 0 || it > t) t else it }
            result += a to nl
            if (nl >= t) break
            a = nl + 1
        }
        return result
    }

    private fun wordAt(text: String, pos: Int): Pair<Int, Int> {
        fun isWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '-'
        var a = pos
        var b = pos
        while (a > 0 && isWord(text[a - 1])) a--
        while (b < text.length && isWord(text[b])) b++
        return a to b
    }
}
