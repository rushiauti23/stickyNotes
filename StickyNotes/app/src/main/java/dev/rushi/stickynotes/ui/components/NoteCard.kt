package dev.rushi.stickynotes.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rushi.stickynotes.data.Checklist
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.ui.common.reminderLabel
import dev.rushi.stickynotes.ui.common.styledPreview
import dev.rushi.stickynotes.ui.common.vanishLabel
import dev.rushi.stickynotes.ui.theme.noteBorderColor
import dev.rushi.stickynotes.ui.theme.noteColor
import dev.rushi.stickynotes.ui.theme.noteContentColor

/** An entry in a note's long-press menu. */
data class CardAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private val CardShape = RoundedCornerShape(14.dp)

/**
 * A note on the board. Tap runs [onClick]; long-press opens a menu of [actions].
 * [footer] replaces the "edited" line (e.g. "Deleted 3 days ago" in Trash).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    actions: List<CardAction>,
    modifier: Modifier = Modifier,
    footer: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val ink = noteContentColor()
    val meta = ink.copy(alpha = 0.7f) // ≥4.5:1 on every tint

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
                        if (actions.isNotEmpty()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
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
                                tint = meta,
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
                        text = remember(note.content, ink) { styledPreview(note.content, ink) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink.copy(alpha = 0.85f),
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                note.reminderAt?.let { MetaLine(Icons.Outlined.Notifications, reminderLabel(context, it), meta) }
                note.expiresAt?.let { MetaLine(Icons.Outlined.HourglassEmpty, vanishLabel(it), meta) }
                Text(
                    text = footer ?: DateUtils.getRelativeTimeSpanString(
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
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

private const val PREVIEW_ITEMS = 6

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

@Composable
private fun MetaLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
