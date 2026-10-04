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

/**
 * Device-aware subtitle size, in sp, for the current window.
 *
 * Phones get smaller text than tablets and portrait gets smaller text than landscape, with a
 * smooth blend in between (foldables, split-screen, resized windows), instead of one fixed
 * number. Target sizes: phone 15 landscape / 13 portrait, tablet 18 landscape / 16 portrait.
 *
 * The user's saved size still counts: it is read relative to the default for this kind of
 * screen, so "a bit bigger than usual" stays a bit bigger on every device.
 */
internal fun autoSubtitleSizeSp(
    requestedSp: Float,
    availableWidthDp: Float,
    availableHeightDp: Float,
): Float {
    val shortSide = minOf(availableWidthDp, availableHeightDp)
    val landscape = availableWidthDp > availableHeightDp
    val t = ((shortSide - 400f) / 300f).coerceIn(0f, 1f)
    val target = if (landscape) 15f + 3f * t else 13f + 3f * t
    val profileDefault = if (shortSide < 600f) {
        if (landscape) 16f else 14f
    } else {
        if (landscape) 18f else 16f
    }
    val userScale = (requestedSp.coerceIn(12f, 60f) / profileDefault).coerceIn(0.6f, 2.5f)
    return (target * userScale).coerceIn(11f, 60f)
}
