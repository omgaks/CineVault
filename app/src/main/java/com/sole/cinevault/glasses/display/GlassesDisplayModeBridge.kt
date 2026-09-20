package com.sole.cinevault.glasses.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.sole.cinevault.glasses.ExternalDisplayInfo

/**
 * D1-2 bridge from CineVault's already-working external-display detector into
 * the new one-CineVault Glasses Display Mode contract.
 *
 * Important: this does not create, replace, or dismiss any Presentation.
 * The current glasses implementation remains untouched while the new display
 * architecture is introduced behind it in small, green-gated slices.
 */
fun ExternalDisplayInfo.toGlassesDisplayModeState(
    enabled: Boolean = true,
): GlassesDisplayModeState = resolveGlassesDisplayMode(
    connected = isConnected,
    displayId = displayId,
    displayName = displayName,
    enabled = enabled,
)

/**
 * Compose adapter for call sites that already own State<ExternalDisplayInfo>.
 *
 * The returned State is derived from the existing detector, so there is still
 * exactly one source of truth for physical display connection information.
 */
@Composable
fun rememberGlassesDisplayModeState(
    externalDisplayState: State<ExternalDisplayInfo>,
    enabled: Boolean = true,
): State<GlassesDisplayModeState> = remember(externalDisplayState, enabled) {
    derivedStateOf {
        externalDisplayState.value.toGlassesDisplayModeState(enabled = enabled)
    }
}
