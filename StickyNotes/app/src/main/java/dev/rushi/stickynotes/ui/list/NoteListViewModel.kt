package dev.rushi.stickynotes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NoteListViewModel(private val repo: NoteRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null while the first DB read is in flight, so the empty state doesn't flash. */
    val notes: StateFlow<List<Note>?> =
        combine(repo.notes, _query) { list, q ->
            val term = q.trim()
            if (term.isEmpty()) list
            else list.filter {
                it.title.contains(term, ignoreCase = true) || it.content.contains(term, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingUndo: StateFlow<Note?> = repo.pendingUndo

    fun onQueryChange(q: String) { _query.value = q }
    fun togglePin(note: Note) = repo.togglePin(note)
    fun delete(note: Note) = repo.delete(note)
    fun undoDelete() = repo.undoDelete()
    fun clearUndo() = repo.clearUndo()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                NoteListViewModel((this[APPLICATION_KEY] as StickyNotesApp).repository)
            }
        }
    }
}
