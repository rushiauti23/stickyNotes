package dev.rushi.stickynotes.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rushi.stickynotes.LocalAppLock
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.UndoEvent
import dev.rushi.stickynotes.ui.components.CardAction
import dev.rushi.stickynotes.ui.components.NoteCard
import dev.rushi.stickynotes.ui.editor.NoteEditorViewModel

@Composable
fun NoteListScreen(
    onOpenNote: (Long) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: NoteListViewModel = viewModel(factory = NoteListViewModel.Factory),
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val pendingUndo by vm.pendingUndo.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val selectedTag by vm.selectedTag.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.purgeExpired() }

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
        topBar = {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Notes",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    OverflowMenu(onOpenArchive, onOpenTrash, onOpenSettings)
                }
                Spacer(Modifier.height(12.dp))
                SearchField(
                    query = query,
                    onQueryChange = vm::onQueryChange,
                    modifier = Modifier.padding(end = 12.dp),
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TagRow(tags = tags, selected = selectedTag, onSelect = vm::onTagSelected)
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenNote(NoteEditorViewModel.NEW_NOTE_ID) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = notes
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                searching = query.isNotBlank() || selectedTag != null,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val pinned = list.filter { it.pinned }
                val others = list.filterNot { it.pinned }
                val actionsFor: (Note) -> List<CardAction> = { note ->
                    listOf(
                        CardAction(if (note.pinned) "Unpin" else "Pin", Icons.Outlined.PushPin) { vm.togglePin(note) },
                        CardAction("Archive", Icons.Outlined.Archive) { vm.archive(note) },
                        CardAction("Delete", Icons.Outlined.Delete) { vm.delete(note) },
                    )
                }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalItemSpacing = 10.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (pinned.isNotEmpty()) {
                        item(key = "header_pinned", span = StaggeredGridItemSpan.FullLine) {
                            SectionHeader("Pinned")
                        }
                        items(pinned, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onOpenNote(note.id) },
                                actions = actionsFor(note),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (others.isNotEmpty()) {
                            item(key = "header_others", span = StaggeredGridItemSpan.FullLine) {
                                SectionHeader("Others")
                            }
                        }
                    }
                    items(others, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            actions = actionsFor(note),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(onOpenArchive: () -> Unit, onOpenTrash: () -> Unit, onOpenSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val appLock = LocalAppLock.current
    val lockEnabled by appLock.enabled.collectAsStateWithLifecycle()
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Archive") },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                onClick = { open = false; onOpenArchive() },
            )
            DropdownMenuItem(
                text = { Text("Trash") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { open = false; onOpenTrash() },
            )
            if (lockEnabled) {
                DropdownMenuItem(
                    text = { Text("Lock now") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    onClick = { open = false; appLock.lockNow() },
                )
            }
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = { open = false; onOpenSettings() },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TagRow(tags: List<String>, selected: String?, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        rowItems(tags, key = { it }) { tag ->
            FilterChip(
                selected = tag == selected,
                onClick = { onSelect(tag) },
                label = { Text("#$tag") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = tag == selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

@Composable
private fun EmptyState(searching: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (searching) "Nothing matches" else "No notes yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (searching) "Try another word or tag." else "Tap + to write one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
