package com.sole.cinevault.glasses.display

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.sole.cinevault.glasses.ExternalDisplayInfo

/**
 * D7-3 — live owner for the shared external CineVault renderer.
 *
 * This activates the existing unified renderer/lifecycle stack. It creates no
 * second player, subtitle tree, FFmpeg path, studio, navigation stack, or
 * glasses-specific feature UI.
 */
@Composable
fun rememberSharedCineVaultExternalRuntime(
    activity: Activity?,
    externalDisplay: ExternalDisplayInfo,
    enabled: Boolean,
): SharedCineVaultExternalRuntimeState {
    if (activity == null) {
        return SharedCineVaultExternalRuntimeState(
            active = false,
            displayId = null,
        )
    }

    val renderer = remember(activity) {
        CineVaultExternalComposeRenderer(activity)
    }
    val runtime = remember(renderer) {
        UnifiedGlassesDisplayRuntime(renderer)
    }

    val modeState =
        externalDisplay.toGlassesDisplayModeState(
            enabled = enabled,
        )

    runtime.apply(modeState)

    DisposableEffect(runtime) {
        onDispose {
            runtime.release()
        }
    }

    val snapshot = runtime.snapshot

    return SharedCineVaultExternalRuntimeState(
        active = snapshot.externalCineVaultActive,
        displayId = snapshot.externalDisplayId,
    )
}

data class SharedCineVaultExternalRuntimeState(
    val active: Boolean,
    val displayId: Int?,
)
