package com.sole.cinevault

/**
 * Preserves the existing one-minute duration threshold used as the
 * LaunchedEffect key for smart-segment loading.
 */
internal fun playerHasSmartSegmentDuration(
    durationMs: Long,
): Boolean = durationMs > 60_000L
