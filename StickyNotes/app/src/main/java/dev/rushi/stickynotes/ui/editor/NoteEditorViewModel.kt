package dev.rushi.stickynotes.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.Checklist
import dev.rushi.stickynotes.data.ChecklistItem
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.NoteRepository
import dev.rushi.stickynotes.data.text.Edit
import dev.rushi.stickynotes.data.text.Markup
import dev.rushi.stickynotes.data.text.TextFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class NoteEditorViewModel(
    private val repo: NoteRepository,
    initialId: Long,
    defaultColorIndex: Int = 0,
) : ViewModel() {

    val isNew = initialId == NEW_NOTE_ID

    var title by mutableStateOf("")
        private set
    /** Body for plain notes; TextFieldValue so a timestamp can go in at the cursor. */
    var body by mutableStateOf(TextFieldValue(""))
        private set
    /** Items for checklist notes. */
    val items = mutableStateListOf<ChecklistItem>()
    var isChecklist by mutableStateOf(false)
        private set
    var colorIndex by mutableIntStateOf(defaultColorIndex) // from Settings → New note colour
        private set
    var pinned by mutableStateOf(false)
        private set
    var reminderAt by mutableStateOf<Long?>(null)
        private set
    var expiresAt by mutableStateOf<Long?>(null)
        private set
    var editedAt by mutableStateOf<Long?>(null)
        private set
    var archived by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(isNew)
        private set

    private var noteId: Long = if (isNew) 0 else initialId
    private var createdAt = System.currentTimeMillis()
    private var lastSaved: Note? = null
    private var deleted = false
    private var deletedAt: Long? = null
    private val mutex = Mutex()

    init {
        if (!isNew) {
            viewModelScope.launch {
                val n = repo.get(initialId)
                if (n == null) {
                    // Note no longer exists (e.g. vanished): continue as a new note, never
                    // "update" a row that isn't there and silently lose what the user types.
                    noteId = 0
                } else {
                    title = n.title
                    isChecklist = n.isChecklist
                    if (n.isChecklist) items.addAll(Checklist.parse(n.content)) else body = TextFieldValue(n.content)
                    colorIndex = n.colorIndex
                    pinned = n.pinned
                    reminderAt = n.reminderAt
                    expiresAt = n.expiresAt
                    archived = n.archived
                    deletedAt = n.deletedAt
                    createdAt = n.createdAt
                    editedAt = n.updatedAt
                    lastSaved = n
                }
                loaded = true
            }
        }
    }

    // --- Edits -------------------------------------------------------------------------------

    fun updateTitle(value: String) { title = value }
    /** Typing in the body. Records undo history and continues lists when Enter is pressed. */
    fun updateBody(value: TextFieldValue) {
        if (value.text == body.text) {
            body = value // selection/composition change only
            return
        }
        recordHistory(force = false)
        val continued = TextFormat.continueList(body.text, value.toEdit())
        body = continued?.toTextFieldValue() ?: value
    }

    // --- Formatting & undo -------------------------------------------------------------------

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastHistoryAt = 0L
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Typing is grouped into ~1 s chunks; formatting actions are always their own undo step. */
    private fun recordHistory(force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || now - lastHistoryAt > HISTORY_GROUP_MS || undoStack.isEmpty()) {
            undoStack.addLast(body)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            redoStack.clear()
        }
        lastHistoryAt = now
        canUndo = undoStack.isNotEmpty()
        canRedo = false
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(body)
        body = previous
        lastHistoryAt = 0
        canUndo = undoStack.isNotEmpty()
        canRedo = true
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(body)
        body = next
        lastHistoryAt = 0
        canUndo = true
        canRedo = redoStack.isNotEmpty()
    }

    private fun applyEdit(change: (Edit) -> Edit) {
        if (isChecklist) return
        val result = change(body.toEdit())
        if (result.text == body.text && result.start == body.selection.start && result.end == body.selection.end) return
        recordHistory(force = true)
        body = result.toTextFieldValue()
        lastHistoryAt = 0
    }

    fun toggleInline(kind: Markup.InlineKind) = applyEdit { TextFormat.toggleInline(it, kind) }
    fun toggleLine(kind: Markup.LineKind) = applyEdit { TextFormat.toggleLine(it, kind) }
    fun clearFormatting() = applyEdit { TextFormat.clearFormatting(it) }

    private fun TextFieldValue.toEdit() = Edit(text, selection.start, selection.end)
    private fun Edit.toTextFieldValue() = TextFieldValue(text, TextRange(start, end))
    fun updateColor(index: Int) { colorIndex = index }

    fun togglePin() {
        if (loaded) pinned = !pinned
    }

    fun setReminder(at: Long?) { reminderAt = at }
    fun setExpiry(at: Long?) { expiresAt = at }

    /** Inserts e.g. "12 Nov 2026, 00:14" at the cursor (replacing any selection). */
    fun insertTimestamp() {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        applyEdit { e -> Edit(e.text.replaceRange(e.min, e.max, stamp), e.min + stamp.length) }
    }

    fun toggleChecklist() {
        if (!loaded) return
        undoStack.clear()
        redoStack.clear()
        canUndo = false
        canRedo = false
        if (isChecklist) {
            body = TextFieldValue(Checklist.toPlainText(items))
            items.clear()
            isChecklist = false
        } else {
            items.clear()
            // "- milk" / "1. eggs" / "**bread**" become plain items "milk", "eggs", "bread".
            items.addAll(Checklist.fromPlainText(Markup.removeAll(body.text)))
            if (items.isEmpty()) items.add(Checklist.newItem())
            body = TextFieldValue("")
            isChecklist = true
        }
    }

    fun updateItem(id: Long, text: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(text = text.replace("\n", ""))
    }

    fun toggleItem(id: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(checked = !items[i].checked)
    }

    /** Adds an empty item after [afterId] (or at the end) and returns its id for focusing. */
    fun addItem(afterId: Long? = null): Long {
        val item = Checklist.newItem()
        val at = if (afterId == null) items.size else items.indexOfFirst { it.id == afterId } + 1
        items.add(at.coerceIn(0, items.size), item)
        return item.id
    }

    fun removeItem(id: Long) {
        items.removeAll { it.id == id }
    }

    // --- Derived -----------------------------------------------------------------------------

    private fun currentContent(): String =
        if (isChecklist) Checklist.serialize(items) else body.text

    val wordCount: Int
        get() {
            val text = title + " " + if (isChecklist) Checklist.toPlainText(items) else body.text
            return text.split(Regex("\\s+")).count { it.isNotBlank() }
        }

    private val isBlank get() = title.isBlank() && currentContent().isBlank()

    private fun snapshot() = Note(
        id = noteId,
        title = title,
        content = currentContent(),
        colorIndex = colorIndex,
        pinned = pinned,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        isChecklist = isChecklist,
        reminderAt = reminderAt,
        expiresAt = expiresAt,
        archived = archived,
        deletedAt = deletedAt,
    )

    // --- Persistence -------------------------------------------------------------------------

    /** Idempotent: safe to call on every exit and every time the app goes to the background. */
    private suspend fun save() = mutex.withLock {
        if (!loaded || deleted) return@withLock
        if (isBlank) {
            // An emptied note is discarded rather than kept as a blank card.
            if (noteId != 0L) {
                repo.deleteSilently(snapshot())
                noteId = 0
                lastSaved = null
            }
            return@withLock
        }
        val note = snapshot()
        val prev = lastSaved
        if (prev != null && prev.copy(updatedAt = note.updatedAt) == note) return@withLock

        if (noteId == 0L) {
            noteId = repo.insert(note)
        } else {
            repo.update(note)
        }
        lastSaved = note.copy(id = noteId)
        editedAt = note.updatedAt
    }

    fun saveAndThen(onDone: () -> Unit) {
        viewModelScope.launch {
            save()
            onDone()
        }
    }

    /** Runs in the app scope so it completes even if this screen is torn down. */
    fun saveInBackground() {
        repo.launch { save() }
    }

    fun delete(onDone: () -> Unit) {
        // Before the note has loaded, the fields are still empty; deleting then would make
        // "Undo" restore a blank note and lose the real content.
        if (!loaded) return
        viewModelScope.launch {
            mutex.withLock {
                deleted = true
                if (noteId != 0L) repo.delete(snapshot())
            }
            onDone()
        }
    }

    /** Archives (or un-archives) the note and leaves the editor. The board offers Undo. */
    fun toggleArchive(onDone: () -> Unit) {
        if (!loaded) return
        viewModelScope.launch {
            save()
            mutex.withLock {
                if (noteId != 0L) {
                    deleted = true // the repository owns the note from here; skip further saves
                    repo.setArchived(snapshot().copy(updatedAt = lastSaved?.updatedAt ?: System.currentTimeMillis()), !archived)
                }
            }
            onDone()
        }
    }

    companion object {
        const val NEW_NOTE_ID = -1L
        const val ARG_NOTE_ID = "noteId"
        private const val HISTORY_GROUP_MS = 1_000L
        private const val MAX_HISTORY = 100

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StickyNotesApp
                val id = createSavedStateHandle().get<Long>(ARG_NOTE_ID) ?: NEW_NOTE_ID
                NoteEditorViewModel(app.repository, id, app.settings.defaultColorIndex.value)
            }
        }
    }
}
