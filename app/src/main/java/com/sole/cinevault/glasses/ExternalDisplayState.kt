package com.sole.cinevault.glasses

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * D8-1 — canonical external-display discovery.
 *
 * D7 removed the legacy external presentation. D8 begins by making display
 * discovery a clean standalone capability with no Player, PlayerView, or
 * presentation compatibility API attached to it.
 */
data class ExternalDisplayInfo(
    val isConnected: Boolean,
    val displayId: Int?,
    val displayName: String?,
)

@Composable
fun rememberExternalDisplayState(): State<ExternalDisplayInfo> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(currentExternalDisplay(context)) }

    DisposableEffect(context) {
        val manager =
            context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                state.value = currentExternalDisplay(context)
            }

            override fun onDisplayRemoved(displayId: Int) {
                state.value = currentExternalDisplay(context)
            }

            override fun onDisplayChanged(displayId: Int) {
                state.value = currentExternalDisplay(context)
            }
        }

        manager.registerDisplayListener(listener, null)

        onDispose {
            manager.unregisterDisplayListener(listener)
        }
    }

    return state
}

private fun currentExternalDisplay(context: Context): ExternalDisplayInfo {
    val manager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    val display = manager
        .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.isValid
        }

    return ExternalDisplayInfo(
        isConnected = display != null,
        displayId = display?.displayId,
        displayName = display?.name,
    )
}
