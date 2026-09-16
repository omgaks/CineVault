package com.sole.cinevault

import androidx.media3.common.MediaItem

internal data class AudioRuntimeRescueHandover(
    val mediaItem: MediaItem,
    val resumePositionMs: Long,
    val playWhenReady: Boolean,
    val playbackSpeed: Float,
    val volume: Float = 1.0f,
    val audioTrackIdentity: AudioRescueTrackIdentity? = null,
    val subtitleSnapshot: AudioRescueSubtitleSnapshot? = null,
)

internal fun AudioRuntimeRescuePlan.toHandover(): AudioRuntimeRescueHandover {
    val playback = AudioRuntimeRescuePlaybackSnapshot(
        resumePositionMs = request.resumePositionMs,
        playWhenReady = playWhenReady,
        playbackSpeed = playbackSpeed,
        volume = volume,
    ).normalized()

    return AudioRuntimeRescueHandover(
        mediaItem = mediaItem,
        resumePositionMs = playback.resumePositionMs,
        playWhenReady = playback.playWhenReady,
        playbackSpeed = playback.playbackSpeed,
        volume = playback.volume,
        audioTrackIdentity = audioTrackIdentity,
        subtitleSnapshot = subtitleSnapshot,
    )
}
