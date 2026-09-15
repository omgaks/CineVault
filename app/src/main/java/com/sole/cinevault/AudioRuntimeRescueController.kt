package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

internal data class AudioRuntimeRescuePlan(
    val request: AudioPlaybackRescueRequest,
    val mediaItem: MediaItem,
    val playWhenReady: Boolean,
)

internal object AudioRuntimeRescueController {
    var rendererPreference by mutableStateOf(CineAudioRendererPreference.PLATFORM_FIRST)
        private set

    private var pendingPlan: AudioRuntimeRescuePlan? = null

    fun request(
        player: ExoPlayer,
        request: AudioPlaybackRescueRequest,
    ): Boolean {
        val mediaItem = player.currentMediaItem ?: return false
        pendingPlan = AudioRuntimeRescuePlan(
            request = request,
            mediaItem = mediaItem,
            playWhenReady = player.playWhenReady,
        )
        rendererPreference = request.rendererPreference
        return true
    }

    fun consumePendingPlan(): AudioRuntimeRescuePlan? =
        pendingPlan.also { pendingPlan = null }

    fun reset() {
        pendingPlan = null
        rendererPreference = CineAudioRendererPreference.PLATFORM_FIRST
    }
}
