package dev.rushi.stickynotes

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dev.rushi.stickynotes.security.AppLock
import dev.rushi.stickynotes.security.Authenticator
import dev.rushi.stickynotes.ui.editor.NoteEditorScreen
import dev.rushi.stickynotes.ui.editor.NoteEditorViewModel
import dev.rushi.stickynotes.ui.list.NoteListScreen
import dev.rushi.stickynotes.ui.lock.LockScreen
import dev.rushi.stickynotes.ui.settings.SettingsScreen
import dev.rushi.stickynotes.ui.theme.StickyNotesTheme

val LocalAuthenticator = staticCompositionLocalOf<Authenticator> { error("No Authenticator") }
val LocalAppLock = staticCompositionLocalOf<AppLock> { error("No AppLock") }

// FragmentActivity (a ComponentActivity) is required by BiometricPrompt.
class MainActivity : FragmentActivity() {

    private val appLock by lazy { (application as StickyNotesApp).appLock }
    private lateinit var authenticator: Authenticator

    /** Note to open, e.g. from a reminder notification. Consumed once the notes UI shows it. */
    private val openNoteRequest = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        authenticator = Authenticator(this)
        applySecureFlag(appLock.enabled.value)
        if (savedInstanceState == null) handleOpenNoteIntent(intent)

        setContent {
            StickyNotesTheme {
                val lockEnabled by appLock.enabled.collectAsStateWithLifecycle()
                val locked by appLock.locked.collectAsStateWithLifecycle()

                // With the lock on, hide notes from screenshots and the recent-apps preview.
                LaunchedEffect(lockEnabled) { applySecureFlag(lockEnabled) }

                CompositionLocalProvider(
                    LocalAuthenticator provides authenticator,
                    LocalAppLock provides appLock,
                ) {
                    // While locked, the notes UI isn't composed at all, so nothing leaks to
                    // screen readers or accessibility services behind the lock screen.
                    Crossfade(targetState = locked, animationSpec = tween(180), label = "lock") { isLocked ->
                        if (isLocked) {
                            LockScreen(onUnlock = {
                                authenticator.authenticate(title = "Unlock Sticky Notes") { appLock.unlock() }
                            })
                        } else {
                            AppNavHost(
                                openNoteRequest = openNoteRequest,
                                onOpenNoteHandled = { openNoteRequest.value = null },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenNoteIntent(intent)
    }

    // The activity is exported (launcher), so treat the extra as untrusted: it can only ever
    // select one of the user's own notes by id, and the app lock still applies on top.
    private fun handleOpenNoteIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L) ?: -1L
        intent?.removeExtra(EXTRA_OPEN_NOTE_ID)
        if (id > 0L) openNoteRequest.value = id
    }

    override fun onStart() {
        super.onStart()
        appLock.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        appLock.onAppBackgrounded()
    }

    private fun applySecureFlag(secure: Boolean) {
        if (secure) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
    }
}

@Composable
private fun AppNavHost(openNoteRequest: StateFlow<Long?>, onOpenNoteHandled: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "notes") {
        composable("notes") {
            NoteListScreen(
                onOpenNote = { id -> nav.navigate("note/$id") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable(
            route = "note/{${NoteEditorViewModel.ARG_NOTE_ID}}",
            arguments = listOf(navArgument(NoteEditorViewModel.ARG_NOTE_ID) { type = NavType.LongType }),
        ) {
            // navigateUp() is a no-op on the start screen, so a double tap can't blank the app.
            NoteEditorScreen(onBack = { nav.navigateUp() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.navigateUp() })
        }
    }

    // After NavHost so the graph is set before we navigate.
    val requestedNote by openNoteRequest.collectAsStateWithLifecycle()
    LaunchedEffect(requestedNote) {
        val id = requestedNote ?: return@LaunchedEffect
        nav.navigate("note/$id") { launchSingleTop = true }
        onOpenNoteHandled()
    }
}
