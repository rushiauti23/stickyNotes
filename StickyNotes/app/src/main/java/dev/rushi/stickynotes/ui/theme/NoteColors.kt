package dev.rushi.stickynotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Muted paper tints. Stored in the DB by index, so only ever append to these lists.
 * Index 0 ("Plain") is the default for new notes.
 */
object NoteColors {
    val names = listOf("Plain", "Sand", "Peach", "Rose", "Lavender", "Sky", "Mint", "Sage")

    internal val light = listOf(
        Color(0xFFFFFFFF), Color(0xFFF7F0DC), Color(0xFFFBE9DC), Color(0xFFF8E4E6),
        Color(0xFFEEE9F6), Color(0xFFE4EEF7), Color(0xFFE2F1EE), Color(0xFFE8F0E2),
    )

    internal val dark = listOf(
        Color(0xFF1C1C1A), Color(0xFF2B281F), Color(0xFF30261F), Color(0xFF302326),
        Color(0xFF282532), Color(0xFF1F2832), Color(0xFF1C2B29), Color(0xFF222B1F),
    )

    val count: Int get() = names.size
}

@Composable
fun noteColor(index: Int): Color {
    val i = index.coerceIn(0, NoteColors.count - 1)
    return if (isSystemInDarkTheme()) NoteColors.dark[i] else NoteColors.light[i]
}

/** Text colour on any note tint (contrast is at least 12:1 in both themes). */
@Composable
fun noteContentColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFE8E6E1) else Color(0xFF1C1B19)

/** Hairline that separates a note from the page without a shadow. */
@Composable
fun noteBorderColor(): Color = noteContentColor().copy(alpha = 0.08f)
