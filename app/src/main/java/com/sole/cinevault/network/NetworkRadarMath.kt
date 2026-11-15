package com.sole.cinevault.network

import kotlin.math.pow

/** Where a device sits on the radar: angle in degrees (0..360) and distance as 0.35..0.9 of the radius. */
internal fun radarPlacement(deviceId: String): Pair<Float, Float> {
    val h = deviceId.hashCode() and 0x7fffffff
    val angle = (h % 360).toFloat()
    val distance = 0.35f + ((h / 360) % 55) / 100f
    return angle to distance
}

/** 0..1 glow: brightest right after the sweep passes [blipAngle]. */
internal fun sweepGlow(sweepAngle: Float, blipAngle: Float): Float {
    val behind = (((sweepAngle - blipAngle) % 360f) + 360f) % 360f / 360f
    return (1f - behind).pow(6f)
}
