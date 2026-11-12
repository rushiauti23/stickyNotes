package dev.rushi.stickynotes.settings

import android.content.Context
import dev.rushi.stickynotes.data.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small user preferences, stored on the device (SharedPreferences). */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _defaultColorIndex = MutableStateFlow(
        prefs.getInt(KEY_DEFAULT_COLOR, 0).coerceIn(0, Note.COLOR_COUNT - 1),
    )
    /** Paper colour for new notes. */
    val defaultColorIndex: StateFlow<Int> = _defaultColorIndex.asStateFlow()

    private val _relockAfterMs = MutableStateFlow(
        prefs.getLong(KEY_RELOCK_AFTER, DEFAULT_RELOCK_MS).takeIf { it in RELOCK_CHOICES } ?: DEFAULT_RELOCK_MS,
    )
    /** How long the app may be in the background before the app lock asks again. */
    val relockAfterMs: StateFlow<Long> = _relockAfterMs.asStateFlow()

    private val _widgetShowsNotes = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_SHOWS_NOTES, false))
    /** With the app lock on, whether the home-screen widget may still show note titles and text. */
    val widgetShowsNotes: StateFlow<Boolean> = _widgetShowsNotes.asStateFlow()

    fun setWidgetShowsNotes(show: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_SHOWS_NOTES, show).apply()
        _widgetShowsNotes.value = show
    }

    fun setDefaultColorIndex(index: Int) {
        val value = index.coerceIn(0, Note.COLOR_COUNT - 1)
        prefs.edit().putInt(KEY_DEFAULT_COLOR, value).apply()
        _defaultColorIndex.value = value
    }

    fun setRelockAfterMs(ms: Long) {
        if (ms !in RELOCK_CHOICES) return
        prefs.edit().putLong(KEY_RELOCK_AFTER, ms).apply()
        _relockAfterMs.value = ms
    }

    companion object {
        // Same file as the app-lock switch, so a backup restores all settings together.
        private const val PREFS = "settings"
        private const val KEY_DEFAULT_COLOR = "default_color_index"
        private const val KEY_RELOCK_AFTER = "relock_after_ms"
        private const val KEY_WIDGET_SHOWS_NOTES = "widget_shows_notes"

        const val DEFAULT_RELOCK_MS = 30_000L

        /** Allowed "lock again" delays, in order, with labels for the settings screen. */
        val RELOCK_CHOICES: List<Long> = listOf(0L, 30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L)

        fun relockLabel(ms: Long): String = when (ms) {
            0L -> "Immediately"
            30_000L -> "After 30 seconds"
            60_000L -> "After 1 minute"
            5 * 60_000L -> "After 5 minutes"
            15 * 60_000L -> "After 15 minutes"
            60 * 60_000L -> "After 1 hour"
            else -> "After ${ms / 1000} seconds"
        }
    }
}
