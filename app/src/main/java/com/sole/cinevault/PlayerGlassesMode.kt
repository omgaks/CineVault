package com.sole.cinevault

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sole.cinevault.glasses.ExternalDisplayInfo
import com.sole.cinevault.glasses.ExternalDisplayLifecyclePolicy
import com.sole.cinevault.glasses.ExternalDisplayLifecycleState
import com.sole.cinevault.glasses.rememberExternalDisplayState
import com.sole.cinevault.glasses.audio.CineAudioRouteController
import com.sole.cinevault.glasses.display.rememberSharedCineVaultExternalRuntime
import kotlinx.coroutines.delay

@Stable
@UnstableApi
class PlayerGlassesMode(
    val display: ExternalDisplayInfo,
    val showConnectedHint: Boolean,
    val sharedExternalActive: Boolean,
    val disableSession: () -> Unit,
) {
    val isConnected: Boolean get() = display.isConnected
    val isActive: Boolean get() = sharedExternalActive
}

/**
 * D13-S3 — canonical audio routing follows the shared glasses lifecycle.
 *
 * The existing host PlayerView already points at CineVault's one ExoPlayer.
 * We deliberately obtain that same player here instead of creating or passing
 * a second playback object.
 *
 * active external session -> apply glasses routing policy
 * inactive/disconnected/disposed -> return routing ownership to Android
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerGlassesMode(
    localPlayerView: PlayerView?,
    onBoundPlayerViewChanged: (PlayerView?) -> Unit,
): PlayerGlassesMode {
    val context = LocalContext.current
    val activity = context.findCineActivity()
    val externalDisplay by rememberExternalDisplayState()

    var showConnectedHint by remember { mutableStateOf(false) }
    var lifecycle by remember {
        mutableStateOf(
            ExternalDisplayLifecycleState(
                connectedDisplayId = externalDisplay.displayId,
            )
        )
    }

    LaunchedEffect(externalDisplay.displayId) {
        lifecycle =
            ExternalDisplayLifecyclePolicy.onDisplayChanged(
                previous = lifecycle,
                newDisplayId = externalDisplay.displayId,
            )
    }

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
            showConnectedHint = false
            activity?.window?.attributes =
                activity?.window?.attributes?.apply {
                    screenBrightness = playerDefaultWindowBrightness()
                }
        }
    }

    val sharedExternal =
        rememberSharedCineVaultExternalRuntime(
            activity = activity,
            externalDisplay = externalDisplay,
            enabled = lifecycle.isSessionEnabled,
        )

    /*
     * D7-4 deliberately keeps the SAME player target/session alive.
     * The external renderer enters the canonical CineVault session instead of
     * stealing the Player into a second glasses-only PlayerView.
     */
    LaunchedEffect(localPlayerView) {
        localPlayerView?.let(onBoundPlayerViewChanged)
    }

    // D13-S3: route the exact canonical ExoPlayer already bound to PlayerView.
    val canonicalPlayer = localPlayerView?.player as? ExoPlayer
    val audioRouteController =
        remember(context, canonicalPlayer) {
            canonicalPlayer?.let { CineAudioRouteController(context, it) }
        }

    LaunchedEffect(audioRouteController, sharedExternal.active) {
        audioRouteController?.apply(
            glassesSessionActive = sharedExternal.active,
        )
    }

    DisposableEffect(audioRouteController) {
        onDispose {
            audioRouteController?.releaseToSystem()
        }
    }

    return PlayerGlassesMode(
        display = externalDisplay,
        showConnectedHint = showConnectedHint,
        sharedExternalActive = sharedExternal.active,
        disableSession = {
            lifecycle =
                ExternalDisplayLifecyclePolicy.disableCurrentSession(lifecycle)
        },
    )
}
