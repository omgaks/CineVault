package com.sole.cinevault.glasses.halo

/**
 * Live-player composition root for the Halo gesture path.
 *
 * Owns no ExoPlayer or UI state. It connects Halo input to the canonical
 * CineVault player callbacks through the existing session controller.
 */
class HaloPlayerLiveSession(
    brightnessDrag: (Float) -> Unit,
    volumeDrag: (Float) -> Unit,
    seekTo: (Long) -> Unit,
    userActivity: () -> Unit,
) {
    private val controller = HaloPlayerSessionController(
        HaloPlayerCanonicalActions(
            brightnessDrag = brightnessDrag,
            volumeDrag = volumeDrag,
            seekTo = seekTo,
            userActivity = userActivity,
        )
    )

    fun onDragStart(
        xPx: Float,
        yPx: Float,
        widthPx: Int,
        heightPx: Int,
        playbackPositionMs: Long,
        durationMs: Long,
    ): HaloPlayerGestureZone? = controller.beginDrag(
        startX = xPx,
        startY = yPx,
        surfaceWidthPx = widthPx,
        surfaceHeightPx = heightPx,
        playbackPositionMs = playbackPositionMs,
        durationMs = durationMs,
    )

    fun onDrag(
        xPx: Float,
        yPx: Float,
        widthPx: Int,
        heightPx: Int,
    ): HaloPlayerGestureIntent? = controller.updateDrag(
        currentX = xPx,
        currentY = yPx,
        surfaceWidthPx = widthPx,
        surfaceHeightPx = heightPx,
    )

    fun onDragEnd() = controller.endDrag()

    fun onDragCancel() = controller.cancel()

    fun reset() = controller.cancel()

    fun isActive(): Boolean = controller.isActive()

    fun activeZone(): HaloPlayerGestureZone? = controller.activeZone()
}
