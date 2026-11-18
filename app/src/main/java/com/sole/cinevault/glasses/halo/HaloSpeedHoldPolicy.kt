package com.sole.cinevault.glasses.halo

import kotlin.math.abs

/**
 * Temporary speed boost for the glasses player: hold several fingers still on the tablet
 * touchpad and the film plays faster for as long as you hold; let go and it returns to the
 * speed it had.
 *
 *  - 2 fingers held still -> 2x
 *  - 3 fingers held still -> 3x
 *  - 4 fingers held still -> 5x
 *
 * Five fingers is reserved for "return to tablet". Two-finger pinch/drag (zoom and move the
 * picture) still works: the hold only starts when the fingers stay put.
 */
object HaloSpeedHoldPolicy {
    const val HOLD_MS = 450L
    /** Normalised-pixel tolerance before fingers count as "moving", not holding. */
    const val MOVE_TOLERANCE_PX = 18f
    const val ZOOM_TOLERANCE = 0.06f

    fun speedFor(fingerCount: Int): Float? = when (fingerCount) {
        2 -> 2f
        3 -> 3f
        4 -> 5f
        else -> null
    }

    fun label(speed: Float): String = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

    /** True while the fingers have not travelled or pinched beyond the hold tolerances. */
    fun isStill(travelPx: Float, sizeRatio: Float): Boolean =
        travelPx <= MOVE_TOLERANCE_PX && abs(sizeRatio - 1f) <= ZOOM_TOLERANCE
}
