package dev.rushi.stickynotes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dev.rushi.stickynotes.MainActivity
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.isExpired
import dev.rushi.stickynotes.data.isTrashed
import dev.rushi.stickynotes.data.plainBody
import dev.rushi.stickynotes.security.Authenticator
import dev.rushi.stickynotes.ui.theme.StickyNotesTheme
import dev.rushi.stickynotes.ui.theme.noteBorderColor
import dev.rushi.stickynotes.ui.theme.noteColor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Opens when a "Sticky note" widget is added (or reconfigured): pick which note it shows.
 * Behind the app lock, because it lists every note.
 */
class NotePickerActivity : FragmentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val unlocked = mutableStateOf(false)
    private lateinit var authenticator: Authenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Until a note is picked, backing out cancels adding the widget.
        setResult(RESULT_CANCELED, resultIntent())

        // This activity is exported for the launcher; only act on widgets that are really ours.
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        if (info?.provider != ComponentName(this, NoteWidgetReceiver::class.java)) {
            finish()
            return
        }

        val app = application as StickyNotesApp
        val lockOn = app.appLock.enabled.value
        if (lockOn) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        unlocked.value = !lockOn
        authenticator = Authenticator(this)

        setContent {
            StickyNotesTheme {
                if (unlocked.value) {
                    Picker(
                        app = app,
                        onPick = ::bind,
                        onNewTodo = ::createTodo,
                        onClose = ::finish,
                    )
                } else {
                    Locked(onUnlock = ::askToUnlock, onClose = ::finish)
                }
            }
        }
        if (lockOn && savedInstanceState == null) askToUnlock()
    }

    private fun askToUnlock() {
        authenticator.authenticate("Unlock Sticky Notes", "Choose a note for the widget") {
            unlocked.value = true
        }
    }

    private fun bind(noteId: Long) {
        lifecycleScope.launch {
            WidgetNotes.set(this@NotePickerActivity, appWidgetId, noteId)
            val glanceId = GlanceAppWidgetManager(this@NotePickerActivity).getGlanceIdBy(appWidgetId)
            NoteWidget().update(this@NotePickerActivity, glanceId)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun createTodo() {
        val app = application as StickyNotesApp
        lifecycleScope.launch {
            val id = app.repository.insert(
                Note(title = "To-do", isChecklist = true, colorIndex = app.settings.defaultColorIndex.value),
            )
            WidgetNotes.set(this@NotePickerActivity, appWidgetId, id)
            val glanceId = GlanceAppWidgetManager(this@NotePickerActivity).getGlanceIdBy(appWidgetId)
            NoteWidget().update(this@NotePickerActivity, glanceId)
            setResult(RESULT_OK, resultIntent())
            // Open the new list so the first items can be typed in.
            startActivity(
                Intent(this@NotePickerActivity, MainActivity::class.java)
                    .setData(Uri.parse("stickynotes://note/$id"))
                    .putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, id),
            )
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(app: StickyNotesApp, onPick: (Long) -> Unit, onNewTodo: () -> Unit, onClose: () -> Unit) {
    val flow = remember {
        app.repository.notes.map { all ->
            all.filter { !it.isTrashed && !it.isExpired() }
                .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
        }
    }
    val notes by flow.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a note") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "The widget shows this note on your home screen, even while the app is locked. " +
                        "Checklist items can be ticked right on the widget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onNewTodo, modifier = Modifier.fillMaxWidth()) { Text("New to-do list") }
                Spacer(Modifier.height(8.dp))
            }
            val list = notes
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        "No notes yet. Start with a new to-do list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { note -> NoteChoice(note, onClick = { onPick(note.id) }) }
        }
    }
}

@Composable
private fun NoteChoice(note: Note, onClick: () -> Unit) {
    val body = note.plainBody().trim()
    val title = note.title.ifBlank { body.lineSequence().firstOrNull().orEmpty().ifBlank { "Untitled" } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(noteColor(note.colorIndex), MaterialTheme.shapes.medium)
            .border(1.dp, noteBorderColor(), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when {
                note.isChecklist -> "Checklist"
                note.title.isBlank() -> body.lineSequence().drop(1).joinToString(" ")
                else -> body.replace('\n', ' ')
            }
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (note.pinned) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
    }
}

@Composable
private fun Locked(onUnlock: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Notes are locked", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onUnlock) { Text("Unlock") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onClose) { Text("Cancel") }
    }
}
