package dev.rushi.stickynotes.data.text

/**
 * Light Markdown-style formatting, stored as plain text inside the note so it survives
 * backups, sharing and old app versions:
 *
 *   **bold**  _italic_  ++underline++  ~~strike~~  ==highlight==  `code`
 *   # Heading   ## Subheading   - bullet   1. numbered   > quote
 *
 * The editor shows the markers faintly; cards, widget and notifications hide them.
 */
object Markup {

    enum class InlineKind(val marker: String) {
        Bold("**"), Italic("_"), Underline("++"), Strike("~~"), Highlight("=="), Code("`"),
    }

    enum class LineKind { Plain, Heading1, Heading2, Bullet, Numbered, Quote }

    /**
     * @param indent leading spaces kept in front of a list marker
     * @param length characters from the line start to where the text begins (indent + marker)
     */
    data class LinePrefix(val kind: LineKind, val indent: Int, val length: Int, val number: Int? = null, val bullet: Char? = null)

    data class Span(val kind: InlineKind, val start: Int, val end: Int) {
        val innerStart: Int get() = start + kind.marker.length
        val innerEnd: Int get() = end - kind.marker.length
    }

    private val headingRegex = Regex("""^(#{1,2}) """)
    private val bulletRegex = Regex("""^( *)([-*•]) """)
    private val numberedRegex = Regex("""^( *)(\d{1,4})[.)] """)
    private val quoteRegex = Regex("""^> """)

    // Text must hug the markers ("** x**" isn't bold), and spans never cross a line break.
    private val inlineRegexes: Map<InlineKind, Regex> = mapOf(
        InlineKind.Bold to Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*"""),
        // _x_ only at word edges, so snake_case_words and #tags_like_this stay untouched.
        InlineKind.Italic to Regex("""(?<![\p{L}\p{N}_])_(?=\S)(.+?)(?<=\S)_(?![\p{L}\p{N}_])"""),
        InlineKind.Underline to Regex("""\+\+(?=\S)(.+?)(?<=\S)\+\+"""),
        InlineKind.Strike to Regex("""~~(?=\S)(.+?)(?<=\S)~~"""),
        InlineKind.Highlight to Regex("""==(?=\S)(.+?)(?<=\S)=="""),
        InlineKind.Code to Regex("""`([^`\n]+)`"""),
    )

    fun linePrefix(line: String): LinePrefix {
        headingRegex.find(line)?.let { m ->
            val kind = if (m.groupValues[1].length == 1) LineKind.Heading1 else LineKind.Heading2
            return LinePrefix(kind, 0, m.value.length)
        }
        bulletRegex.find(line)?.let { m ->
            return LinePrefix(LineKind.Bullet, m.groupValues[1].length, m.value.length, bullet = m.groupValues[2][0])
        }
        numberedRegex.find(line)?.let { m ->
            return LinePrefix(LineKind.Numbered, m.groupValues[1].length, m.value.length, number = m.groupValues[2].toInt())
        }
        quoteRegex.find(line)?.let { m -> return LinePrefix(LineKind.Quote, 0, m.value.length) }
        return LinePrefix(LineKind.Plain, 0, 0)
    }

    /** Every inline span in [text], in no particular order (spans may nest). */
    fun inlineSpans(text: String): List<Span> =
        inlineRegexes.flatMap { (kind, regex) ->
            regex.findAll(text).map { Span(kind, it.range.first, it.range.last + 1) }.toList()
        }

    fun inlineRegex(kind: InlineKind): Regex = inlineRegexes.getValue(kind)

    /** Readable text without markers: "• " for bullets, numbers kept, everything else removed. */
    fun strip(text: String): String =
        text.lineSequence().joinToString("\n") { line ->
            val p = linePrefix(line)
            val body = stripInline(line.substring(p.length))
            val indent = " ".repeat(p.indent)
            when (p.kind) {
                LineKind.Bullet -> "$indent• $body"
                LineKind.Numbered -> "$indent${p.number}. $body"
                else -> body
            }
        }

    /** Removes every marker, including list markers. Used by "Clear formatting". */
    fun removeAll(text: String): String =
        text.lineSequence().joinToString("\n") { line ->
            val p = linePrefix(line)
            " ".repeat(p.indent) + stripInline(line.substring(p.length))
        }

    fun stripInline(text: String): String {
        var out = text
        // Repeat so nested spans (**_both_**) lose all their markers.
        repeat(3) {
            inlineRegexes.values.forEach { regex -> out = regex.replace(out) { it.groupValues[1] } }
        }
        return out
    }
}
