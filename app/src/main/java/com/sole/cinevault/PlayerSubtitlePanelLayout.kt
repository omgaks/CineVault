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
        isCompactLandscape -> (maxHeight.value * 0.76f).dp.coerceAtMost(280.dp)
        isLandscape -> (maxHeight.value * 0.78f).dp.coerceAtMost(360.dp)
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
        isTabletSized && isLandscape -> (maxWidth.value * 0.50f).dp.coerceIn(420.dp, 640.dp)
        isTabletSized -> (maxWidth.value * 0.75f).dp.coerceIn(420.dp, 560.dp)
        isLandscape -> (maxWidth.value * 0.60f).dp.coerceIn(300.dp, 420.dp)
        else -> (maxWidth.value * 0.92f).dp.coerceAtMost(380.dp)
    }

    val maxPanelHeight = when {
        isTabletSized && isLandscape -> (maxHeight.value * 0.80f).dp.coerceAtMost(560.dp)
        isTabletSized -> (maxHeight.value * 0.65f).dp.coerceAtMost(680.dp)
        isCompactLandscape -> (maxHeight.value * 0.80f).dp.coerceAtMost(260.dp)
        isLandscape -> (maxHeight.value * 0.82f).dp.coerceAtMost(320.dp)
        else -> (maxHeight.value * 0.58f).dp.coerceAtMost(480.dp)
    }

    return PlayerPopupLayout(width = width, maxHeight = maxPanelHeight)
}
