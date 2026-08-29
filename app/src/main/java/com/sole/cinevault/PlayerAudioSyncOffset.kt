package com.sole.cinevault

/**
 * Converts the player audio sync offset from milliseconds to microseconds
 * using the same calculation previously kept inline in VideoPlayerScreen.
 */
internal fun playerAudioSyncOffsetUs(
    audioSyncMs: Int,
): Long = audioSyncMs * 1000L
