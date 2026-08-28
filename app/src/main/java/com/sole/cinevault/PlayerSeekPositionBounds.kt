package com.sole.cinevault

/**
 * Keeps seek positions inside the playable media timeline.
 */
internal fun playerBoundedSeekPosition(
    positionMs: Long,
    durationMs: Long,
): Long = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
