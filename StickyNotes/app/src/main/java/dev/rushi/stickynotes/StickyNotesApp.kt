package dev.rushi.stickynotes

import android.app.Application
import dev.rushi.stickynotes.data.NoteDatabase
import dev.rushi.stickynotes.data.NoteRepository
import dev.rushi.stickynotes.security.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StickyNotesApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: NoteRepository by lazy {
        NoteRepository(NoteDatabase.get(this).noteDao(), appScope)
    }

    val appLock: AppLock by lazy { AppLock(this) }
}
