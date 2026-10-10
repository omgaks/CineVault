package com.sole.cinevault.ui.theme

// ============================================================
//  Pure layout rules (no Android types, so they are unit-tested).
//  The layout follows the WINDOW width, never the device type
//  or an aspect ratio.
// ============================================================

enum class CineWidthClass { Compact, Medium, Expanded }

/** Compact under 600dp, Medium 600 to 839dp, Expanded 840dp and up. */
fun cineWidthClassFor(widthDp: Int): CineWidthClass = when {
    widthDp < 600 -> CineWidthClass.Compact
    widthDp < 840 -> CineWidthClass.Medium
    else -> CineWidthClass.Expanded
}

/**
 * How many cards of at least [minCardDp] fit across [availableWidthDp],
 * counting [gapDp] between them. Always at least [minColumns].
 * Posters keep their 2:3 shape, so only the count changes, not the ratio.
 */
fun adaptiveColumnCount(
    availableWidthDp: Float,
    minCardDp: Float = 100f,
    gapDp: Float = 12f,
    minColumns: Int = 2,
    maxColumns: Int = 10
): Int {
    if (availableWidthDp <= 0f || minCardDp <= 0f) return minColumns
    val fit = ((availableWidthDp + gapDp) / (minCardDp + gapDp)).toInt()
    return fit.coerceIn(minColumns, maxColumns)
}

/** Wide or landscape windows use a side rail instead of a bottom dock. */
fun usesSideRail(widthDp: Int, heightDp: Int): Boolean =
    widthDp >= 600 || widthDp > heightDp
