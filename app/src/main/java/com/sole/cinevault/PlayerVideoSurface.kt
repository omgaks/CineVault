package com.sole.cinevault

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView

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
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = videoScale,
                scaleY = videoScale,
                translationX = videoOffsetX,
                translationY = videoOffsetY
            ),
        factory = { context ->
            PlayerView(context).apply {
                this.player = if (externalDisplayActive) null else player
                useController = false
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
        }
    )
}
