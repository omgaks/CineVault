package com.sole.cinevault

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal data class PlayerPopupLayout(
    val width: Dp,
    val maxHeight: Dp,
)

internal fun calculateSubtitleSearchLayout(
    maxWidth: Dp,
    maxHeight: Dp,
    isLandscape: Boolean,
    isCompactLandscape: Boolean,
): PlayerPopupLayout {
    val width = if (isLandscape) {
        (maxWidth.value * 0.72f).dp.coerceIn(320.dp, 620.dp)
    } else {
        (maxWidth.value * 0.94f).dp.coerceAtMost(480.dp)
    }

    val maxPanelHeight = when {
        isCompactLandscape -> (maxHeight.value * 0.88f).dp
        isLandscape -> (maxHeight.value * 0.90f).dp
        else -> (maxHeight.value * 0.65f).dp.coerceAtMost(580.dp)
    }

    return PlayerPopupLayout(width = width, maxHeight = maxPanelHeight)
}

internal fun calculateSubtitleStudioLayout(
    maxWidth: Dp,
    maxHeight: Dp,
    isLandscape: Boolean,
    isCompactLandscape: Boolean,
): PlayerPopupLayout {
    val isTabletSized = min(maxWidth.value, maxHeight.value) >= 600f

    val width = when {
        isTabletSized && isLandscape -> (maxWidth.value * 0.42f).dp.coerceIn(360.dp, 480.dp)
        isTabletSized -> (maxWidth.value * 0.52f).dp.coerceIn(320.dp, 420.dp)
        isLandscape -> (maxWidth.value * 0.42f).dp.coerceIn(300.dp, 420.dp)
        else -> (maxWidth.value * 0.78f).dp.coerceAtMost(320.dp)
    }

    val maxPanelHeight = when {
        isTabletSized && isLandscape -> (maxHeight.value * 0.78f).dp
        isTabletSized -> (maxHeight.value * 0.55f).dp
        isCompactLandscape -> (maxHeight.value * 0.82f).dp
        isLandscape -> (maxHeight.value * 0.80f).dp
        else -> (maxHeight.value * 0.52f).dp.coerceAtMost(420.dp)
    }

    return PlayerPopupLayout(width = width, maxHeight = maxPanelHeight)
}
