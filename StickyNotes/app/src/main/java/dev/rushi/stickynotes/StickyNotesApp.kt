package dev.rushi.stickynotes

import android.app.Application
import dev.rushi.stickynotes.data.NoteDatabase
import dev.rushi.stickynotes.data.NoteRepository
import dev.rushi.stickynotes.reminders.Notifications
import dev.rushi.stickynotes.reminders.ReminderScheduler
import dev.rushi.stickynotes.security.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StickyNotesApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: NoteRepository by lazy {
        NoteRepository(NoteDatabase.get(this).noteDao(), ReminderScheduler(this), appScope)
    }

    val appLock: AppLock by lazy { AppLock(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch { repository.purgeExpired() }
    }
}
