package dev.rushi.stickynotes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.NoteRepository
import dev.rushi.stickynotes.data.isExpired
import dev.rushi.stickynotes.data.searchableText
import dev.rushi.stickynotes.data.tags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(private val repo: NoteRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val live = repo.notes.map { list -> list.filterNot { it.isExpired() } }

    /** Most-used #tags first, for the filter chips. */
    val tags: StateFlow<List<String>> = live.map { list ->
        list.flatMap { it.tags() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(MAX_TAGS)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null while the first DB read is in flight, so the empty state doesn't flash. */
    val notes: StateFlow<List<Note>?> =
        combine(live, _query, _selectedTag) { list, q, tag ->
            val term = q.trim()
            list.filter { note ->
                (tag == null || tag in note.tags()) &&
                    (term.isEmpty() || note.searchableText().contains(term, ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingUndo: StateFlow<Note?> = repo.pendingUndo

    fun onQueryChange(q: String) { _query.value = q }

    fun onTagSelected(tag: String) {
        _selectedTag.value = if (_selectedTag.value == tag) null else tag
    }

    fun togglePin(note: Note) = repo.togglePin(note)
    fun delete(note: Note) = repo.delete(note)
    fun undoDelete() = repo.undoDelete()
    fun clearUndo() = repo.clearUndo()

    /** Called whenever the board comes into view, so vanished notes are really gone. */
    fun purgeExpired() {
        viewModelScope.launch { repo.purgeExpired() }
    }

    companion object {
        private const val MAX_TAGS = 20

        val Factory = viewModelFactory {
            initializer {
                NoteListViewModel((this[APPLICATION_KEY] as StickyNotesApp).repository)
            }
        }
    }
}
