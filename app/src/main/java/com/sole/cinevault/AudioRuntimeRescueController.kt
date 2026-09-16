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
    val volume: Float,
    val audioTrackIdentity: AudioRescueTrackIdentity? = null,
    val subtitleSnapshot: AudioRescueSubtitleSnapshot? = null,
)

internal enum class AudioRuntimeRescueAdmission {
    ACCEPT,
    REJECT_PENDING_RESCUE,
    REJECT_ALREADY_IN_RESCUE_MODE,
}

internal fun decideAudioRuntimeRescueAdmission(
    hasPendingPlan: Boolean,
    rendererPreference: CineAudioRendererPreference,
): AudioRuntimeRescueAdmission =
    when {
        hasPendingPlan ->
            AudioRuntimeRescueAdmission.REJECT_PENDING_RESCUE

        rendererPreference != CineAudioRendererPreference.PLATFORM_FIRST ->
            AudioRuntimeRescueAdmission.REJECT_ALREADY_IN_RESCUE_MODE

        else ->
            AudioRuntimeRescueAdmission.ACCEPT
    }

internal fun shouldConsumeAudioRuntimeRescuePlan(
    rescueVideoPath: String?,
    expectedVideoPath: String?,
): Boolean =
    rescueVideoPath != null &&
        expectedVideoPath != null &&
        rescueVideoPath == expectedVideoPath

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
        if (
            decideAudioRuntimeRescueAdmission(
                hasPendingPlan = pendingPlan != null,
                rendererPreference = rendererPreference,
            ) != AudioRuntimeRescueAdmission.ACCEPT
        ) {
            return false
        }

        val mediaItem = player.currentMediaItem ?: return false
        pendingPlan = AudioRuntimeRescuePlan(
            request = request,
            mediaItem = mediaItem,
            playWhenReady = player.playWhenReady,
            playbackSpeed = player.playbackParameters.speed,
            volume = player.volume,
            audioTrackIdentity = player.selectedAudioTrackIdentityForRescue(),
            subtitleSnapshot = subtitleSnapshot,
        )
        rescueVideoPath = videoPath
        rendererPreference = request.rendererPreference
        return true
    }

    fun consumePendingPlan(expectedVideoPath: String): AudioRuntimeRescuePlan? {
        if (
            !shouldConsumeAudioRuntimeRescuePlan(
                rescueVideoPath = rescueVideoPath,
                expectedVideoPath = expectedVideoPath,
            )
        ) {
            return null
        }

        return pendingPlan.also { pendingPlan = null }
    }

    fun enterVideoScope(videoPath: String): Boolean {
        val decision = decideAudioRuntimeRescueScope(
            scope = AudioRuntimeRescueScope(
                videoPath = rescueVideoPath,
                hasPendingPlan = pendingPlan != null,
                rendererPreference = rendererPreference,
            ),
            activeVideoPath = videoPath,
        )

        if (decision != AudioRuntimeRescueScopeDecision.RESET) return false
        reset()
        return true
    }

    fun resetForVideo(videoPath: String): Boolean =
        enterVideoScope(videoPath)

    fun endVideoScope(videoPath: String): Boolean {
        val decision = decideAudioRuntimeRescueExit(
            rescueVideoPath = rescueVideoPath,
            exitingVideoPath = videoPath,
        )
        if (decision != AudioRuntimeRescueExitDecision.RESET) return false
        reset()
        return true
    }

    fun reset() {
        pendingPlan = null
        rescueVideoPath = null
        rendererPreference = CineAudioRendererPreference.PLATFORM_FIRST
    }
}
