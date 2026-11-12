package dev.rushi.stickynotes.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.rushi.stickynotes.MainActivity
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.plainBody

/**
 * Home-screen widget: pinned notes (or the most recent ones). When the app lock is on it
 * shows no note content at all, only "Notes are locked".
 */
class NotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as StickyNotesApp
        val locked = app.appLock.enabled.value
        val notes = if (locked) emptyList() else app.repository.widgetNotes()
        val anyPinned = notes.any { it.pinned }
        provideContent { WidgetContent(context, notes, locked, anyPinned) }
    }
}

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}

private val Paper = ColorProvider(day = Color(0xFFFAFAF8), night = Color(0xFF121211))
private val Ink = ColorProvider(day = Color(0xFF1C1B19), night = Color(0xFFE8E6E1))
private val Muted = ColorProvider(day = Color(0xFF6B6A66), night = Color(0xFFA3A19B))

@Composable
private fun WidgetContent(context: Context, notes: List<Note>, locked: Boolean, anyPinned: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Paper)
            .cornerRadius(20.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (anyPinned) "Pinned" else "Notes",
                style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(appIntent(context))),
            )
            Text(
                text = "+",
                style = TextStyle(color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier
                    .padding(horizontal = 10.dp)
                    .clickable(actionStartActivity(newNoteIntent(context))),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        when {
            locked -> Hint(context, "Notes are locked")
            notes.isEmpty() -> Hint(context, "No notes yet. Tap + to write one.")
            else -> LazyColumn {
                items(notes, itemId = { it.id }) { note -> NoteRow(context, note) }
            }
        }
    }
}

@Composable
private fun Hint(context: Context, text: String) {
    Text(
        text = text,
        style = TextStyle(color = Muted, fontSize = 13.sp),
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(appIntent(context))),
    )
}

@Composable
private fun NoteRow(context: Context, note: Note) {
    val body = note.plainBody().trim()
    val title = note.title.ifBlank { body.lineSequence().firstOrNull().orEmpty() }
    val preview = if (note.title.isBlank()) body.lineSequence().drop(1).joinToString(" ") else body.replace('\n', ' ')
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(actionStartActivity(openNoteIntent(context, note.id))),
    ) {
        Text(
            text = title,
            style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        if (preview.isNotBlank()) {
            Text(text = preview, style = TextStyle(color = Muted, fontSize = 13.sp), maxLines = 2)
        }
    }
}

// Explicit intents to our own activity. A distinct data URI per target keeps each tap's
// PendingIntent separate.
private fun appIntent(context: Context) =
    Intent(context, MainActivity::class.java).setData(Uri.parse("stickynotes://open"))

private fun newNoteIntent(context: Context) =
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("stickynotes://new"))
        .putExtra(MainActivity.EXTRA_NEW_NOTE, true)

private fun openNoteIntent(context: Context, id: Long) =
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("stickynotes://note/$id"))
        .putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, id)
