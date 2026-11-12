package dev.rushi.stickynotes.ui.collections

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.NoteRepository
import dev.rushi.stickynotes.data.UndoEvent
import dev.rushi.stickynotes.data.isExpired
import dev.rushi.stickynotes.data.isTrashed
import dev.rushi.stickynotes.ui.components.CardAction
import dev.rushi.stickynotes.ui.components.NoteCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class CollectionMode { Archive, Trash }

class CollectionViewModel(private val repo: NoteRepository, val mode: CollectionMode) : ViewModel() {
    val notes: StateFlow<List<Note>?> = repo.notes.map { list ->
        when (mode) {
            CollectionMode.Archive -> list.filter { it.archived && !it.isTrashed && !it.isExpired() }
            CollectionMode.Trash -> list.filter { it.isTrashed }.sortedByDescending { it.deletedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingUndo: StateFlow<UndoEvent?> = repo.pendingUndo

    fun unarchive(note: Note) = repo.setArchived(note, false)
    fun trash(note: Note) = repo.delete(note)
    fun restore(note: Note) = repo.restoreFromTrash(note)
    fun deleteForever(note: Note) = repo.deleteForever(note)
    fun emptyTrash() = repo.emptyTrash()
    fun undo() = repo.undo()
    fun clearUndo() = repo.clearUndo()

    companion object {
        fun factory(mode: CollectionMode) = viewModelFactory {
            initializer {
                CollectionViewModel((this[APPLICATION_KEY] as StickyNotesApp).repository, mode)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    mode: CollectionMode,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    vm: CollectionViewModel = viewModel(key = mode.name, factory = CollectionViewModel.factory(mode)),
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val pendingUndo by vm.pendingUndo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmEmpty by remember { mutableStateOf(false) }
    val isTrash = mode == CollectionMode.Trash

    LaunchedEffect(pendingUndo) {
        val event = pendingUndo ?: return@LaunchedEffect
        val message = when (event.kind) {
            UndoEvent.Kind.Trashed -> "Moved to Trash"
            UndoEvent.Kind.Archived -> "Archived"
            UndoEvent.Kind.Unarchived -> "Moved back to notes"
        }
        val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undo() else vm.clearUndo()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (isTrash) "Trash" else "Archive") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isTrash && !notes.isNullOrEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) { Text("Empty") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val list = notes
        when {
            list == null -> Unit
            list.isEmpty() -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (isTrash) "Trash is empty" else "Nothing archived", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isTrash) "Deleted notes stay here for 30 days." else "Archive notes you want to keep but not see every day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isTrash) {
                    item(key = "hint", span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            "Notes in Trash are deleted for good after 30 days. Tap a note to restore it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { note ->
                    if (isTrash) {
                        val deletedAgo = note.deletedAt?.let {
                            "Deleted " + DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                        }
                        NoteCard(
                            note = note,
                            // Trashed notes aren't editable; tapping restores.
                            onClick = { vm.restore(note) },
                            actions = listOf(
                                CardAction("Restore", Icons.Outlined.RestoreFromTrash) { vm.restore(note) },
                                CardAction("Delete forever", Icons.Outlined.DeleteForever) { vm.deleteForever(note) },
                            ),
                            footer = deletedAgo,
                            modifier = Modifier.animateItem(),
                        )
                    } else {
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            actions = listOf(
                                CardAction("Unarchive", Icons.Outlined.Unarchive) { vm.unarchive(note) },
                                CardAction("Delete", Icons.Outlined.Delete) { vm.trash(note) },
                            ),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Trash?") },
            text = { Text("All notes in Trash will be deleted for good. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    vm.emptyTrash()
                }) { Text("Empty Trash") }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
}
