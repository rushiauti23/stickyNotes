package dev.rushi.stickynotes

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        authenticator = Authenticator(this)
        applySecureFlag(appLock.enabled.value)

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
                            AppNavHost()
                        }
                    }
                }
            }
        }
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
}

@Composable
private fun AppNavHost() {
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
}
