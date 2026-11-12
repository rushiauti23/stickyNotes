package dev.rushi.stickynotes.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import dev.rushi.stickynotes.data.text.Markup
import dev.rushi.stickynotes.data.text.Markup.InlineKind
import dev.rushi.stickynotes.data.text.Markup.LineKind

private fun inlineStyle(kind: InlineKind, ink: Color): SpanStyle = when (kind) {
    InlineKind.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    InlineKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    InlineKind.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    InlineKind.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = ink.copy(alpha = 0.6f))
    InlineKind.Highlight -> SpanStyle(background = Color(0x66FFD54F))
    InlineKind.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = ink.copy(alpha = 0.08f))
}

private fun lineStyle(kind: LineKind, ink: Color): SpanStyle? = when (kind) {
    LineKind.Heading1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.35.em)
    LineKind.Heading2 -> SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 1.15.em)
    LineKind.Quote -> SpanStyle(fontStyle = FontStyle.Italic, color = ink.copy(alpha = 0.75f))
    else -> null
}

/**
 * Editor view: the text is unchanged (so the cursor maps 1:1), formatting is applied and the
 * markers themselves are drawn faintly. Recomputed per keystroke; regexes are linear-time.
 */
fun markupVisualTransformation(ink: Color): VisualTransformation = VisualTransformation { text ->
    TransformedText(styledForEditor(text.text, ink), OffsetMapping.Identity)
}

private fun styledForEditor(text: String, ink: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val faint = SpanStyle(color = ink.copy(alpha = 0.35f))
    var offset = 0
    for (line in text.split('\n')) {
        val p = Markup.linePrefix(line)
        lineStyle(p.kind, ink)?.let { addStyle(it, offset, offset + line.length) }
        if (p.length > 0 && p.kind != LineKind.Bullet && p.kind != LineKind.Numbered) {
            addStyle(faint, offset, offset + p.length)
        }
        offset += line.length + 1
    }
    for (span in Markup.inlineSpans(text)) {
        addStyle(inlineStyle(span.kind, ink), span.innerStart, span.innerEnd)
        addStyle(faint, span.start, span.innerStart)
        addStyle(faint, span.innerEnd, span.end)
    }
}

/** Card preview: markers removed, bullets as "•", formatting applied (outermost span per run). */
fun styledPreview(text: String, ink: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.split('\n')
    lines.forEachIndexed { index, line ->
        val p = Markup.linePrefix(line)
        val lineStart = length
        append(" ".repeat(p.indent))
        when (p.kind) {
            LineKind.Bullet -> append("•  ")
            LineKind.Numbered -> append("${p.number}. ")
            LineKind.Quote -> append("│ ")
            else -> Unit
        }
        appendInline(line.substring(p.length), ink)
        lineStyle(p.kind, ink)?.let { addStyle(it, lineStart, length) }
        if (index < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendInline(body: String, ink: Color) {
    // Non-overlapping, left-to-right; a nested span's inner markers are stripped as plain text.
    val spans = Markup.inlineSpans(body).sortedBy { it.start }
    var cursor = 0
    for (span in spans) {
        if (span.start < cursor) continue
        append(body.substring(cursor, span.start))
        val inner = Markup.stripInline(body.substring(span.innerStart, span.innerEnd))
        val from = length
        append(inner)
        addStyle(inlineStyle(span.kind, ink), from, length)
        cursor = span.end
    }
    append(body.substring(cursor))
}
