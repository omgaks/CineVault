package com.sole.cinevault

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * ExternalDisplayHelper.kt
 *
 * Detects an external display connected via USB-C DisplayPort Alt Mode
 * (RayNeo Air 4 Pro and similar AR/XR glasses, or any USB-C/HDMI monitor)
 * using Android's DisplayManager. This is PHASE 1 of "dedicated glasses
 * mode": detection + a clean landscape lock while connected, wired into
 * VideoPlayerScreen.kt.
 *
 * PHASE 2 (not built yet — needs the physical glasses to iterate on):
 * actually rendering video ON the external display via android.app.
 * Presentation while the tablet screen shows a minimal touch-remote
 * instead of the full player UI. Surface handoff between displays for
 * ExoPlayer needs real hardware testing to get right, not something safe
 * to hand over unverified.
 */

data class ExternalDisplayInfo(
    val isConnected: Boolean,
    val displayId: Int?,
    val displayName: String?
)

@Composable
fun rememberExternalDisplayState(): State<ExternalDisplayInfo> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(currentExternalDisplay(context)) }

    DisposableEffect(context) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
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
        displayManager.registerDisplayListener(listener, null)
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }

    return state
}

private fun currentExternalDisplay(context: Context): ExternalDisplayInfo {
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    // DEFAULT_DISPLAY (id 0) is always the device's own screen. Any other
    // PRESENTATION-capable display is treated as external — this is how a
    // USB-C DP Alt Mode monitor/glasses shows up to the app.
    val external = displayManager.displays.firstOrNull {
        it.displayId != Display.DEFAULT_DISPLAY &&
            (it.flags and Display.FLAG_PRESENTATION) != 0
    }
    return ExternalDisplayInfo(
        isConnected = external != null,
        displayId = external?.displayId,
        displayName = external?.name
    )
}
