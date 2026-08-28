package com.sole.cinevault

internal fun playerSeekBackPosition(
    currentPosition: Long,
    stepMs: Long = 10_000L,
): Long {
    return (currentPosition - stepMs).coerceAtLeast(0L)
}

internal fun playerSeekForwardPosition(
    currentPosition: Long,
    duration: Long,
    stepMs: Long = 10_000L,
): Long {
    return (currentPosition + stepMs).coerceAtMost(duration.coerceAtLeast(0L))
}
