package dev.rushi.stickynotes.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.isTrashed

/**
 * Not exported: receives only our own explicit alarm intents and system boot/update broadcasts.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as StickyNotesApp
        when (intent.action) {
            ACTION_REMIND -> {
                val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
                if (id <= 0L) return
                val pending = goAsync()
                app.repository.launch {
                    try {
                        val note = app.repository.get(id) ?: return@launch
                        if (note.isTrashed) return@launch
                        val due = note.reminderAt ?: return@launch // reminder was removed
                        if (due > System.currentTimeMillis() + EARLY_TOLERANCE_MS) {
                            // Fired early (time changed): re-arm instead of notifying.
                            app.repository.update(note)
                            return@launch
                        }
                        Notifications.showReminder(context, note, hideContent = app.appLock.enabled.value)
                        app.repository.clearReminder(note)
                    } finally {
                        pending.finish()
                    }
                }
            }
            in RESCHEDULE_ACTIONS -> {
                val pending = goAsync()
                app.repository.launch {
                    try {
                        app.repository.rescheduleAllReminders()
                        app.repository.purgeExpired()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "dev.rushi.stickynotes.action.REMIND"
        const val EXTRA_NOTE_ID = "note_id"
        private const val EARLY_TOLERANCE_MS = 60_000L

        /** System events after which all alarms must be re-armed. */
        private val RESCHEDULE_ACTIONS: Set<String?> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Some phones (Xiaomi, HTC, …) send these instead of BOOT_COMPLETED on a fast reboot.
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            // User granted "Alarms & reminders": re-arm as exact alarms.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
