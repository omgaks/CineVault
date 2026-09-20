package com.sole.cinevault

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.sole.cinevault.glasses.ExternalDisplayInfo
import com.sole.cinevault.glasses.ExternalPresentationHandle
import com.sole.cinevault.glasses.rememberExternalDisplayState
import com.sole.cinevault.glasses.rememberExternalVideoPresentation
import kotlinx.coroutines.delay

/**
 * External-display state used by VideoPlayerScreen.
 *
 * D3-1:
 * - no vendor-specific behavior
 * - no forced device orientation
 * - host window remains free to rotate, resize, fold, split-screen or run
 *   freeform while the external CineVault surface is active
 * - brightness dimming keeps the host usable as the Cinema Void controller
 *
 * Layout decisions belong to the current available window, never to a device
 * name, physical model or assumed resolution.
 */
@Stable
@UnstableApi
class PlayerGlassesMode(
    val display: ExternalDisplayInfo,
    val presentation: ExternalPresentationHandle?,
    val showConnectedHint: Boolean,
    val externalPlayerView: PlayerView?,
    val disableSession: () -> Unit,
) {
    val isConnected: Boolean get() = display.isConnected
    val isActive: Boolean get() = externalPlayerView != null
}

@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerGlassesMode(
    player: Player,
    title: String,
    ratingText: String?,
    cinemaVoidEnabled: Boolean = false,
    initialSubtitleContentLocked: Boolean = false,
    onBack: () -> Unit,
    localPlayerView: PlayerView?,
    onBoundPlayerViewChanged: (PlayerView?) -> Unit,
): PlayerGlassesMode {
    val context = LocalContext.current
    val activity = context.findCineActivity()

    val externalDisplay by rememberExternalDisplayState()
    var showConnectedHint by remember { mutableStateOf(false) }
    var sessionDisabled by remember(externalDisplay.displayId) {
        mutableStateOf(false)
    }

    // Never force orientation for an external display. Cinema Void must adapt
    // to the host window it actually receives at runtime.
    LaunchedEffect(externalDisplay.isConnected) {
        if (externalDisplay.isConnected) {
            activity?.window?.attributes =
                activity?.window?.attributes?.apply {
                    screenBrightness = playerGlassesConnectedBrightness()
                }
            showConnectedHint = true
            delay(playerGlassesConnectedHintDurationMs())
            showConnectedHint = false
        } else {
            activity?.window?.attributes =
                activity?.window?.attributes?.apply {
                    screenBrightness = playerDefaultWindowBrightness()
                }
        }
    }

    val presentation by rememberExternalVideoPresentation(
        player = player,
        externalDisplay = externalDisplay,
        title = title,
        ratingText = ratingText,
        cinemaVoidEnabled = cinemaVoidEnabled,
        initialSubtitleContentLocked = initialSubtitleContentLocked,
        onBack = onBack,
    )

    val externalPlayerView =
        if (sessionDisabled) null else presentation?.playerView

    LaunchedEffect(externalPlayerView, localPlayerView) {
        val localView = localPlayerView
        val externalView = externalPlayerView
        when {
            externalView != null && externalView.player !== player -> {
                PlayerView.switchTargetView(player, localView, externalView)
                onBoundPlayerViewChanged(externalView)
            }
            externalView == null &&
                localView != null &&
                localView.player !== player -> {
                localView.player = player
                onBoundPlayerViewChanged(localView)
            }
        }
    }

    return PlayerGlassesMode(
        display = externalDisplay,
        presentation = presentation,
        showConnectedHint = showConnectedHint,
        externalPlayerView = externalPlayerView,
        disableSession = { sessionDisabled = true },
    )
}
