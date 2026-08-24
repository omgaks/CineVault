package com.sole.cinevault

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun calculatePlayerPopupOffsetX(
    iconCenterX: Float,
    popupWidth: Dp,
    screenWidthPx: Float,
    density: Density,
): Int {
    val popupWidthPx = with(density) { popupWidth.toPx() }
    val edgePaddingPx = with(density) { 8.dp.toPx() }

    return (iconCenterX - popupWidthPx / 2f)
        .coerceIn(
            edgePaddingPx,
            (screenWidthPx - popupWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx)
        )
        .roundToInt()
}

internal fun playerPopupBottomPadding(
    desiredBottomPadding: Dp,
): Dp = desiredBottomPadding
