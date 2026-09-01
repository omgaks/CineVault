package com.sole.cinevault

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
internal fun LocalLibrarySecretScreenProtection(
    context: Context,
    enabled: Boolean
) {
    val activity = context.findCineActivity()

    DisposableEffect(enabled, activity) {
        if (enabled) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        onDispose {
            if (enabled) {
                activity?.window?.clearFlags(
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
        }
    }
}
