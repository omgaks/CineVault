package com.sole.cinevault

/**
 * Preserves the existing zero-minute condition used to switch the
 * sleep timer off.
 */
internal fun playerSleepTimerIsOff(
    minutes: Int,
): Boolean = minutes == 0
