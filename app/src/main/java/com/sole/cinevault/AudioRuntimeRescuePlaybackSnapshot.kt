package com.sole.cinevault

internal data class AudioRuntimeRescuePlaybackSnapshot(
    val resumePositionMs: Long,
    val playWhenReady: Boolean,
    val playbackSpeed: Float,
    val volume: Float,
)

internal fun AudioRuntimeRescuePlaybackSnapshot.normalized(): AudioRuntimeRescuePlaybackSnapshot =
    copy(
        resumePositionMs = resumePositionMs.coerceAtLeast(0L),
        playbackSpeed = playbackSpeed.coerceIn(0.25f, 4.0f),
        volume = volume.coerceIn(0.0f, 1.0f),
    )
