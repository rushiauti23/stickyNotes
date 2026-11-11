package dev.rushi.stickynotes.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.rushi.stickynotes.data.Note

/**
 * Arms one alarm per note. Uses inexact, doze-friendly alarms, so no "exact alarm"
 * permission is needed; a reminder can arrive a few minutes late when the phone is idle.
 */
class ReminderScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    fun sync(note: Note) {
        val at = note.reminderAt
        if (at == null || note.id == 0L) cancel(note.id) else schedule(note.id, at)
    }

    fun schedule(noteId: Long, at: Long) {
        alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(noteId))
    }

    fun cancel(noteId: Long) {
        alarms?.cancel(pendingIntent(noteId))
    }

    // Explicit + immutable: only our own receiver can be triggered, and nobody can alter the intent.
    private fun pendingIntent(noteId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            noteId.toInt(),
            Intent(appContext, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
