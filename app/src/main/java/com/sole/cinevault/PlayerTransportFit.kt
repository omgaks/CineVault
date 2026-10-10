package com.sole.cinevault

/**
 * The bottom transport row has a fixed number of buttons. On a narrow
 * portrait screen they do not all fit, so the row would be cut off and need
 * scrolling. This works out how much to shrink the row so everything shows.
 * Pure Kotlin so it can be tested without a phone.
 */
internal const val MIN_TRANSPORT_FIT = 0.6f

internal fun transportFitFactor(
    availableDp: Float,
    smallButtonDp: Float,
    playButtonDp: Float,
    smallButtonCount: Int,
    spacingDp: Float,
    extraGapDp: Float,
    sidePaddingDp: Float
): Float {
    val items = smallButtonCount + 1
    val content = smallButtonCount * smallButtonDp + playButtonDp +
        (items - 1) * spacingDp + extraGapDp + 2 * sidePaddingDp
    if (content <= 0f || availableDp <= 0f) return 1f
    return (availableDp / content).coerceIn(MIN_TRANSPORT_FIT, 1f)
}
