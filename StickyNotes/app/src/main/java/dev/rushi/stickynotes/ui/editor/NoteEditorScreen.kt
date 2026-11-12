package dev.rushi.stickynotes.ui.editor

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rushi.stickynotes.LocalAppLock
import dev.rushi.stickynotes.MainActivity
import dev.rushi.stickynotes.data.text.Markup
import dev.rushi.stickynotes.ui.common.markupVisualTransformation
import dev.rushi.stickynotes.data.ChecklistItem
import dev.rushi.stickynotes.reminders.Notifications as ReminderNotifications
import dev.rushi.stickynotes.ui.common.reminderLabel
import dev.rushi.stickynotes.ui.common.vanishLabel
import dev.rushi.stickynotes.ui.theme.NoteColors
import dev.rushi.stickynotes.ui.theme.noteColor
import dev.rushi.stickynotes.ui.theme.noteContentColor
import kotlinx.coroutines.launch

private enum class Sheet { None, Reminder, CustomReminder, Vanish }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    vm: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val background by animateColorAsState(noteColor(vm.colorIndex), tween(200), label = "noteBg")
    val ink = noteContentColor()
    val exit: () -> Unit = { vm.saveAndThen(onBack) }

    var sheet by remember { mutableStateOf(Sheet.None) }
    var showColors by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    val appLock = LocalAppLock.current

    BackHandler(onBack = exit)
    // Saves if the user switches apps or the system kills the process mid-edit.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveInBackground() }

    val bodyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (vm.isNew) runCatching { bodyFocus.requestFocus() } }

    // Android 13+: notifications need runtime permission. Asked only when a reminder is set.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbar.showSnackbar("Notifications are off, so this reminder can't alert you. You can allow them in Settings.")
            }
        }
    }
    val setReminder: (Long?) -> Unit = { at ->
        vm.setReminder(at)
        if (at != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ReminderNotifications.canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = ink,
        disabledContentColor = ink.copy(alpha = 0.3f),
    )

    Scaffold(
        containerColor = background,
        contentColor = ink,
        snackbarHost = { SnackbarHost(snackbar) },
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
                        onClick = {
                            appLock.expectExternalActivity()
                            shareNote(context, vm.title, vm.shareText())
                        },
                        enabled = vm.loaded,
                        colors = iconColors,
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { vm.toggleArchive(onBack) }, enabled = vm.loaded, colors = iconColors) {
                        Icon(
                            if (vm.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = if (vm.archived) "Unarchive" else "Archive",
                        )
                    }
                    IconButton(onClick = { vm.delete(onBack) }, enabled = vm.loaded, colors = iconColors) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                AnimatedVisibility(
                    visible = showFormat && !vm.isChecklist,
                    enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(140)),
                ) {
                    FormatBar(vm = vm, ink = ink)
                }
                AnimatedVisibility(
                    visible = showColors,
                    enter = fadeIn(tween(150)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(140)),
                ) {
                    ColorPicker(selected = vm.colorIndex, onSelect = vm::updateColor, ink = ink)
                }
                HorizontalDivider(color = ink.copy(alpha = 0.08f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    ToolButton(
                        icon = Icons.Outlined.CheckBox,
                        label = if (vm.isChecklist) "Turn into text" else "Turn into checklist",
                        active = vm.isChecklist,
                        ink = ink,
                        enabled = vm.loaded,
                        onClick = vm::toggleChecklist,
                    )
                    ToolButton(Icons.Outlined.Notifications, "Reminder", vm.reminderAt != null, ink, vm.loaded) {
                        sheet = Sheet.Reminder
                    }
                    ToolButton(Icons.Outlined.HourglassEmpty, "Vanish", vm.expiresAt != null, ink, vm.loaded) {
                        sheet = Sheet.Vanish
                    }
                    ToolButton(
                        icon = Icons.Outlined.TextFormat,
                        label = "Text formatting",
                        active = showFormat && !vm.isChecklist,
                        ink = ink,
                        enabled = vm.loaded && !vm.isChecklist,
                    ) { showFormat = !showFormat }
                    ToolButton(Icons.Outlined.Palette, "Colour", showColors, ink, vm.loaded) {
                        showColors = !showColors
                    }
                    ToolButton(
                        icon = Icons.Outlined.Schedule,
                        label = "Insert date and time",
                        active = false,
                        ink = ink,
                        enabled = vm.loaded && !vm.isChecklist,
                        onClick = vm::insertTimestamp,
                    )
                }
            }
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
            TitleField(
                value = vm.title,
                onValueChange = vm::updateTitle,
                ink = ink,
                onNext = { if (!vm.isChecklist) runCatching { bodyFocus.requestFocus() } },
            )

            val reminder = vm.reminderAt
            val expiry = vm.expiresAt
            if (reminder != null || expiry != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (reminder != null) {
                        InfoPill(Icons.Outlined.Notifications, reminderLabel(context, reminder), ink) {
                            sheet = Sheet.Reminder
                        }
                    }
                    if (expiry != null) {
                        InfoPill(Icons.Outlined.HourglassEmpty, vanishLabel(expiry), ink) { sheet = Sheet.Vanish }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (vm.isChecklist) {
                ChecklistEditor(
                    items = vm.items,
                    ink = ink,
                    onText = vm::updateItem,
                    onToggle = vm::toggleItem,
                    onAddAfter = vm::addItem,
                    onRemove = vm::removeItem,
                )
            } else {
                BodyField(
                    value = vm.body,
                    onValueChange = vm::updateBody,
                    ink = ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 280.dp)
                        .focusRequester(bodyFocus),
                )
            }

            Spacer(Modifier.height(24.dp))
            Footer(editedAt = vm.editedAt, words = vm.wordCount, ink = ink)
            Spacer(Modifier.height(16.dp))
        }
    }

    when (sheet) {
        Sheet.Reminder -> ReminderSheet(
            current = vm.reminderAt,
            onPick = { setReminder(it) },
            onPickCustom = { sheet = Sheet.CustomReminder },
            onRemove = { setReminder(null) },
            onDismiss = { if (sheet == Sheet.Reminder) sheet = Sheet.None },
        )
        Sheet.CustomReminder -> DateTimePickerDialog(
            onPicked = {
                setReminder(it)
                sheet = Sheet.None
            },
            onDismiss = { sheet = Sheet.None },
        )
        Sheet.Vanish -> VanishSheet(
            current = vm.expiresAt,
            onPick = vm::setExpiry,
            onDismiss = { sheet = Sheet.None },
        )
        Sheet.None -> Unit
    }
}

