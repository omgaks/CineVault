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
    val playbackSpeed: Float,
    val audioTrackIdentity: AudioRescueTrackIdentity? = null,
    val subtitleSnapshot: AudioRescueSubtitleSnapshot? = null,
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
        subtitleSnapshot: AudioRescueSubtitleSnapshot? = null,
    ): Boolean {
        val mediaItem = player.currentMediaItem ?: return false
        pendingPlan = AudioRuntimeRescuePlan(
            request = request,
            mediaItem = mediaItem,
            playWhenReady = player.playWhenReady,
            playbackSpeed = player.playbackParameters.speed,
            audioTrackIdentity = player.selectedAudioTrackIdentityForRescue(),
            subtitleSnapshot = subtitleSnapshot,
        )
        rescueVideoPath = videoPath
        rendererPreference = request.rendererPreference
        return true
    }

    fun consumePendingPlan(): AudioRuntimeRescuePlan? =
        pendingPlan.also { pendingPlan = null }

    fun resetForVideo(videoPath: String): Boolean {
        val rescuedPath = rescueVideoPath ?: return false
        if (rescuedPath == videoPath) return false
        reset()
        return true
    }

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
