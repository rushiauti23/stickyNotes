package dev.rushi.stickynotes.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rushi.stickynotes.LocalAppLock
import dev.rushi.stickynotes.LocalAuthenticator
import dev.rushi.stickynotes.StickyNotesApp
import dev.rushi.stickynotes.data.BackupCodec
import dev.rushi.stickynotes.data.BackupFormatException
import dev.rushi.stickynotes.reminders.Notifications
import dev.rushi.stickynotes.settings.AppSettings
import dev.rushi.stickynotes.ui.editor.ColorPicker
import dev.rushi.stickynotes.ui.editor.OptionsSheet
import dev.rushi.stickynotes.ui.editor.SheetOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as StickyNotesApp }
    val repository = app.repository
    val appLock = LocalAppLock.current
    val settings = app.settings
    val defaultColor by settings.defaultColorIndex.collectAsStateWithLifecycle()
    val relockAfter by settings.relockAfterMs.collectAsStateWithLifecycle()
    var showRelockSheet by remember { mutableStateOf(false) }
    var reminderMessage by remember { mutableStateOf<String?>(null) }

    // Re-read system permission states whenever we come back from the phone's settings.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notificationsOn = remember(refresh) {
        Notifications.canNotify(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exactOn = remember(refresh) { app.reminderScheduler.canScheduleExact() }
    val batteryUnrestricted = remember(refresh) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun openSystemSettings(intent: Intent) {
        appLock.expectExternalActivity()
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(fallback) } }
    }
    val authenticator = LocalAuthenticator.current
    val scope = rememberCoroutineScope()
    val enabled by appLock.enabled.collectAsStateWithLifecycle()
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toggleLock(target: Boolean) {
        if (target && !appLock.isDeviceSecure()) {
            lockMessage = "Set a screen lock (PIN, pattern or password) in your phone settings first."
            return
        }
        lockMessage = null
        // Changing the lock in either direction requires proving you own the device.
        authenticator.authenticate(title = if (target) "Turn on app lock" else "Turn off app lock") {
            appLock.setEnabled(target)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            backupMessage = try {
                val (json, count) = repository.exportJson()
                withContext(Dispatchers.IO) { writeText(context, uri, json) }
                "Exported $count notes."
            } catch (e: IOException) {
                "Couldn't save the file."
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            backupMessage = try {
                val text = withContext(Dispatchers.IO) { readTextLimited(context, uri, BackupCodec.MAX_BYTES) }
                val result = repository.importJson(text)
                buildString {
                    append(if (result.added == 1) "Imported 1 note" else "Imported ${result.added} notes")
                    if (result.skipped > 0) append(", skipped ${result.skipped} already here")
                    append(".")
                }
            } catch (e: BackupFormatException) {
                "That file isn't a Sticky Notes backup (${e.message})."
            } catch (e: IOException) {
                "Couldn't read the file."
            } finally {
                busy = false
            }
        }
    }

    fun export() {
        val start = {
            appLock.expectExternalActivity()
            exportLauncher.launch("sticky-notes-backup-${LocalDate.now()}.json")
        }
        // The export file isn't encrypted, so with the app lock on, prove it's you first.
        if (enabled) authenticator.authenticate(title = "Export notes") { start() } else start()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Privacy")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = ::toggleLock)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("App lock", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Ask for fingerprint, face or screen lock when opening the app. " +
                            "Also hides notes from screenshots, recent apps, the widget and notifications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                // The whole row is the toggle; the Switch is visual only.
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            lockMessage?.let { Message(it, error = true) }
            ActionRow(
                title = "Lock again",
                detail = if (enabled) {
                    AppSettings.relockLabel(relockAfter) + " after leaving the app or turning off the screen"
                } else {
                    "Turn on app lock to choose"
                },
                enabled = enabled,
                onClick = { showRelockSheet = true },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionTitle("Notes")
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                Text("New note colour", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Colour every new note starts with. You can still change it per note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ColorPicker(
                selected = defaultColor,
                onSelect = settings::setDefaultColorIndex,
                ink = MaterialTheme.colorScheme.onSurface,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionTitle("Reminders")
            StatusRow(
                title = "Notifications",
                ok = notificationsOn,
                okText = "Allowed",
                problemText = "Off. Tap to allow, or reminders stay silent",
                onClick = {
                    openSystemSettings(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                StatusRow(
                    title = "On-time reminders",
                    ok = exactOn,
                    okText = "Allowed: reminders arrive at the exact minute",
                    problemText = "Not allowed: reminders can be a few minutes late. Tap to allow \"Alarms & reminders\"",
                    onClick = {
                        openSystemSettings(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    },
                )
            }
            StatusRow(
                title = "Battery",
                ok = batteryUnrestricted,
                okText = "Unrestricted",
                problemText = "Optimised: after a restart some phones block reminders. Tap → Battery → Unrestricted",
                onClick = {
                    openSystemSettings(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
            Text(
                "On Xiaomi, Oppo, Vivo and some other phones, also turn on Autostart for Sticky Notes " +
                    "in the phone's settings so reminders survive a restart.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            ActionRow(
                title = "Send a test notification",
                detail = "Checks that reminders can reach you right now.",
                enabled = true,
                onClick = {
                    reminderMessage = if (Notifications.showTest(context)) null else "Notifications are off for this app."
                },
            )
            reminderMessage?.let { Message(it, error = true) }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionTitle("Backup")
            ActionRow(
                title = "Export notes",
                detail = "Saves all notes (except Trash) to a .json file. The file isn't encrypted, so keep it somewhere private.",
                enabled = !busy,
                onClick = ::export,
            )
            ActionRow(
                title = "Import notes",
                detail = "Adds notes from a Sticky Notes backup. Notes you already have are skipped.",
                enabled = !busy,
                onClick = {
                    appLock.expectExternalActivity()
                    // Some file managers label .json files as plain text or generic data.
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
            )
            backupMessage?.let { Message(it, error = false) }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Notes are stored only on this phone. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    if (showRelockSheet) {
        OptionsSheet(
            title = "Lock again",
            options = AppSettings.RELOCK_CHOICES.map { ms ->
                SheetOption(
                    label = AppSettings.relockLabel(ms),
                    detail = if (ms == relockAfter) "Current" else null,
                ) { settings.setRelockAfterMs(ms) }
            },
            onDismiss = { showRelockSheet = false },
        )
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, okText: String, problemText: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        Text(
            if (ok) okText else problemText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ActionRow(title: String, detail: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Message(text: String, error: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
}

private fun writeText(context: Context, uri: Uri, text: String) {
    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
    out.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
}

/** Reads at most [maxBytes]; a bigger file is rejected before it can exhaust memory. */
private fun readTextLimited(context: Context, uri: Uri, maxBytes: Int): String {
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream")
    input.use { stream ->
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupFormatException("file is larger than 10 MB")
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }
}