private fun NoteEditorViewModel.shareText(): String =
    if (isChecklist) {
        items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
    } else {
        Markup.strip(body.text)
    }

// --- Text fields -----------------------------------------------------------------------------

@Composable
private fun TitleField(value: String, onValueChange: (String) -> Unit, ink: Color, onNext: () -> Unit) {
    val style = MaterialTheme.typography.headlineSmall
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = ink),
        cursorBrush = SolidColor(ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner -> Placeholder(value.isEmpty(), "Title", style, ink) { inner() } },
    )
}

@Composable
private fun BodyField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style.copy(color = ink),
        cursorBrush = SolidColor(ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier,
        visualTransformation = remember(ink) { markupVisualTransformation(ink) },
        decorationBox = { inner -> Placeholder(value.text.isEmpty(), "Note. Add #tags to group notes.", style, ink) { inner() } },
    )
}

@Composable
private fun Placeholder(show: Boolean, text: String, style: TextStyle, ink: Color, field: @Composable () -> Unit) {
    Box {
        if (show) Text(text, style = style, color = ink.copy(alpha = 0.4f))
        field()
    }
}

// --- Checklist -------------------------------------------------------------------------------

@Composable
private fun ChecklistEditor(
    items: List<ChecklistItem>,
    ink: Color,
    onText: (Long, String) -> Unit,
    onToggle: (Long) -> Unit,
    onAddAfter: (Long?) -> Long,
    onRemove: (Long) -> Unit,
) {
    val requesters = remember { mutableMapOf<Long, FocusRequester>() }
    var focusTarget by remember { mutableStateOf<Long?>(null) }
    var focusedId by remember { mutableStateOf<Long?>(null) }
    val style = MaterialTheme.typography.bodyLarge

    Column {
        items.forEach { item ->
            key(item.id) {
                val requester = requesters.getOrPut(item.id) { FocusRequester() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggle(item.id) }) {
                        Icon(
                            if (item.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = if (item.checked) "Mark as not done" else "Mark as done",
                            tint = ink.copy(alpha = if (item.checked) 0.5f else 0.85f),
                        )
                    }
                    BasicTextField(
                        value = item.text,
                        onValueChange = { onText(item.id, it) },
                        singleLine = true,
                        textStyle = style.copy(
                            color = ink.copy(alpha = if (item.checked) 0.5f else 1f),
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        ),
                        cursorBrush = SolidColor(ink),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                        // "Next" on the keyboard adds a new item below, like a paper list.
                        keyboardActions = KeyboardActions(onNext = { focusTarget = onAddAfter(item.id) }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(requester)
                            .onFocusChanged { if (it.isFocused) focusedId = item.id },
                        decorationBox = { inner -> Placeholder(item.text.isEmpty(), "List item", style, ink) { inner() } },
                    )
                    // Remove button only on the row being edited, to keep the list calm.
                    if (focusedId == item.id) {
                        IconButton(onClick = {
                            val index = items.indexOfFirst { it.id == item.id }
                            onRemove(item.id)
                            focusTarget = items.getOrNull(index - 1)?.id
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove item",
                                tint = ink.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { focusTarget = onAddAfter(null) }
                .padding(vertical = 4.dp),
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = ink.copy(alpha = 0.7f))
            }
            Text("Add item", style = style, color = ink.copy(alpha = 0.7f))
        }
    }

    LaunchedEffect(focusTarget) {
        val id = focusTarget ?: return@LaunchedEffect
        runCatching { requesters[id]?.requestFocus() }
        focusTarget = null
    }
}

// --- Formatting bar --------------------------------------------------------------------------

@Composable
private fun FormatBar(vm: NoteEditorViewModel, ink: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", false, ink, vm.canUndo, vm::undo)
        ToolButton(Icons.AutoMirrored.Filled.Redo, "Redo", false, ink, vm.canRedo, vm::redo)
        BarDivider(ink)
        ToolButton(Icons.Filled.FormatBold, "Bold", false, ink) { vm.toggleInline(Markup.InlineKind.Bold) }
        ToolButton(Icons.Filled.FormatItalic, "Italic", false, ink) { vm.toggleInline(Markup.InlineKind.Italic) }
        ToolButton(Icons.Filled.FormatUnderlined, "Underline", false, ink) { vm.toggleInline(Markup.InlineKind.Underline) }
        ToolButton(Icons.Filled.StrikethroughS, "Strikethrough", false, ink) { vm.toggleInline(Markup.InlineKind.Strike) }
        ToolButton(Icons.Filled.Highlight, "Highlight", false, ink) { vm.toggleInline(Markup.InlineKind.Highlight) }
        ToolButton(Icons.Filled.Code, "Code", false, ink) { vm.toggleInline(Markup.InlineKind.Code) }
        BarDivider(ink)
        ToolButton(Icons.Filled.Title, "Heading", false, ink) { vm.toggleLine(Markup.LineKind.Heading1) }
        ToolButton(Icons.Filled.FormatListBulleted, "Bulleted list", false, ink) { vm.toggleLine(Markup.LineKind.Bullet) }
        ToolButton(Icons.Filled.FormatListNumbered, "Numbered list", false, ink) { vm.toggleLine(Markup.LineKind.Numbered) }
        ToolButton(Icons.Filled.FormatQuote, "Quote", false, ink) { vm.toggleLine(Markup.LineKind.Quote) }
        BarDivider(ink)
        ToolButton(Icons.Filled.FormatClear, "Clear formatting", false, ink, onClick = vm::clearFormatting)
    }
}

@Composable
private fun BarDivider(ink: Color) {
    VerticalDivider(
        color = ink.copy(alpha = 0.12f),
        modifier = Modifier
            .height(24.dp)
            .padding(horizontal = 4.dp),
    )
}

// --- Small pieces ----------------------------------------------------------------------------

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    ink: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) ink.copy(alpha = 0.1f) else Color.Transparent,
            contentColor = ink,
            disabledContentColor = ink.copy(alpha = 0.3f),
        ),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String, ink: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = ink.copy(alpha = 0.07f),
        contentColor = ink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Footer(editedAt: Long?, words: Int, ink: Color) {
    val parts = buildList {
        editedAt?.let {
            add("Edited " + DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
        }
        add(if (words == 1) "1 word" else "$words words")
    }
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = ink.copy(alpha = 0.7f),
    )
}

/** Row of small colour dots. Each dot sits in a 44dp touch target. */
@Composable
internal fun ColorPicker(selected: Int, onSelect: (Int) -> Unit, ink: Color) {
    Row(
        modifier = Modifier
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
    val chooser = Intent.createChooser(send, null).putExtra(
        Intent.EXTRA_EXCLUDE_COMPONENTS,
        // Don't offer "Sticky Notes" itself; sharing a note into the app would just duplicate it.
        arrayOf(ComponentName(context, MainActivity::class.java)),
    )
    context.startActivity(chooser)
}
