package dev.rushi.stickynotes.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.NoteRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NoteEditorViewModel(
    private val repo: NoteRepository,
    initialId: Long,
) : ViewModel() {

    val isNew = initialId == NEW_NOTE_ID

    var title by mutableStateOf("")
        private set
    var content by mutableStateOf("")
        private set
    var colorIndex by mutableIntStateOf(0) // 0 = "Plain"
        private set
    var pinned by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(isNew)
        private set

    private var noteId: Long = if (isNew) 0 else initialId
    private var createdAt = System.currentTimeMillis()
    private var lastSaved: Note? = null
    private var deleted = false
    private val mutex = Mutex()

    init {
        if (!isNew) {
            viewModelScope.launch {
                repo.get(initialId)?.let { n ->
                    title = n.title
                    content = n.content
                    colorIndex = n.colorIndex
                    pinned = n.pinned
                    createdAt = n.createdAt
                    lastSaved = n
                }
                loaded = true
            }
        }
    }

    fun updateTitle(value: String) { title = value }
    fun updateContent(value: String) { content = value }
    fun updateColor(index: Int) { colorIndex = index }
    fun togglePin() {
        if (loaded) pinned = !pinned
    }

    private val isBlank get() = title.isBlank() && content.isBlank()

    private fun snapshot() = Note(
        id = noteId,
        title = title,
        content = content,
        colorIndex = colorIndex,
        pinned = pinned,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
    )

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
        val prev = lastSaved
        if (prev != null && prev.title == title && prev.content == content &&
            prev.colorIndex == colorIndex && prev.pinned == pinned
        ) return@withLock

        val note = snapshot()
        if (noteId == 0L) {
            noteId = repo.insert(note)
        } else {
            repo.update(note)
        }
        lastSaved = note.copy(id = noteId)
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

    companion object {
        const val NEW_NOTE_ID = -1L
        const val ARG_NOTE_ID = "noteId"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StickyNotesApp
                val id = createSavedStateHandle().get<Long>(ARG_NOTE_ID) ?: NEW_NOTE_ID
                NoteEditorViewModel(app.repository, id)
            }
        }
    }
}
