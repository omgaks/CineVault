package com.sole.cinevault

import androidx.media3.common.MediaItem

internal data class AudioRuntimeRescueHandover(
    val mediaItem: MediaItem,
    val resumePositionMs: Long,
    val playWhenReady: Boolean,
)

/**
 * Produces the exact playback state that must cross the ExoPlayer rebuild.
 *
 * Keeping this transformation separate makes the handover deterministic and
 * testable without changing subtitle/media configuration already attached to
 * the MediaItem.
 */
internal fun AudioRuntimeRescuePlan.toHandover(): AudioRuntimeRescueHandover =
    AudioRuntimeRescueHandover(
        mediaItem = mediaItem,
        resumePositionMs = request.resumePositionMs.coerceAtLeast(0L),
        playWhenReady = playWhenReady,
    )
