package com.sole.cinevault.glasses.gestures

/**
 * Stable geometry/threshold contract for the tablet-as-touchpad surface used
 * while an external glasses display is active.
 *
 * Keeping these values out of PlayerGestureModifiers makes the raw pointer
 * detector smaller and lets the interaction zones be regression-tested without
 * Android/Compose pointer infrastructure.
 */
object GlassesGesturePolicy {
    const val EDGE_ZONE_FRACTION = 0.10f
    const val BRIGHTNESS_ZONE_END_FRACTION = 1f / 3f
    const val VOLUME_ZONE_START_FRACTION = 2f / 3f

    const val SEEK_ARM_THRESHOLD_DP = 32
    const val EDGE_SWIPE_THRESHOLD_DP = 48

    const val EMERGENCY_SPREAD_SCALE = 1.35f

    fun isLeftEdge(x: Float, width: Float): Boolean =
        width > 0f && x < width * EDGE_ZONE_FRACTION

    fun isRightEdge(x: Float, width: Float): Boolean =
        width > 0f && x > width * (1f - EDGE_ZONE_FRACTION)

    fun isBrightnessZone(x: Float, width: Float): Boolean =
        width > 0f &&
            !isLeftEdge(x, width) &&
            x < width * BRIGHTNESS_ZONE_END_FRACTION

    fun isVolumeZone(x: Float, width: Float): Boolean =
        width > 0f &&
            x > width * VOLUME_ZONE_START_FRACTION &&
            !isRightEdge(x, width)

    fun isCenterZone(x: Float, width: Float): Boolean =
        width > 0f &&
            x >= width * BRIGHTNESS_ZONE_END_FRACTION &&
            x <= width * VOLUME_ZONE_START_FRACTION
}
