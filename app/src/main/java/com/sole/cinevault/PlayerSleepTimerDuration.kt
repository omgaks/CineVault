package com.sole.cinevault

/**
 * Converts sleep-timer minutes to milliseconds using the same arithmetic
 * previously kept inline in VideoPlayerScreen.
 */
internal fun playerSleepTimerDurationMs(
    minutes: Int,
): Long = minutes * 60 * 1000L
