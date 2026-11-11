package dev.rushi.stickynotes.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional app lock. The on/off setting is persisted; the locked/unlocked state lives in
 * memory only, so a fresh process always starts locked when the lock is enabled.
 *
 * This is a privacy lock against someone picking up an unlocked phone. It is not
 * cryptographically bound to the notes, so it doesn't stop an attacker with root and
 * instrumentation tools. See README, "App lock: what it protects".
 */
class AppLock(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _locked = MutableStateFlow(_enabled.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

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

    fun onAppBackgrounded() {
        if (_enabled.value && !_locked.value) backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        // If the user removed their screen lock, auth can't succeed; fail open to avoid a lockout.
        if (_enabled.value && !isDeviceSecure()) {
            setEnabled(false)
            return
        }
        if (_enabled.value && SystemClock.elapsedRealtime() - since >= GRACE_PERIOD_MS) {
            _locked.value = true
        }
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_ENABLED = "app_lock_enabled"
        /** Short trips out of the app (share sheet, quick switch) don't re-lock. */
        const val GRACE_PERIOD_MS = 30_000L
    }
}
