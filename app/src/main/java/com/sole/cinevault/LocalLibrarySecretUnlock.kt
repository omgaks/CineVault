package com.sole.cinevault

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal fun requestSecretFolderUnlock(
    context: Context,
    onUnlocked: () -> Unit,
    onAuthenticationError: () -> Unit
) {
    val activity = context.findCineActivity() as? FragmentActivity
    if (activity == null) {
        Toast.makeText(
            context,
            "Couldn't open Secret Folder unlock",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    if (
        BiometricManager.from(context)
            .canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS
    ) {
        Toast.makeText(
            context,
            "Set a device lock (fingerprint, PIN, pattern, or password) first to secure this folder",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Secret Folder")
        .setSubtitle("Confirm fingerprint, PIN, pattern, or password")
        .setAllowedAuthenticators(authenticators)
        .build()

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                onUnlocked()
                Toast.makeText(
                    context,
                    "Secret folder unlocked",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence
            ) {
                onAuthenticationError()
            }

            override fun onAuthenticationFailed() {
                // Keep the prompt open for another attempt.
            }
        }
    )

    prompt.authenticate(promptInfo)
}
