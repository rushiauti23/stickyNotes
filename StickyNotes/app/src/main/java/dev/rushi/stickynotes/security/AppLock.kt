package dev.rushi.stickynotes.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import dev.rushi.stickynotes.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional app lock. The on/off setting is persisted; the locked/unlocked state lives in
 * memory only, so a fresh process always starts locked when the lock is enabled.
 *
 * Re-locks when the app comes back after being in the background for at least
 * [AppSettings.relockAfterMs] ("Immediately" = every time it leaves the screen, including
 * when the phone's screen turns off).
 *
 * This is a privacy lock against someone picking up an unlocked phone. It is not
 * cryptographically bound to the notes, so it doesn't stop an attacker with root and
 * instrumentation tools. See README, "App lock: what it protects".
 */
class AppLock(context: Context, private val settings: AppSettings) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _locked = MutableStateFlow(_enabled.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var externalActivitySince: Long? = null

    /** True when the phone has a PIN, pattern, password or biometric, which the lock needs. */
    fun isDeviceSecure(): Boolean =
        appContext.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
        if (!value) _locked.value = false
    }

    fun unlock() {
        _locked.value = false
    }

    fun lockNow() {
        if (_enabled.value) _locked.value = true
    }

    /**
     * Call right before the app itself opens another screen (file picker, share sheet, system
     * settings, fingerprint/PIN prompt), so coming straight back from it doesn't re-lock.
     */
    fun expectExternalActivity() {
        externalActivitySince = SystemClock.elapsedRealtime()
    }

    fun onAppBackgrounded() {
        if (_enabled.value && !_locked.value) backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val now = SystemClock.elapsedRealtime()

        val external = externalActivitySince
        externalActivitySince = null
        if (external != null && now - external < EXTERNAL_ACTIVITY_GRACE_MS) return

        // If the user removed their screen lock, auth can't succeed; fail open to avoid a lockout.
        if (_enabled.value && !isDeviceSecure()) {
            setEnabled(false)
            return
        }
        if (_enabled.value && now - since >= settings.relockAfterMs.value) {
            _locked.value = true
        }
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_ENABLED = "app_lock_enabled"
        /** A trip to a picker/settings screen we opened ourselves doesn't re-lock, up to this long. */
        private const val EXTERNAL_ACTIVITY_GRACE_MS = 5 * 60_000L
    }
}
