package com.sole.cinevault

import android.content.pm.ActivityInfo
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
 * Glasses / external-display state used by VideoPlayerScreen.
 *
 * Extracted from VideoPlayerScreen so orientation lock, brightness dimming,
 * Presentation creation, five-finger session disable, and PlayerView handoff
 * live in one place. Playback behavior is unchanged.
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
    onBack: () -> Unit,
    localPlayerView: PlayerView?,
    onBoundPlayerViewChanged: (PlayerView?) -> Unit,
): PlayerGlassesMode {
    val context = LocalContext.current
    val activity = context.findCineActivity()

    val externalDisplay by rememberExternalDisplayState()
    var showConnectedHint by remember { mutableStateOf(false) }
    var sessionDisabled by remember(externalDisplay.displayId) { mutableStateOf(false) }

    // Detects a USB-C DisplayPort Alt Mode external display (RayNeo glasses
    // or similar) and locks the player to landscape while it's connected —
    // these devices render a fixed-aspect virtual screen, so letting the
    // player sit in portrait while one's attached just produces an
    // unnecessarily letterboxed picture. Also auto-dims the tablet's own
    // brightness to near-zero while connected, while keeping the screen
    // genuinely ON and touchable so it still works as a remote/control
    // surface. Reverts automatically on disconnect or when leaving the player.
    LaunchedEffect(externalDisplay.isConnected) {
        if (externalDisplay.isConnected) {
            // setRequestedOrientation() throws IllegalStateException if the
            // Activity isn't in a plain fullscreen state at that moment
            // (split-screen, floating/free-form window, or a PiP
            // transition — all real states HyperOS's tablet multitasking
            // can put an app into). An orientation lock is a nice-to-have,
            // never something that should be allowed to crash the app.
            try {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } catch (_: Exception) {
            }
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = playerGlassesConnectedBrightness()
            }
            showConnectedHint = true
            delay(playerGlassesConnectedHintDurationMs())
            showConnectedHint = false
        } else {
            try {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            } catch (_: Exception) {
            }
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = playerDefaultWindowBrightness()
            }
        }
    }

    // Presentation creation is tied to the player + physical display ID, so
    // hot-unplug disposes only the external surface and the local PlayerView
    // immediately takes ownership of the same ExoPlayer again at the same
    // playback position.
    val presentation by rememberExternalVideoPresentation(
        player = player,
        externalDisplay = externalDisplay,
        title = title,
        ratingText = ratingText,
        onBack = onBack,
    )
    val externalPlayerView = if (sessionDisabled) null else presentation?.playerView

    LaunchedEffect(externalPlayerView, localPlayerView) {
        val localView = localPlayerView
        val externalView = externalPlayerView
        when {
            externalView != null && externalView.player !== player -> {
                PlayerView.switchTargetView(player, localView, externalView)
                onBoundPlayerViewChanged(externalView)
            }
            externalView == null && localView != null && localView.player !== player -> {
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
