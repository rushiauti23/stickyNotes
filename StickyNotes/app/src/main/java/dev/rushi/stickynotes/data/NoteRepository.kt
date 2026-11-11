package dev.rushi.stickynotes.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for notes. Writes that must outlive a screen (deletes, pin toggles,
 * saves when the app goes to the background) run in [appScope], not a ViewModel scope.
 */
class NoteRepository(
    private val dao: NoteDao,
    private val appScope: CoroutineScope,
) {
    val notes: Flow<List<Note>> = dao.observeAll()

    private val _pendingUndo = MutableStateFlow<Note?>(null)
    /** The most recently deleted note, while its "Undo" snackbar can still be offered. */
    val pendingUndo: StateFlow<Note?> = _pendingUndo.asStateFlow()

    suspend fun get(id: Long): Note? = dao.getById(id)
    suspend fun insert(note: Note): Long = dao.insert(note)
    suspend fun update(note: Note) = dao.update(note)
    suspend fun deleteSilently(note: Note) = dao.delete(note)

    fun launch(block: suspend () -> Unit): Job = appScope.launch { block() }

    fun delete(note: Note) {
        appScope.launch {
            dao.delete(note)
            _pendingUndo.value = note
        }
    }

    fun undoDelete() {
        val note = _pendingUndo.value ?: return
        _pendingUndo.value = null
        appScope.launch { dao.insert(note) }
    }

    fun clearUndo() {
        _pendingUndo.value = null
    }

    fun togglePin(note: Note) {
        appScope.launch { dao.update(note.copy(pinned = !note.pinned)) }
    }
}
