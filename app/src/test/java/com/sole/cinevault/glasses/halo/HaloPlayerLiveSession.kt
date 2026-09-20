package com.sole.cinevault.glasses.halo

/**
 * D2-15 — live-player composition root for the new Halo gesture path.
 *
 * This is intentionally framework-free. PlayerPlaybackGestureLayer can create
 * one instance around the canonical callbacks it already owns, while the
 * pointer modifier only forwards begin/move/end/cancel events.
 *
 * Nothing here owns ExoPlayer or duplicates player UI state.
 */
class HaloPlayerLiveSession(
    brightnessDrag: (Float) -> Unit,
    volumeDrag: (Float) -> Unit,
    seekTo: (Long) -> Unit,
    userActivity: () -> Unit,
) {
    private val controller =
        HaloPlayerSessionController(
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
    ): HaloPlayerGestureZone? =
        controller.beginDrag(
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
    ): HaloPlayerGestureIntent? =
        controller.updateDrag(
            currentX = xPx,
            currentY = yPx,
            surfaceWidthPx = widthPx,
            surfaceHeightPx = heightPx,
        )

    fun onDragEnd() = controller.endDrag()

    fun onDragCancel() = controller.cancel()

    /** Use on glasses disconnect, video replacement, or display recreation. */
    fun reset() = controller.cancel()

    fun isActive(): Boolean = controller.isActive()

    fun activeZone(): HaloPlayerGestureZone? = controller.activeZone()
}
