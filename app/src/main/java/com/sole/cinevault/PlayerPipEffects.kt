package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.exoplayer.ExoPlayer

/**
 * Slice 46: groups the player's PiP lifecycle side effects.
 *
 * CineVault UI cleanup remains callback-driven so this helper does not own
 * player menu state. PiP media actions continue through the existing
 * PlayerPipActionReceiverEffect.
 */
@Composable
fun PlayerPipEffects(
    context: Context,
    player: ExoPlayer,
    isInPipMode: Boolean,
    onEnteredPip: () -> Unit,
) {
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            onEnteredPip()
        }
    }

    PlayerPipActionReceiverEffect(
        context = context,
        player = player,
    )
}
