package dev.rushi.stickynotes

import android.app.Application
import dev.rushi.stickynotes.data.NoteDatabase
import dev.rushi.stickynotes.data.NoteRepository
import dev.rushi.stickynotes.reminders.Notifications
import dev.rushi.stickynotes.reminders.ReminderScheduler
import dev.rushi.stickynotes.security.AppLock
import dev.rushi.stickynotes.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.glance.appwidget.updateAll
import dev.rushi.stickynotes.widget.NotesWidget
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class StickyNotesApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: NoteRepository by lazy {
        NoteRepository(NoteDatabase.get(this).noteDao(), reminderScheduler, appScope)
    }

    val settings: AppSettings by lazy { AppSettings(this) }

    val appLock: AppLock by lazy { AppLock(this, settings) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(this) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch {
            repository.purgeExpired()
            // Re-arm reminders on every start too: some phones never deliver the boot broadcast
            // (battery savers / "autostart" blocks), which would otherwise lose reminders after a
            // restart. Reminders whose time has passed fire right away.
            repository.rescheduleAllReminders()
        }
        // Keep the home-screen widget in step with notes and the app lock.
        appScope.launch {
            combine(repository.notes, appLock.enabled) { _, _ -> }
                .debounce(WIDGET_REFRESH_DEBOUNCE_MS)
                .collect { runCatching { NotesWidget().updateAll(this@StickyNotesApp) } }
        }
    }

    private companion object {
        const val WIDGET_REFRESH_DEBOUNCE_MS = 500L
    }
}
