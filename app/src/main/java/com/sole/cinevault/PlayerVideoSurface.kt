package com.sole.cinevault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.sole.cinevault.glasses.CinemaVoidControllerSurface

/**
 * Player surface shared by local playback and external-display playback.
 *
 * D3-2:
 * When an external display owns playback, the host PlayerView remains alive
 * only as the hand-back target while the visible host surface becomes Cinema
 * Void. Cinema Void is presentation-only here; all touch ownership remains in
 * PlayerPlaybackGestureLayer so Halo can operate across 100% of the window.
 */
@Composable
internal fun PlayerVideoSurface(
    player: Player,
    externalDisplayActive: Boolean,
    isZoomMode: Boolean,
    videoScale: Float,
    videoOffsetX: Float,
    videoOffsetY: Float,
    onPlayerViewChanged: (PlayerView) -> Unit,
    onResizeModeChanged: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = if (externalDisplayActive) 1f else videoScale,
                    scaleY = if (externalDisplayActive) 1f else videoScale,
                    translationX = if (externalDisplayActive) 0f else videoOffsetX,
                    translationY = if (externalDisplayActive) 0f else videoOffsetY,
                    alpha = if (externalDisplayActive) 0f else 1f,
                ),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = if (externalDisplayActive) null else player
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = if (isZoomMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    subtitleView?.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
                    onPlayerViewChanged(this)
                }
            },
            update = { playerView ->
                if (!externalDisplayActive && playerView.player !== player) {
                    playerView.player = player
                } else if (externalDisplayActive && playerView.player != null) {
                    playerView.player = null
                }

                playerView.resizeMode = if (isZoomMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }

                onResizeModeChanged(playerView.resizeMode)
                onPlayerViewChanged(playerView)
            },
        )

        if (externalDisplayActive) {
            CinemaVoidControllerSurface(
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
