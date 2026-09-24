package com.sole.cinevault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import com.sole.cinevault.glasses.display.CineVaultRenderDestination
import com.sole.cinevault.glasses.display.LocalCineVaultRenderDestination
import com.sole.cinevault.glasses.display.ExternalViewportSessionState
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
    val renderDestination = LocalCineVaultRenderDestination.current
    val cinemaVoidHost =
        shouldUseCinemaVoidPlayerSurface(
            externalDisplayActive = externalDisplayActive,
            renderDestination = renderDestination,
        )
    val externalViewport = ExternalViewportSessionState.transform
    val surfaceScale =
        if (renderDestination == CineVaultRenderDestination.EXTERNAL_DISPLAY) {
            externalViewport.scale
        } else {
            videoScale
        }
    val surfaceOffsetX =
        if (renderDestination == CineVaultRenderDestination.EXTERNAL_DISPLAY) {
            externalViewport.panX
        } else {
            videoOffsetX
        }
    val surfaceOffsetY =
        if (renderDestination == CineVaultRenderDestination.EXTERNAL_DISPLAY) {
            externalViewport.panY
        } else {
            videoOffsetY
        }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = if (cinemaVoidHost) 1f else surfaceScale,
                    scaleY = if (cinemaVoidHost) 1f else surfaceScale,
                    translationX = if (cinemaVoidHost) 0f else surfaceOffsetX,
                    translationY = if (cinemaVoidHost) 0f else surfaceOffsetY,
                    alpha = if (cinemaVoidHost) 0f else 1f,
                ),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = if (cinemaVoidHost) null else player
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
                if (!cinemaVoidHost && playerView.player !== player) {
                    playerView.player = player
                } else if (cinemaVoidHost && playerView.player != null) {
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

        if (cinemaVoidHost) {
            CinemaVoidControllerSurface(
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}


internal fun shouldUseCinemaVoidPlayerSurface(
    externalDisplayActive: Boolean,
    renderDestination: CineVaultRenderDestination,
): Boolean =
    externalDisplayActive &&
        renderDestination == CineVaultRenderDestination.HOST_DISPLAY
