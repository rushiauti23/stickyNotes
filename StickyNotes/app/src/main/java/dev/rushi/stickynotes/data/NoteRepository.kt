package dev.rushi.stickynotes.data

import dev.rushi.stickynotes.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Something the user just did that the snackbar can undo. */
data class UndoEvent(val note: Note, val kind: Kind) {
    enum class Kind { Trashed, Archived, Unarchived }
}

data class ImportResult(val added: Int, val skipped: Int)

/**
 * Single source of truth for notes. Every write also keeps the note's reminder alarm in sync.
 * Writes that must outlive a screen run in [appScope], not a ViewModel scope.
 */
class NoteRepository(
    private val dao: NoteDao,
    private val scheduler: ReminderScheduler,
    private val appScope: CoroutineScope,
) {
    /** Every note, including archived and trashed; screens filter what they show. */
    val notes: Flow<List<Note>> = dao.observeAll()

    private val _pendingUndo = MutableStateFlow<UndoEvent?>(null)
    val pendingUndo: StateFlow<UndoEvent?> = _pendingUndo.asStateFlow()

    suspend fun get(id: Long): Note? = dao.getById(id)

    suspend fun insert(note: Note): Long {
        val id = dao.insert(note)
        scheduler.sync(note.copy(id = id))
        return id
    }

    suspend fun update(note: Note) {
        dao.update(note)
        if (note.isTrashed) scheduler.cancel(note.id) else scheduler.sync(note)
    }

    /** Permanent delete, no Trash. Used for emptied notes and "Delete forever". */
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
        dao.withReminders().filterNot { it.isTrashed }.forEach { scheduler.sync(it) }
    }

    /** Removes vanished notes and Trash older than 30 days. */
    suspend fun purgeExpired() {
        val now = System.currentTimeMillis()
        dao.deleteExpired(now)
        dao.deleteTrashedBefore(now - Note.TRASH_RETENTION_MS)
    }

    /** Notes for the home-screen widget: pinned first, else the most recent. */
    suspend fun widgetNotes(limit: Int = 8): List<Note> {
        val board = dao.getAll().filter { it.isOnBoard() }
        val pinned = board.filter { it.pinned }
        return (pinned.ifEmpty { board }).take(limit)
    }

    // --- Backup ------------------------------------------------------------------------------

    /** Everything except Trash and vanished notes. */
    suspend fun exportJson(): Pair<String, Int> {
        val now = System.currentTimeMillis()
        val notes = dao.getAll().filter { !it.isTrashed && !it.isExpired(now) }
        return BackupCodec.encode(notes, now) to notes.size
    }

    /** Adds notes from a backup, skipping ones that already exist. */
    suspend fun importJson(json: String): ImportResult {
        val incoming = BackupCodec.decode(json)
        val existing = dao.getAll().map { Triple(it.createdAt, it.title, it.content) }.toHashSet()
        val fresh = incoming.filter { Triple(it.createdAt, it.title, it.content) !in existing }
            .distinctBy { Triple(it.createdAt, it.title, it.content) }
        if (fresh.isNotEmpty()) {
            val ids = dao.insertAll(fresh)
            fresh.zip(ids).forEach { (note, id) -> scheduler.sync(note.copy(id = id)) }
        }
        return ImportResult(added = fresh.size, skipped = incoming.size - fresh.size)
    }

    // --- User actions with Undo --------------------------------------------------------------

    fun launch(block: suspend () -> Unit): Job = appScope.launch { block() }

    /** Moves the note to Trash (recoverable for 30 days). */
    fun delete(note: Note) {
        appScope.launch {
            update(note.copy(deletedAt = System.currentTimeMillis()))
            _pendingUndo.value = UndoEvent(note, UndoEvent.Kind.Trashed)
        }
    }

    fun setArchived(note: Note, archived: Boolean) {
        appScope.launch {
            update(note.copy(archived = archived))
            _pendingUndo.value = UndoEvent(
                note,
                if (archived) UndoEvent.Kind.Archived else UndoEvent.Kind.Unarchived,
            )
        }
    }

    fun undo() {
        val event = _pendingUndo.value ?: return
        _pendingUndo.value = null
        appScope.launch {
            val current = dao.getById(event.note.id) ?: return@launch
            when (event.kind) {
                UndoEvent.Kind.Trashed -> update(current.copy(deletedAt = null))
                UndoEvent.Kind.Archived -> update(current.copy(archived = false))
                UndoEvent.Kind.Unarchived -> update(current.copy(archived = true))
            }
        }
    }

    fun clearUndo() {
        _pendingUndo.value = null
    }

    fun togglePin(note: Note) {
        appScope.launch { update(note.copy(pinned = !note.pinned)) }
    }

    fun restoreFromTrash(note: Note) {
        appScope.launch { update(note.copy(deletedAt = null)) }
    }

    fun deleteForever(note: Note) {
        appScope.launch { deleteSilently(note) }
    }

    fun emptyTrash() {
        appScope.launch {
            dao.trashedIds().forEach { scheduler.cancel(it) }
            dao.deleteAllTrashed()
        }
    }
}
