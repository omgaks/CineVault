package com.sole.cinevault

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Slice 53: one immutable snapshot for all player/popup/Sub Studio geometry
 * derived from the current container and video dimensions.
 */
data class PlayerSurfaceLayout(
    val isLandscape: Boolean,
    val isSmallPhone: Boolean,
    val isCompactLandscape: Boolean,
    val scale: Float,
    val playButton: Dp,
    val smallButton: Dp,
    val hudSize: Dp,
    val sidePadding: Dp,
    val bottomDockPadding: Dp,
    val seekBottomPadding: Dp,
    val topClusterPaddingTop: Dp,
    val uiScale: Float,
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val popupBottomPadding: Dp,
    val subtitlePopupWidth: Dp,
    val subtitlePopupHeightEstimate: Dp,
    val trackSelectorWidth: Dp,
    val trackSelectorMaxHeight: Dp,
    val visibleMovieWidth: Dp,
    val studioFrameInset: Dp,
    val trackStudioWidth: Dp,
    val trackStudioMaxHeight: Dp,
    val styleStudioWidth: Dp,
    val styleStudioMaxHeight: Dp,
    val srtPopupWidth: Dp,
    val srtPopupMaxHeight: Dp,
    val audioPopupWidth: Dp,
    val smallMenuWidth: Dp,
    val smallMenuMaxHeight: Dp,
    val topIconSize: Dp,
)

/**
 * Pure branch used by [calculatePlayerSurfaceLayout] and unit-tested directly.
 *
 * Returns the width of the visible movie picture inside a fit-centre container.
 * Wide video fills the container width; narrower video is pillar-boxed.
 */
fun calculateVisibleMovieWidthDp(
    containerWidthDp: Float,
    containerHeightDp: Float,
    videoWidth: Int,
    videoHeight: Int,
    pixelWidthHeightRatio: Float,
): Float {
    val safePixelRatio = pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
    val videoAspect =
        if (videoWidth > 0 && videoHeight > 0) {
            (videoWidth.toFloat() * safePixelRatio) / videoHeight.toFloat()
        } else {
            0f
        }
    val containerAspect =
        if (containerHeightDp > 0f) containerWidthDp / containerHeightDp else 0f

    return when {
        videoAspect <= 0f || containerAspect <= 0f -> containerWidthDp
        videoAspect >= containerAspect -> containerWidthDp
        else -> (containerHeightDp * videoAspect).coerceAtMost(containerWidthDp)
    }
}

fun calculatePlayerSurfaceLayout(
    maxWidth: Dp,
    maxHeight: Dp,
    videoWidth: Int,
    videoHeight: Int,
    pixelWidthHeightRatio: Float,
    density: Density,
): PlayerSurfaceLayout {
    val displayLayout = calculatePlayerDisplayLayout(maxWidth, maxHeight)

    val popupDimensions = calculatePlayerPopupDimensions(
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        isLandscape = displayLayout.isLandscape,
        isCompactLandscape = displayLayout.isCompactLandscape,
        bottomDockPadding = displayLayout.bottomDockPadding,
        playButton = displayLayout.playButton,
    )

    val visibleMovieWidth = calculateVisibleMovieWidthDp(
        containerWidthDp = maxWidth.value,
        containerHeightDp = maxHeight.value,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        pixelWidthHeightRatio = pixelWidthHeightRatio,
    ).dp

    val movieFrameHorizontalInset =
        ((maxWidth - visibleMovieWidth) / 2f).coerceAtLeast(0.dp)
    val studioFrameInset =
        movieFrameHorizontalInset + if (maxWidth < 700.dp) 12.dp else 22.dp

    val trackStudioWidth =
        (popupDimensions.trackSelectorWidth * if (maxWidth >= 900.dp) 1.38f else 1.24f)
            .coerceAtLeast(if (maxWidth >= 900.dp) 330.dp else 270.dp)
            .coerceAtMost(
                (visibleMovieWidth - studioFrameInset * 2).coerceAtLeast(250.dp)
            )

    val trackStudioMaxHeight =
        (popupDimensions.trackSelectorMaxHeight * if (maxHeight >= 600.dp) 1.30f else 1.20f)
            .coerceAtMost((maxHeight - 24.dp).coerceAtLeast(250.dp))

    val styleStudioWidth =
        (popupDimensions.trackSelectorWidth * 1.28f)
            .coerceAtMost(
                (visibleMovieWidth - studioFrameInset * 2).coerceAtLeast(270.dp)
            )

    val styleStudioMaxHeight =
        (popupDimensions.trackSelectorMaxHeight * 1.22f)
            .coerceAtMost((maxHeight - 24.dp).coerceAtLeast(270.dp))

    return PlayerSurfaceLayout(
        isLandscape = displayLayout.isLandscape,
        isSmallPhone = displayLayout.isSmallPhone,
        isCompactLandscape = displayLayout.isCompactLandscape,
        scale = displayLayout.scale,
        playButton = displayLayout.playButton,
        smallButton = displayLayout.smallButton,
        hudSize = displayLayout.hudSize,
        sidePadding = displayLayout.sidePadding,
        bottomDockPadding = displayLayout.bottomDockPadding,
        seekBottomPadding = displayLayout.seekBottomPadding,
        topClusterPaddingTop = displayLayout.topClusterPaddingTop,
        uiScale = popupDimensions.uiScale,
        screenWidthPx = with(density) { maxWidth.toPx() },
        screenHeightPx = with(density) { maxHeight.toPx() },
        popupBottomPadding = popupDimensions.bottomPadding,
        subtitlePopupWidth = popupDimensions.subtitlePopupWidth,
        subtitlePopupHeightEstimate = popupDimensions.subtitlePopupHeightEstimate,
        trackSelectorWidth = popupDimensions.trackSelectorWidth,
        trackSelectorMaxHeight = popupDimensions.trackSelectorMaxHeight,
        visibleMovieWidth = visibleMovieWidth,
        studioFrameInset = studioFrameInset,
        trackStudioWidth = trackStudioWidth,
        trackStudioMaxHeight = trackStudioMaxHeight,
        styleStudioWidth = styleStudioWidth,
        styleStudioMaxHeight = styleStudioMaxHeight,
        srtPopupWidth = popupDimensions.srtPopupWidth,
        srtPopupMaxHeight = popupDimensions.srtPopupMaxHeight,
        audioPopupWidth = popupDimensions.audioPopupWidth,
        smallMenuWidth = popupDimensions.smallMenuWidth,
        smallMenuMaxHeight = popupDimensions.smallMenuMaxHeight,
        topIconSize = calculatePlayerTopIconSize(
            uiScale = popupDimensions.uiScale,
            playerScale = displayLayout.scale,
        ),
    )
}
