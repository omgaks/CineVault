package com.sole.cinevault

/**
 * Slice 34: determines whether a remembered Dual Subs configuration is ready
 * to be rebuilt after reopening a movie.
 *
 * Kept pure so the restore gate is covered by plain JVM/JUnit4 tests.
 */
fun shouldApplyRestoredDualSubtitles(
    movieSubtitleMemoryReady: Boolean,
    restoredDualNeedsApply: Boolean,
    dualSubtitlesEnabled: Boolean,
    hasPrimarySubtitle: Boolean,
): Boolean =
    movieSubtitleMemoryReady &&
        restoredDualNeedsApply &&
        dualSubtitlesEnabled &&
        hasPrimarySubtitle
