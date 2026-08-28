package com.sole.cinevault

internal fun calculatePlayerSeekPreviewPosition(
    currentPosition: Long,
    deltaFraction: Float,
    duration: Long,
): Long {
    val safeDuration = duration.coerceAtLeast(1L)
    return (currentPosition + (deltaFraction * safeDuration).toLong())
        .coerceIn(0L, safeDuration)
}
