package dev.rushi.stickynotes.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wraps BiometricPrompt. It must be created in Activity.onCreate, so the callback is fixed and
 * the per-request success action is stored in [onSuccess].
 *
 * Allows fingerprint/face OR the phone's PIN/pattern/password, so a user whose biometrics
 * fail is never locked out of their own notes.
 */
class Authenticator(activity: FragmentActivity) {

    private var onSuccess: (() -> Unit)? = null

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val action = onSuccess
                onSuccess = null
                action?.invoke()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancelled, lockout, etc. Stay locked; the user can retry from the lock screen.
                onSuccess = null
            }
        },
    )

    fun authenticate(title: String, subtitle: String? = null, onSuccess: () -> Unit) {
        this.onSuccess = onSuccess
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            // BIOMETRIC_WEAK | DEVICE_CREDENTIAL is the combination supported on every API level
            // the app runs on (28+). No negative button is allowed with DEVICE_CREDENTIAL.
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}
