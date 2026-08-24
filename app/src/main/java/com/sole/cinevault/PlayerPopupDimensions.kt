package com.sole.cinevault

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class PlayerPopupDimensions(
    val uiScale: Float,
    val bottomPadding: Dp,
    val subtitlePopupWidth: Dp,
    val subtitlePopupHeightEstimate: Dp,
    val trackSelectorWidth: Dp,
    val trackSelectorMaxHeight: Dp,
    val srtPopupWidth: Dp,
    val srtPopupMaxHeight: Dp,
    val audioPopupWidth: Dp,
    val smallMenuWidth: Dp,
    val smallMenuMaxHeight: Dp,
)

internal fun calculatePlayerPopupDimensions(
    maxWidth: Dp,
    maxHeight: Dp,
    isLandscape: Boolean,
    isCompactLandscape: Boolean,
    bottomDockPadding: Dp,
    playButton: Dp,
): PlayerPopupDimensions {
    val uiScale = (maxWidth.value / 400f).coerceIn(0.85f, 1.25f)
    val bottomPadding = bottomDockPadding + playButton + 18.dp

    val subtitlePopupWidthBase = if (isLandscape) {
        (maxWidth.value * 0.30f).dp.coerceIn(210.dp, 270.dp)
    } else {
        (maxWidth.value * 0.62f).dp.coerceIn(220.dp, 300.dp)
    }

    val subtitlePopupWidth = subtitleMenuWidth(maxWidth.value, isLandscape)
    val subtitlePopupHeightEstimate =
        (((if (isCompactLandscape || isLandscape) 220f else 360f) * uiScale).dp)
            .coerceAtMost(maxHeight * 0.45f)

    val trackSelectorWidth = subtitlePopupWidth
    val trackSelectorMaxHeight =
        (((if (isCompactLandscape || isLandscape) 230f else 380f) * uiScale).dp)
            .coerceAtMost(maxHeight * 0.55f)

    val srtPopupWidth =
        (subtitlePopupWidthBase.value * uiScale).dp.coerceAtMost(maxWidth * 0.86f)

    val srtPopupMaxHeight =
        (((if (isCompactLandscape) 160f else if (isLandscape) 200f else 280f) * uiScale).dp)
            .coerceAtMost(maxHeight * 0.5f)

    val audioPopupWidth =
        ((((if (isCompactLandscape) 175f else if (isLandscape) 190f else 205f) * uiScale).dp)
            .coerceAtMost(maxWidth * 0.75f)) * 0.6f

    val smallMenuWidth =
        (((165f * uiScale).dp).coerceAtMost(maxWidth * 0.6f)) * 0.6f

    val smallMenuHeightScale = if (isLandscape) 0.95f else 0.6f
    val smallMenuMaxHeight =
        ((((if (isCompactLandscape) 150f else if (isLandscape) 190f else 230f) * uiScale).dp)
            .coerceAtMost(maxHeight * 0.55f)) * smallMenuHeightScale

    return PlayerPopupDimensions(
        uiScale = uiScale,
        bottomPadding = bottomPadding,
        subtitlePopupWidth = subtitlePopupWidth,
        subtitlePopupHeightEstimate = subtitlePopupHeightEstimate,
        trackSelectorWidth = trackSelectorWidth,
        trackSelectorMaxHeight = trackSelectorMaxHeight,
        srtPopupWidth = srtPopupWidth,
        srtPopupMaxHeight = srtPopupMaxHeight,
        audioPopupWidth = audioPopupWidth,
        smallMenuWidth = smallMenuWidth,
        smallMenuMaxHeight = smallMenuMaxHeight,
    )
}
