package com.sole.cinevault

/**
 * Returns a non-zero duration for seek-preview calculations.
 *
 * ExoPlayer can temporarily report an unset or non-positive duration while
 * media is preparing. Keeping the previous 1 ms floor preserves the existing
 * seek-preview behaviour.
 */
internal fun playerSafeSeekDuration(
    durationMs: Long,
): Long = durationMs.coerceAtLeast(1L)
