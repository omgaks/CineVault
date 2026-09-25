package com.sole.cinevault.glasses.halo

/**
 * D2-13 — lifecycle-safe owner for the D2-12 player action bridge.
 *
 * The external-display / Halo input layer calls this controller for one
 * pointer drag. It intentionally does not own ExoPlayer, Compose state,
 * brightness, volume or HUD state; those remain in the canonical CineVault
 * player through [HaloCanonicalPlayerActions].
 *
 * Keeping begin/update/end here gives the glasses display path one stable
 * session boundary and prevents a drag from leaking across disconnect,
 * orientation change, movie change or cancelled pointer input.
 */
class HaloPlayerSessionController(
    actions: HaloCanonicalPlayerActions,
) {
    private val bridge = HaloPlayerActionBridge(actions)

    private var active = false
    private var activeSurfaceWidthPx = 0
    private var activeSurfaceHeightPx = 0

    fun beginDrag(
        startX: Float,
        startY: Float,
        surfaceWidthPx: Int,
        surfaceHeightPx: Int,
        playbackPositionMs: Long,
        durationMs: Long,
    ): HaloPlayerGestureZone? {
        if (surfaceWidthPx <= 0 || surfaceHeightPx <= 0) {
            active = false
            return null
        }

        active = true
        activeSurfaceWidthPx = surfaceWidthPx
        activeSurfaceHeightPx = surfaceHeightPx

        return bridge.begin(
            start = HaloVector(
                x = normalize(startX, surfaceWidthPx),
                y = normalize(startY, surfaceHeightPx),
            ),
            viewportHeightPx = surfaceHeightPx,
            playbackPositionMs = playbackPositionMs,
            durationMs = durationMs,
        )
    }

    fun updateDrag(
        currentX: Float,
        currentY: Float,
        surfaceWidthPx: Int,
        surfaceHeightPx: Int,
    ): HaloPlayerGestureIntent? {
        if (!active || surfaceWidthPx <= 0 || surfaceHeightPx <= 0) return null

        if (
            surfaceWidthPx != activeSurfaceWidthPx ||
            surfaceHeightPx != activeSurfaceHeightPx
        ) {
            cancel()
            return null
        }

        return bridge.update(
            HaloVector(
                x = normalize(currentX, surfaceWidthPx),
                y = normalize(currentY, surfaceHeightPx),
            ),
        )
    }

    fun endDrag() {
        if (!active) return
        bridge.end()
        clearActiveGeometry()
    }

    /**
     * Must be used for pointer cancellation, display disconnect, movie change,
     * orientation recreation and emergency return-to-tablet.
     */
    fun cancel() {
        bridge.cancel()
        clearActiveGeometry()
    }

    fun isActive(): Boolean = active

    fun activeZone(): HaloPlayerGestureZone? =
        if (active) bridge.activeZone() else null

    private fun clearActiveGeometry() {
        active = false
        activeSurfaceWidthPx = 0
        activeSurfaceHeightPx = 0
    }

    private fun normalize(valuePx: Float, extentPx: Int): Float =
        (valuePx / extentPx.toFloat()).coerceIn(0f, 1f)
}
