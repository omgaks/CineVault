package com.sole.cinevault.glasses.display

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.media3.common.util.UnstableApi
import com.sole.cinevault.CineVaultPlayerHolder
import com.sole.cinevault.PlayerVideoSurface

/**
 * Draws the film on the glasses.
 *
 * There is only ONE ExoPlayer, owned by the phone's player screen. While it exists, the
 * glasses copy of the app puts a video surface bound to that same player on top of the app,
 * so no second player or decoder is ever created. When the film ends, the layer disappears
 * and the glasses show the app again.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun ExternalCanonicalVideoLayer() {
    val player = CineVaultPlayerHolder.currentPlayer ?: return
    DisposableEffect(player) {
        onDispose {
            ExternalPlayerViewRegistry.playerView = null
            ExternalPlayerHudState.reset()
        }
    }
    // 2D films shrink with the app size; 3D side-by-side stays full frame so depth is not broken.
    val size = GlassesScreenSizePolicy.effective(
        chosen = GlassesScreenSize.scale,
        fullFrame3d = ExternalPlayerHudState.fullFrame3d,
    )
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = size
                scaleY = size
            },
        ) {
            PlayerVideoSurface(
                player = player,
                externalDisplayActive = true,
                isZoomMode = false,
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f,
                onPlayerViewChanged = { ExternalPlayerViewRegistry.playerView = it },
                onResizeModeChanged = {},
            )
        }
        // Controls and messages always follow the chosen size so no edge button is cropped.
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = GlassesScreenSize.scale
                scaleY = GlassesScreenSize.scale
            },
        ) {
            ExternalPlayerHudOverlay(player = player)
        }
    }
}
