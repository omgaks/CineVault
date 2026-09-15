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

internal fun AudioRuntimeRescuePlan.toHandover(): AudioRuntimeRescueHandover =
    AudioRuntimeRescueHandover(
        mediaItem = mediaItem,
        resumePositionMs = request.resumePositionMs.coerceAtLeast(0L),
        playWhenReady = playWhenReady,
        playbackSpeed = playbackSpeed.coerceIn(0.25f, 4.0f),
        volume = volume.coerceIn(0.0f, 1.0f),
        audioTrackIdentity = audioTrackIdentity,
        subtitleSnapshot = subtitleSnapshot,
    )
