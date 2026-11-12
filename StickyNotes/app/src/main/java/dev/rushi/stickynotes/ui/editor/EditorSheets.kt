package dev.rushi.stickynotes.ui.editor

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.rushi.stickynotes.ui.common.reminderLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class SheetOption(val label: String, val detail: String? = null, val onClick: () -> Unit)

/** A plain list of choices in a bottom sheet. Tapping one runs it and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(title: String, options: List<SheetOption>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            options.forEach { option ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            option.onClick()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    option.detail?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderSheet(
    current: Long?,
    onPick: (Long) -> Unit,
    onPickCustom: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val now = ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    val presets = buildList {
        add("In 1 hour" to now.plusHours(1))
        val evening = now.with(LocalTime.of(18, 0))
        if (now.isBefore(evening.minusHours(1))) add("This evening" to evening)
        add("Tomorrow morning" to now.plusDays(1).with(LocalTime.of(9, 0)))
        add("Next week" to now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(LocalTime.of(9, 0)))
    }
    val options = presets.map { (label, time) ->
        val at = time.toInstant().toEpochMilli()
        SheetOption(label, reminderLabel(context, at)) { onPick(at) }
    } + SheetOption("Pick date & time…", onClick = onPickCustom) +
        listOfNotNull(current?.let { SheetOption("Remove reminder", reminderLabel(context, it), onRemove) })

    OptionsSheet(title = "Remind me", options = options, onDismiss = onDismiss)
}

@Composable
fun VanishSheet(current: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val now = System.currentTimeMillis()
    val hour = 60 * 60 * 1000L
    val options = listOf(
        SheetOption("In 24 hours", "Good for codes, parking spots, today's to-dos") { onPick(now + 24 * hour) },
        SheetOption("In 3 days") { onPick(now + 72 * hour) },
        SheetOption("In 7 days") { onPick(now + 7 * 24 * hour) },
    ) + listOfNotNull(current?.let { SheetOption("Keep forever", "Stop this note from vanishing") { onPick(null) } })

    OptionsSheet(title = "Vanish this note", options = options, onDismiss = onDismiss)
}

/** Date picker, then time picker. Past days can't be chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pickingTime by remember { mutableStateOf(false) }
    val todayUtc = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = todayUtc,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
        },
    )
    // Start one hour ahead, so on a 12-hour clock AM/PM is already the sensible one.
    val nextHour = remember { LocalTime.now().plusHours(1).hour }
    val timeState = rememberTimePickerState(
        initialHour = nextHour,
        initialMinute = 0,
        is24Hour = DateFormat.is24HourFormat(context),
    )

    if (!pickingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickingTime = true }, enabled = dateState.selectedDateMillis != null) {
                    Text("Next")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        // The date picker reports UTC midnight of the chosen day.
        val date = Instant.ofEpochMilli(dateState.selectedDateMillis ?: todayUtc)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val at = date.atTime(timeState.hour, timeState.minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        // A time that has already passed would make Android fire the reminder at once.
        val inPast = at <= System.currentTimeMillis()
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { onPicked(at) }, enabled = !inPast) { Text("Set reminder") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            text = {
                Column {
                    TimePicker(state = timeState)
                    if (inPast) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "That time has already passed today. Check AM/PM or pick a later time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
    }
}
