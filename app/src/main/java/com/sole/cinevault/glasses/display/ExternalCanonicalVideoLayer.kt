package com.sole.cinevault.glasses.display

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerVideoSurface(
            player = player,
            externalDisplayActive = true,
            isZoomMode = false,
            videoScale = 1f,
            videoOffsetX = 0f,
            videoOffsetY = 0f,
            onPlayerViewChanged = {},
            onResizeModeChanged = {},
        )
    }
}
