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
import androidx.media3.common.Player

/**
 * D7-6 fix — external-display discovery separated from the deleted legacy
 * ExternalDisplayPresentation implementation.
 *
 * ExternalDisplayInfo and rememberExternalDisplayState are still shared
 * infrastructure used by MainActivity, PlayerGlassesMode, the display-mode
 * bridge, and the shared external runtime. They were previously declared
 * inside the legacy presentation file and therefore must survive its removal.
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

/**
 * Compatibility symbol for one stale import in PlayerVisualComponents.
 *
 * The legacy presentation itself is intentionally gone. Code search shows
 * this symbol is imported but not invoked. Keeping this no-op declaration
 * lets D7-6 remove the legacy renderer without forcing an unrelated rewrite
 * of the very large PlayerVisualComponents file in the same slice.
 *
 * Remove this compatibility symbol when that stale import is cleaned up.
 */
@Deprecated(
    message = "Legacy external presentation removed; use the shared CineVault external renderer.",
)
@Composable
fun rememberExternalVideoPresentation(
    player: Player,
    externalDisplay: ExternalDisplayInfo,
    title: String,
    ratingText: String?,
    onBack: () -> Unit,
    cinemaVoidEnabled: Boolean = false,
    initialSubtitleContentLocked: Boolean = false,
): State<Nothing?> {
    @Suppress("UNUSED_VARIABLE")
    val compatibilityInputs = arrayOf(
        player,
        externalDisplay,
        title,
        ratingText,
        onBack,
        cinemaVoidEnabled,
        initialSubtitleContentLocked,
    )
    return remember { mutableStateOf(null) }
}
