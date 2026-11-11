package dev.rushi.stickynotes.ui.editor

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rushi.stickynotes.ui.theme.NoteColors
import dev.rushi.stickynotes.ui.theme.noteColor
import dev.rushi.stickynotes.ui.theme.noteContentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    vm: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val context = LocalContext.current
    val background by animateColorAsState(noteColor(vm.colorIndex), tween(200), label = "noteBg")
    val ink = noteContentColor()
    val exit: () -> Unit = { vm.saveAndThen(onBack) }

    BackHandler(onBack = exit)
    // Saves if the user switches apps or the system kills the process mid-edit.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveInBackground() }

    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (vm.isNew) contentFocus.requestFocus() }

    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = ink,
        disabledContentColor = ink.copy(alpha = 0.3f),
    )

    Scaffold(
        containerColor = background,
        contentColor = ink,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = exit, colors = iconColors) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Disabled until the note has loaded (see NoteEditorViewModel.delete).
                    IconButton(onClick = vm::togglePin, enabled = vm.loaded, colors = iconColors) {
                        Icon(
                            if (vm.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (vm.pinned) "Unpin" else "Pin",
                        )
                    }
                    IconButton(
                        onClick = { shareNote(context, vm.title, vm.content) },
                        enabled = vm.loaded,
                        colors = iconColors,
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { vm.delete(onBack) }, enabled = vm.loaded, colors = iconColors) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            ColorPicker(
                selected = vm.colorIndex,
                onSelect = vm::updateColor,
                ink = ink,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            NoteField(
                value = vm.title,
                onValueChange = vm::updateTitle,
                placeholder = "Title",
                style = MaterialTheme.typography.headlineSmall,
                ink = ink,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            NoteField(
                value = vm.content,
                onValueChange = vm::updateContent,
                placeholder = "Note",
                style = MaterialTheme.typography.bodyLarge,
                ink = ink,
                imeAction = ImeAction.Default,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 320.dp)
                    .focusRequester(contentFocus),
            )
        }
    }
}

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    ink: Color,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = ink),
        cursorBrush = SolidColor(ink),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = imeAction,
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = ink.copy(alpha = 0.4f))
                inner()
            }
        },
    )
}

/** Row of small colour dots. Each dot sits in a 44dp touch target. */
@Composable
private fun ColorPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        NoteColors.names.forEachIndexed { index, name ->
            val isSelected = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .semantics { contentDescription = name },
            ) {
                Box(
                    Modifier
                        .size(if (isSelected) 26.dp else 22.dp)
                        .clip(CircleShape)
                        .background(noteColor(index))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) ink else ink.copy(alpha = 0.2f),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

private fun shareNote(context: Context, title: String, content: String) {
    val text = listOf(title, content).filter { it.isNotBlank() }.joinToString("\n\n")
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
    }
    context.startActivity(Intent.createChooser(send, null))
}
