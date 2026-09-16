package com.sole.cinevault.ui.responsive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Central adaptive dimensions. Add shared UI measurements here, not ad-hoc in screens. */
data class CineAdaptiveTokens(
    val screenMargin: Dp,
    val componentGap: Dp,
    val compactGap: Dp,
    val cornerRadius: Dp,
    val iconSize: Dp,
    val minimumTouchTarget: Dp,
    val playerSidePadding: Dp,
    val dialogMaxWidth: Dp,
)

fun cineAdaptiveTokens(info: CineWindowSizeInfo): CineAdaptiveTokens = when (info.widthClass) {
    WindowWidthClass.COMPACT -> CineAdaptiveTokens(16.dp, 12.dp, 6.dp, 16.dp, 20.dp, 48.dp, 14.dp, 360.dp)
    WindowWidthClass.MEDIUM -> CineAdaptiveTokens(28.dp, 14.dp, 8.dp, 18.dp, 22.dp, 48.dp, 20.dp, 480.dp)
    WindowWidthClass.EXPANDED -> CineAdaptiveTokens(40.dp, 16.dp, 8.dp, 20.dp, 24.dp, 48.dp, 28.dp, 560.dp)
}
