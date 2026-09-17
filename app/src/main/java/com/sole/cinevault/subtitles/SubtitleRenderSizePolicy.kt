package com.sole.cinevault.subtitles

/**
 * Converts the user's saved subtitle size into a rendered size for the
 * current player window. The saved preference is never changed.
 *
 * Compact portrait video has much less usable picture width than landscape,
 * so the same nominal SP value is visually overwhelming there. Medium
 * portrait gets a gentler constraint; expanded and landscape windows retain
 * the requested size.
 */
internal fun adaptiveSubtitleRenderSizeSp(
    requestedSp: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
): Float {
    val safeRequested = requestedSp.coerceIn(12f, 60f)
    val portrait = availableHeightDp > availableWidthDp
    if (!portrait) return safeRequested

    val factor = when {
        availableWidthDp < 600f -> 0.82f
        availableWidthDp < 840f -> 0.90f
        else -> 1.0f
    }
    return (safeRequested * factor).coerceAtLeast(12f)
}
