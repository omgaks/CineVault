package com.sole.cinevault

/**
 * Applies the same one-second countdown step previously performed inline
 * by VideoPlayerScreen.
 */
internal fun playerSleepTimerRemainingAfterTick(
    remainingMs: Long,
): Long = remainingMs - 1000
