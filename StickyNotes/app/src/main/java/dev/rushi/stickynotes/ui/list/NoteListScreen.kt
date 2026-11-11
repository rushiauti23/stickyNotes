package dev.rushi.stickynotes.ui.list

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rushi.stickynotes.data.Checklist
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.ui.common.reminderLabel
import dev.rushi.stickynotes.ui.common.vanishLabel
import dev.rushi.stickynotes.ui.editor.NoteEditorViewModel
import dev.rushi.stickynotes.ui.theme.noteBorderColor
import dev.rushi.stickynotes.ui.theme.noteColor
import dev.rushi.stickynotes.ui.theme.noteContentColor

private val CardShape = RoundedCornerShape(14.dp)

@Composable
fun NoteListScreen(
    onOpenNote: (Long) -> Unit,
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
        if (pendingUndo == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "Note deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearUndo()
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
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
                                onTogglePin = { vm.togglePin(note) },
                                onDelete = { vm.delete(note) },
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
                            onTogglePin = { vm.togglePin(note) },
                            onDelete = { vm.delete(note) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
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
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val ink = noteContentColor()

    Box(modifier) {
        Surface(
            color = noteColor(note.colorIndex),
            contentColor = ink,
            shape = CardShape,
            border = BorderStroke(1.dp, noteBorderColor()),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onLongClickLabel = "Note options",
                ),
        ) {
            Column(Modifier.padding(16.dp)) {
                if (note.title.isNotBlank() || note.pinned) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (note.pinned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = ink.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    if (note.content.isNotBlank()) Spacer(Modifier.height(6.dp))
                }
                if (note.isChecklist) {
                    ChecklistPreview(note.content, ink)
                } else if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink.copy(alpha = 0.85f),
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val context = LocalContext.current
                val meta = ink.copy(alpha = 0.7f) // ≥4.5:1 on every tint
                note.reminderAt?.let { MetaLine(Icons.Outlined.Notifications, reminderLabel(context, it), meta) }
                note.expiresAt?.let { MetaLine(Icons.Outlined.HourglassEmpty, vanishLabel(it), meta) }
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        note.updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = meta,
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (note.pinned) "Unpin" else "Pin") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                onClick = { menuOpen = false; onTogglePin() },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

@Composable
private fun ChecklistPreview(content: String, ink: Color) {
    val items = remember(content) { Checklist.parse(content) }
    val done = items.count { it.checked }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.take(PREVIEW_ITEMS).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = if (item.checked) "Done" else "To do",
                    tint = ink.copy(alpha = if (item.checked) 0.5f else 0.8f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = if (item.checked) 0.55f else 0.85f),
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (items.isNotEmpty()) {
            val more = items.size - PREVIEW_ITEMS
            Text(
                (if (more > 0) "+$more more · " else "") + "$done/${items.size} done",
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.7f),
            )
        }
    }
}

private const val PREVIEW_ITEMS = 6

@Composable
private fun MetaLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
