package com.sole.cinevault

internal data class PlayerZoomPanTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

internal fun calculatePlayerZoomPanTransform(
    currentScale: Float,
    currentOffsetX: Float,
    currentOffsetY: Float,
    zoomFactor: Float,
    panX: Float,
    panY: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
): PlayerZoomPanTransform {
    var scale = (currentScale * zoomFactor).coerceIn(1f, 3f)
    var offsetX = currentOffsetX + panX
    var offsetY = currentOffsetY + panY

    val panBounds = calculatePlayerPanBounds(
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        videoScale = scale
    )

    offsetX = offsetX.coerceIn(-panBounds.maxOffsetX, panBounds.maxOffsetX)
    offsetY = offsetY.coerceIn(-panBounds.maxOffsetY, panBounds.maxOffsetY)

    if (scale <= 1.02f) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    return PlayerZoomPanTransform(
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY
    )
}
