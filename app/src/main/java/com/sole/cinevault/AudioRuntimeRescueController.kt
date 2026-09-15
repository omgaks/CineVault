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
    private var rescueVideoPath: String? = null

    fun request(
        player: ExoPlayer,
        request: AudioPlaybackRescueRequest,
        videoPath: String? = player.currentMediaItem?.localConfiguration?.uri?.toString(),
    ): Boolean {
        val mediaItem = player.currentMediaItem ?: return false
        pendingPlan = AudioRuntimeRescuePlan(
            request = request,
            mediaItem = mediaItem,
            playWhenReady = player.playWhenReady,
        )
        rescueVideoPath = videoPath
        rendererPreference = request.rendererPreference
        return true
    }

    fun consumePendingPlan(): AudioRuntimeRescuePlan? =
        pendingPlan.also { pendingPlan = null }

    /**
     * A rescue preference belongs only to the title that requested it.
     * Moving to a different movie/episode restores normal platform-first audio.
     */
    fun resetForVideo(videoPath: String): Boolean {
        val rescuedPath = rescueVideoPath ?: return false
        if (rescuedPath == videoPath) return false

        reset()
        return true
    }

    /**
     * Clears rescue state when the player screen for the rescued title leaves
     * composition. This prevents FFmpeg-first from leaking into a later player
     * session that happens to reopen the same path.
     *
     * A stale screen cannot clear a newer title's rescue.
     */
    fun endVideoScope(videoPath: String): Boolean {
        if (rescueVideoPath != videoPath) return false

        reset()
        return true
    }

    fun reset() {
        pendingPlan = null
        rescueVideoPath = null
        rendererPreference = CineAudioRendererPreference.PLATFORM_FIRST
    }
}
