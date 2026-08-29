package com.sole.cinevault

/**
 * Converts the subtitle sync offset from seconds to milliseconds using the
 * same Float-to-Long calculation previously kept inline in the player.
 */
internal fun playerSubtitleSyncOffsetMs(
    syncOffsetSeconds: Float,
): Long = (syncOffsetSeconds * 1000f).toLong()
