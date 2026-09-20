package com.sole.cinevault.glasses.halo

import kotlin.math.abs

/**
 * D2-19 — direction-aware geometry router for the agreed glasses player
 * gestures.
 *
 * Same CineVault player, another display.
 *
 * Locked controller geometry:
 *  - left 20%  : vertical brightness
 *  - right 20% : vertical volume
 *  - top 50%   : HORIZONTAL seek-eligible area
 *  - otherwise : canonical Halo/CineVault pointer interaction
 *
 * Important: top 50% is SEEK-ELIGIBLE, not seek-exclusive. A vertical drag
 * starting in the left/right 20% still wins brightness/volume. This preserves
 * large, easy seek coverage without sacrificing the side controls.
 *
 * A small normalized movement threshold prevents ordinary pointer settling
 * from immediately becoming brightness/volume/seek.
 */
object HaloPlayerGestureRouter {

    fun zoneFor(position: HaloVector): HaloPlayerGestureZone {
        val x = position.x.coerceIn(0f, 1f)
        val y = position.y.coerceIn(0f, 1f)

        return when {
            y <= HaloPlayerGestureGeometry.TOP_SEEK_FRACTION ->
                HaloPlayerGestureZone.SEEK

            x <= HaloPlayerGestureGeometry.SIDE_FRACTION ->
                HaloPlayerGestureZone.BRIGHTNESS

            x >= 1f - HaloPlayerGestureGeometry.SIDE_FRACTION ->
                HaloPlayerGestureZone.VOLUME

            else ->
                HaloPlayerGestureZone.CANONICAL_UI
        }
    }

    fun classifyDrag(
        start: HaloVector,
        current: HaloVector,
    ): HaloPlayerGestureIntent {
        val x = start.x.coerceIn(0f, 1f)
        val y = start.y.coerceIn(0f, 1f)

        val deltaX = (current.x - start.x).coerceIn(-1f, 1f)
        // Positive means finger moved upward, matching the existing bridge.
        val deltaY = (start.y - current.y).coerceIn(-1f, 1f)

        val absX = abs(deltaX)
        val absY = abs(deltaY)

        if (
            absX < HaloPlayerGestureGeometry.ARM_THRESHOLD_FRACTION &&
            absY < HaloPlayerGestureGeometry.ARM_THRESHOLD_FRACTION
        ) {
            return HaloPlayerGestureIntent.CanonicalUi
        }

        val horizontal =
            absX > absY * HaloPlayerGestureGeometry.DIRECTION_DOMINANCE
        val vertical =
            absY > absX * HaloPlayerGestureGeometry.DIRECTION_DOMINANCE

        // Side vertical actions get first refusal, including in the top half.
        if (
            x <= HaloPlayerGestureGeometry.SIDE_FRACTION &&
            vertical
        ) {
            return HaloPlayerGestureIntent.Brightness(
                verticalDeltaFraction = deltaY,
            )
        }

        if (
            x >= 1f - HaloPlayerGestureGeometry.SIDE_FRACTION &&
            vertical
        ) {
            return HaloPlayerGestureIntent.Volume(
                verticalDeltaFraction = deltaY,
            )
        }

        // The complete top half is an easy horizontal scrub surface.
        if (
            y <= HaloPlayerGestureGeometry.TOP_SEEK_FRACTION &&
            horizontal
        ) {
            return HaloPlayerGestureIntent.Seek(
                horizontalDeltaFraction = deltaX,
            )
        }

        return HaloPlayerGestureIntent.CanonicalUi
    }
}

object HaloPlayerGestureGeometry {
    const val SIDE_FRACTION = 0.20f

    // D2-19: expanded from 30% to the locked 50% seek-eligible surface.
    const val TOP_SEEK_FRACTION = 0.50f

    // Normalized surface travel required before a player action can arm.
    const val ARM_THRESHOLD_FRACTION = 0.012f

    // Keeps diagonal pointer travel from accidentally becoming an adjustment.
    const val DIRECTION_DOMINANCE = 1.15f
}

enum class HaloPlayerGestureZone {
    BRIGHTNESS,
    VOLUME,
    SEEK,
    CANONICAL_UI,
}

sealed interface HaloPlayerGestureIntent {
    data class Brightness(
        val verticalDeltaFraction: Float,
    ) : HaloPlayerGestureIntent

    data class Volume(
        val verticalDeltaFraction: Float,
    ) : HaloPlayerGestureIntent

    data class Seek(
        val horizontalDeltaFraction: Float,
    ) : HaloPlayerGestureIntent

    data object CanonicalUi : HaloPlayerGestureIntent
}
