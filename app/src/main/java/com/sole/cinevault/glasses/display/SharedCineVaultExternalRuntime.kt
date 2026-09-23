package com.sole.cinevault.glasses.display

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.sole.cinevault.glasses.ExternalDisplayInfo

/**
 * D7-3 — live owner for the shared external CineVault renderer.
 *
 * This activates the D1/D2 shared-renderer stack that already exists in the
 * repository. It does not create a player, subtitle tree, FFmpeg path, studio,
 * navigation stack, or glasses-specific feature UI.
 *
 * The only input is physical display state. The external window renders
 * CineVaultRoot() through CineVaultExternalComposeRenderer, and Halo remains
 * attached at HaloCanonicalCineVaultSurface inside that renderer.
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

    val decision = runtime.apply(modeState)

    DisposableEffect(runtime) {
        onDispose {
            runtime.release()
        }
    }

    return SharedCineVaultExternalRuntimeState(
        active =
            decision.command == GlassesDisplayCommand.SHOW_EXTERNAL_CINEVAULT ||
                runtime.snapshot.attachedDisplayId != null,
        displayId = runtime.snapshot.attachedDisplayId,
    )
}

data class SharedCineVaultExternalRuntimeState(
    val active: Boolean,
    val displayId: Int?,
)
