package com.sole.cinevault.glasses.halo

/**
 * D2-11 — geometry-only router for the agreed glasses player gestures.
 *
 * This does NOT create a glasses player. It classifies Halo interaction against
 * the existing CineVault player surface so later wiring can call the same
 * brightness / volume / seek actions already used by the normal player.
 *
 * Agreed zones:
 *  - left 20%  : vertical brightness
 *  - right 20% : vertical volume
 *  - top 30%   : horizontal seek scrub
 *  - remaining centre: normal Halo/canonical CineVault interaction
 *
 * Corner precedence is intentional: TOP SEEK wins over LEFT/RIGHT vertical
 * zones so the complete top strip behaves consistently from edge to edge.
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

            else -> HaloPlayerGestureZone.CANONICAL_UI
        }
    }

    fun classifyDrag(
        start: HaloVector,
        current: HaloVector,
    ): HaloPlayerGestureIntent {
        return when (zoneFor(start)) {
            HaloPlayerGestureZone.SEEK ->
                HaloPlayerGestureIntent.Seek(
                    horizontalDeltaFraction =
                        (current.x - start.x).coerceIn(-1f, 1f),
                )

            HaloPlayerGestureZone.BRIGHTNESS ->
                HaloPlayerGestureIntent.Brightness(
                    verticalDeltaFraction =
                        (start.y - current.y).coerceIn(-1f, 1f),
                )

            HaloPlayerGestureZone.VOLUME ->
                HaloPlayerGestureIntent.Volume(
                    verticalDeltaFraction =
                        (start.y - current.y).coerceIn(-1f, 1f),
                )

            HaloPlayerGestureZone.CANONICAL_UI ->
                HaloPlayerGestureIntent.CanonicalUi
        }
    }
}

object HaloPlayerGestureGeometry {
    const val SIDE_FRACTION = 0.20f
    const val TOP_SEEK_FRACTION = 0.30f
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
