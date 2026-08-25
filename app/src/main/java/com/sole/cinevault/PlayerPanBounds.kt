package com.sole.cinevault

internal data class PlayerPanBounds(
    val maxOffsetX: Float,
    val maxOffsetY: Float,
)

internal fun calculatePlayerPanBounds(
    screenWidthPx: Float,
    screenHeightPx: Float,
    videoScale: Float,
): PlayerPanBounds {
    val scaleExtra = (videoScale - 1f).coerceAtLeast(0f)
    return PlayerPanBounds(
        maxOffsetX = screenWidthPx * scaleExtra / 2f,
        maxOffsetY = screenHeightPx * scaleExtra / 2f,
    )
}
