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
import com.sole.cinevault.glasses.ExternalDisplayLifecyclePolicy
import com.sole.cinevault.glasses.ExternalDisplayLifecycleState
import com.sole.cinevault.glasses.ExternalPresentationHandle
import com.sole.cinevault.glasses.rememberExternalDisplayState
import com.sole.cinevault.glasses.rememberExternalVideoPresentation
import kotlinx.coroutines.delay

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
        if (lifecycle.isSessionEnabled) presentation?.playerView else null

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
                PlayerView.switchTargetView(player, null, localView)
                onBoundPlayerViewChanged(localView)
            }

            externalView == null && localView != null -> {
                onBoundPlayerViewChanged(localView)
            }
        }
    }

    return PlayerGlassesMode(
        display = externalDisplay,
        presentation = presentation,
        showConnectedHint = showConnectedHint,
        externalPlayerView = externalPlayerView,
        disableSession = {
            lifecycle =
                ExternalDisplayLifecyclePolicy.disableCurrentSession(lifecycle)
        },
    )
}
