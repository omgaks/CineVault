package com.sole.cinevault

import androidx.media3.common.MediaItem

internal data class AudioRuntimeRescueHandover(
    val mediaItem: MediaItem,
    val resumePositionMs: Long,
    val playWhenReady: Boolean,
    val subtitleSnapshot: AudioRescueSubtitleSnapshot? = null,
)

internal fun AudioRuntimeRescuePlan.toHandover(): AudioRuntimeRescueHandover =
    AudioRuntimeRescueHandover(
        mediaItem = mediaItem,
        resumePositionMs = request.resumePositionMs.coerceAtLeast(0L),
        playWhenReady = playWhenReady,
        subtitleSnapshot = subtitleSnapshot,
    )
