package com.sole.cinevault

internal data class PlayerExternalZoomHud(
    val scale: Float,
    val percent: Int,
    val progressPercent: Int,
)

internal fun calculatePlayerExternalZoomHud(
    currentScale: Float,
    zoomFactor: Float,
): PlayerExternalZoomHud {
    val scale = (currentScale * zoomFactor).coerceIn(1f, 3f)
    return PlayerExternalZoomHud(
        scale = scale,
        percent = (scale * 100).toInt(),
        progressPercent = (((scale - 1f) / 2f) * 100).toInt(),
    )
}
