package dev.rushi.stickynotes.data

import dev.rushi.stickynotes.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for notes. Every write also keeps the note's reminder alarm in sync.
 * Writes that must outlive a screen run in [appScope], not a ViewModel scope.
 */
class NoteRepository(
    private val dao: NoteDao,
    private val scheduler: ReminderScheduler,
    private val appScope: CoroutineScope,
) {
    val notes: Flow<List<Note>> = dao.observeAll()

    private val _pendingUndo = MutableStateFlow<Note?>(null)
    /** The most recently deleted note, while its "Undo" snackbar can still be offered. */
    val pendingUndo: StateFlow<Note?> = _pendingUndo.asStateFlow()

    suspend fun get(id: Long): Note? = dao.getById(id)

    suspend fun insert(note: Note): Long {
        val id = dao.insert(note)
        scheduler.sync(note.copy(id = id))
        return id
    }

    suspend fun update(note: Note) {
        dao.update(note)
        scheduler.sync(note)
    }

    suspend fun deleteSilently(note: Note) {
        scheduler.cancel(note.id)
        dao.delete(note)
    }

    /** Called when a reminder has fired; doesn't change the note's "edited" time. */
    suspend fun clearReminder(note: Note) {
        dao.update(note.copy(reminderAt = null))
    }

    /** Re-arms every pending reminder (after a reboot or app update clears alarms). */
    suspend fun rescheduleAllReminders() {
        dao.withReminders().forEach { scheduler.sync(it) }
    }

    suspend fun purgeExpired() {
        dao.deleteExpired(System.currentTimeMillis())
    }

    fun launch(block: suspend () -> Unit): Job = appScope.launch { block() }

    fun delete(note: Note) {
        appScope.launch {
            deleteSilently(note)
            _pendingUndo.value = note
        }
    }

    fun undoDelete() {
        val note = _pendingUndo.value ?: return
        _pendingUndo.value = null
        appScope.launch { insert(note) }
    }

    fun clearUndo() {
        _pendingUndo.value = null
    }

    fun togglePin(note: Note) {
        appScope.launch { update(note.copy(pinned = !note.pinned)) }
    }
}
