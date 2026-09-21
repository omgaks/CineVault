package com.sole.cinevault.glasses.halo

import kotlin.math.hypot
import kotlin.math.pow

/** D4-1 — Halo 2.0 motion foundation: adaptive acceleration plus soft-edge precision. */
class HaloMotionEngine(
    private val config: HaloMotionConfig = HaloMotionConfig(),
) {
    private var lastRaw: HaloVector? = null
    private var cursor: HaloVector? = null

    fun reset(position: HaloVector? = null) {
        lastRaw = position
        cursor = position
    }

    fun update(rawXFraction: Float, rawYFraction: Float, deltaTimeMillis: Long): HaloVector {
        val raw = HaloVector(rawXFraction.coerceIn(0f, 1f), rawYFraction.coerceIn(0f, 1f))
        val previousRaw = lastRaw
        val previousCursor = cursor
        if (previousRaw == null || previousCursor == null) {
            lastRaw = raw
            cursor = raw
            return raw
        }

        val dt = deltaTimeMillis.coerceIn(config.minFrameMillis, config.maxFrameMillis).toFloat()
        val dx = raw.x - previousRaw.x
        val dy = raw.y - previousRaw.y
        val velocityPerSecond = hypot(dx, dy) * (1000f / dt)
        val gain = adaptiveGain(velocityPerSecond)

        val target = HaloVector(
            applySoftEdge(previousCursor.x, previousCursor.x + dx * gain),
            applySoftEdge(previousCursor.y, previousCursor.y + dy * gain),
        )

        val framesAt60Hz = dt / 16.6667f
        val alpha = 1f - (1f - config.smoothingAt60Hz).pow(framesAt60Hz)
        val output = HaloVector(
            lerp(previousCursor.x, target.x, alpha).coerceIn(0f, 1f),
            lerp(previousCursor.y, target.y, alpha).coerceIn(0f, 1f),
        )

        lastRaw = raw
        cursor = output
        return output
    }

    private fun adaptiveGain(velocityPerSecond: Float): Float {
        val t = (velocityPerSecond / config.fullAccelerationVelocity).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        return lerp(config.precisionGain, config.fastGain, eased)
    }

    /** Slow only motion travelling into an edge; escape motion remains unrestricted. */
    private fun applySoftEdge(previous: Float, proposed: Float): Float {
        val delta = proposed - previous
        if (delta == 0f || config.softEdgeFraction == 0f) return proposed.coerceIn(0f, 1f)

        val distanceToEdge = if (delta < 0f) previous else 1f - previous
        if (distanceToEdge >= config.softEdgeFraction) return proposed.coerceIn(0f, 1f)

        val edgeT = (distanceToEdge / config.softEdgeFraction).coerceIn(0f, 1f)
        val eased = edgeT * edgeT * (3f - 2f * edgeT)
        val multiplier = lerp(config.minimumEdgeGain, 1f, eased)
        return (previous + delta * multiplier).coerceIn(0f, 1f)
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float =
        start + (end - start) * amount
}

data class HaloMotionConfig(
    val precisionGain: Float = 0.95f,
    val fastGain: Float = 1.75f,
    val fullAccelerationVelocity: Float = 1.4f,
    val smoothingAt60Hz: Float = 0.82f,
    val softEdgeFraction: Float = 0.07f,
    val minimumEdgeGain: Float = 0.42f,
    val minFrameMillis: Long = 4L,
    val maxFrameMillis: Long = 50L,
) {
    init {
        require(precisionGain > 0f)
        require(fastGain >= precisionGain)
        require(fullAccelerationVelocity > 0f)
        require(smoothingAt60Hz in 0f..1f)
        require(softEdgeFraction in 0f..0.25f)
        require(minimumEdgeGain in 0f..1f)
        require(minFrameMillis > 0L)
        require(maxFrameMillis >= minFrameMillis)
    }
}

data class HaloVector(val x: Float, val y: Float)
