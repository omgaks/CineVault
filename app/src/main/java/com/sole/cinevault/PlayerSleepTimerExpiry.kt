package com.sole.cinevault

/**
 * Preserves the existing condition used to determine when the sleep timer
 * has reached zero and playback should pause.
 */
internal fun playerSleepTimerHasExpired(
    remainingMs: Long,
): Boolean = remainingMs <= 0
