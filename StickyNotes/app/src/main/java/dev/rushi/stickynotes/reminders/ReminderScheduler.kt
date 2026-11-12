package dev.rushi.stickynotes.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.rushi.stickynotes.data.Note

/**
 * Arms one alarm per note. Uses an exact alarm when Android allows it (always on Android 9–11;
 * on 12+ only if the user grants "Alarms & reminders"), otherwise an inexact doze-friendly
 * alarm that can arrive a few minutes late.
 */
class ReminderScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    fun sync(note: Note) {
        val at = note.reminderAt
        if (at == null || note.id == 0L) cancel(note.id) else schedule(note.id, at)
    }

    fun schedule(noteId: Long, at: Long) {
        val manager = alarms ?: return
        val intent = pendingIntent(noteId)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                return
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call: fall back to inexact.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
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
