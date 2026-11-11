package dev.rushi.stickynotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Neutral "paper and ink" palette. No dynamic colour, so the app looks the same everywhere.
private val Ink = Color(0xFF1C1B19)
private val Paper = Color(0xFFFAFAF8)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Color(0xFF4A4945),
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EFEC),
    onSurfaceVariant = Color(0xFF6B6A66), // 5.1:1 on Paper
    surfaceContainer = Color(0xFFF4F3F0),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFBDBAB3),
    outlineVariant = Color(0xFFE4E2DD),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE8E6E1),
    onPrimary = Color(0xFF121211),
    secondary = Color(0xFFC9C6BF),
    onSecondary = Color(0xFF121211),
    background = Color(0xFF121211),
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF121211),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF1F1F1D),
    onSurfaceVariant = Color(0xFFA3A19B), // 7.3:1 on background
    surfaceContainer = Color(0xFF1A1A18),
    surfaceContainerHigh = Color(0xFF242422),
    outline = Color(0xFF5C5A55),
    outlineVariant = Color(0xFF2E2D2A),
    inverseSurface = Color(0xFFE8E6E1),
    inverseOnSurface = Color(0xFF121211),
    error = Color(0xFFF2B8B5),
)

private val base = Typography()

private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun StickyNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
