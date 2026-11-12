package dev.rushi.stickynotes.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import dev.rushi.stickynotes.MainActivity
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.Checklist
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.isExpired
import dev.rushi.stickynotes.data.isTrashed
import dev.rushi.stickynotes.data.text.Markup
import dev.rushi.stickynotes.ui.theme.NoteColors
import kotlinx.coroutines.flow.map

/**
 * "Sticky note" widget: one note you picked, on your home screen, in its own colour, like a
 * paper note on a desk. Checklist items can be ticked right on the widget. You chose to put
 * this note on the home screen, so it stays visible while the app lock is on; the app itself
 * still asks to unlock when you tap through.
 */
class NoteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as StickyNotesApp
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val noteId = WidgetNotes.noteFor(context, appWidgetId)
        val initial = noteId?.let { app.repository.get(it) }?.takeIf { it.isShowable() }
        provideContent {
            // Follow the note while the widget is on screen, so edits and ticks show at once.
            val flow = remember(noteId) {
                app.repository.notes.map { notes -> notes.firstOrNull { it.id == noteId && it.isShowable() } }
            }
            val note by flow.collectAsState(initial = initial)
            NoteWidgetContent(context, noteId, note)
        }
    }
}

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetNotes.remove(context, appWidgetIds)
    }
}

private fun Note.isShowable() = !isTrashed && !isExpired()

private val NoteInk = ColorProvider(day = Color(0xFF1C1B19), night = Color(0xFFE8E6E1))
private val NoteMuted = ColorProvider(day = Color(0xFF6B6A66), night = Color(0xFFA3A19B))

// The day/night ColorProvider(...) imported above is a factory function; its return type
// lives in another package, so let the compiler infer it.
private fun paper(index: Int) = NoteColors.count.let { count ->
    val i = index.coerceIn(0, count - 1)
    ColorProvider(day = NoteColors.light[i], night = NoteColors.dark[i])
}

@Composable
private fun NoteWidgetContent(context: Context, noteId: Long?, note: Note?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(paper(note?.colorIndex ?: 0))
            .cornerRadius(20.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (note == null) {
            Text(
                text = if (noteId == null) "No note chosen" else "This note was deleted",
                style = TextStyle(color = NoteInk, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "Hold the widget and choose Reconfigure (or remove and add it again) to pick a note.",
                style = TextStyle(color = NoteMuted, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(actionStartActivity(openAppIntent(context))),
            )
            return@Column
        }

        val items = if (note.isChecklist) Checklist.parse(note.content) else emptyList()
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.title.ifBlank { if (note.isChecklist) "To-do" else "Note" },
                style = TextStyle(color = NoteInk, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openNoteIntent(context, note.id))),
            )
            if (items.any { it.checked }) {
                // Start the day fresh: untick everything.
                Text(
                    text = "↺",
                    style = TextStyle(color = NoteMuted, fontSize = 18.sp),
                    modifier = GlanceModifier
                        .padding(horizontal = 8.dp)
                        .clickable(actionRunCallback<UncheckAllAction>(actionParametersOf(NoteIdKey to note.id))),
                )
            }
            Text(
                text = if (note.isChecklist) "+" else "✎",
                style = TextStyle(color = NoteInk, fontSize = 20.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier
                    .padding(start = 8.dp)
                    .clickable(actionStartActivity(openNoteIntent(context, note.id))),
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        if (note.isChecklist) {
            if (items.isEmpty()) {
                Hint(context, note.id, "Empty list. Tap + to add items.")
            } else {
                // Unticked first, like the app's checklist editor.
                val ordered = items.withIndex().sortedBy { it.value.checked }
                LazyColumn {
                    items(ordered, itemId = { it.index.toLong() }) { entry ->
                        val item = entry.value
                        CheckBox(
                            checked = item.checked,
                            onCheckedChange = actionRunCallback<ToggleItemAction>(
                                actionParametersOf(
                                    NoteIdKey to note.id,
                                    IndexKey to entry.index,
                                    TextKey to item.text,
                                ),
                            ),
                            text = item.text,
                            style = TextStyle(
                                color = if (item.checked) NoteMuted else NoteInk,
                                fontSize = 14.sp,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                            ),
                            maxLines = 3,
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            val body = Markup.strip(note.content).trim()
            if (body.isEmpty()) {
                Hint(context, note.id, "Empty note. Tap to write.")
            } else {
                LazyColumn {
                    item {
                        Text(
                            text = body,
                            style = TextStyle(color = NoteInk, fontSize = 14.sp),
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .clickable(actionStartActivity(openNoteIntent(context, note.id))),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(context: Context, noteId: Long, text: String) {
    Text(
        text = text,
        style = TextStyle(color = NoteMuted, fontSize = 13.sp),
        modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openNoteIntent(context, noteId))),
    )
}

private val NoteIdKey = ActionParameters.Key<Long>("noteId")
private val IndexKey = ActionParameters.Key<Int>("index")
private val TextKey = ActionParameters.Key<String>("text")

/** Ticks or unticks one checklist item from the widget, without opening the app. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[NoteIdKey] ?: return
        val index = parameters[IndexKey] ?: return
        val text = parameters[TextKey] ?: return
        editChecklist(context, noteId) { Checklist.toggleItem(it, index, text) }
    }
}

/** Unticks every item of the widget's checklist. */
class UncheckAllAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[NoteIdKey] ?: return
        editChecklist(context, noteId, Checklist::uncheckAll)
    }
}

private suspend fun editChecklist(context: Context, noteId: Long, change: (String) -> String) {
    val app = context.applicationContext as StickyNotesApp
    val note = app.repository.get(noteId)?.takeIf { it.isChecklist && it.isShowable() } ?: return
    val content = change(note.content)
    if (content == note.content) return
    app.repository.update(note.copy(content = content, updatedAt = System.currentTimeMillis()))
    NoteWidget().updateAll(context)
}

private fun openAppIntent(context: Context) =
    Intent(context, MainActivity::class.java).setData(Uri.parse("stickynotes://open"))

private fun openNoteIntent(context: Context, id: Long) =
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("stickynotes://note/$id"))
        .putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, id)
