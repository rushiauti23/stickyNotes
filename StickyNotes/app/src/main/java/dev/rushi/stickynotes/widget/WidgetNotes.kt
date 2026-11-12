package dev.rushi.stickynotes.widget

import android.content.Context

/** Which note each "Sticky note" widget shows, keyed by the launcher's widget id. */
object WidgetNotes {
    private const val PREFS = "note_widgets"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun noteFor(context: Context, appWidgetId: Int): Long? =
        prefs(context).getLong(key(appWidgetId), -1L).takeIf { it > 0 }

    fun set(context: Context, appWidgetId: Int, noteId: Long) {
        prefs(context).edit().putLong(key(appWidgetId), noteId).apply()
    }

    fun remove(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit().apply { appWidgetIds.forEach { remove(key(it)) } }.apply()
    }

    private fun key(appWidgetId: Int) = "w_$appWidgetId"
}
