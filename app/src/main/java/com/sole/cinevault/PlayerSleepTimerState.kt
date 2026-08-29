package com.sole.cinevault

/**
 * Preserves the existing condition for running the next sleep-timer tick.
 */
internal fun playerShouldTickSleepTimer(
    isActive: Boolean,
    remainingMs: Long,
): Boolean = isActive && remainingMs > 0
