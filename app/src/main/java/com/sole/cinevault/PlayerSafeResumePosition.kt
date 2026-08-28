package com.sole.cinevault

/**
 * Normalizes a player position before it is reused as a resume point.
 * Media3 can briefly expose an unset/negative position during transitions;
 * resume operations should always start from zero or later.
 */
internal fun playerSafeResumePosition(currentPositionMs: Long): Long =
    currentPositionMs.coerceAtLeast(0L)
