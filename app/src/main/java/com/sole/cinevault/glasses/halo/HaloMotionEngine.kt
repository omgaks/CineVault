package com.sole.cinevault.glasses.halo

import kotlin.math.hypot
import kotlin.math.pow

/**
 * D2-4 — Halo feel engine.
 *
 * Goal: remove the slow/dragging feel observed on-device without making small
 * targets difficult to hit.
 *
 * Strategy:
 *  - no artificial dead-zone: tiny intentional motion still moves
 *  - adaptive gain: small movement stays precise, larger movement accelerates
 *  - light one-pole smoothing: reduces jitter without building a long history
 *  - frame-time normalization: behaviour remains stable across event rates
 *
 * This engine works in normalised 0..1 space and is resolution independent.
 */
class HaloMotionEngine(
    private val config: HaloMotionConfig = HaloMotionConfig(),
) {
    private var lastRaw: HaloVector? = null
    private var cursor: HaloVector? = null

    fun reset(position: HaloVector? = null) {
        lastRaw = position
        cursor = position
    }

    fun update(
        rawXFraction: Float,
        rawYFraction: Float,
        deltaTimeMillis: Long,
    ): HaloVector {
        val raw = HaloVector(
            x = rawXFraction.coerceIn(0f, 1f),
            y = rawYFraction.coerceIn(0f, 1f),
        )

        val previousRaw = lastRaw
        val previousCursor = cursor

        if (previousRaw == null || previousCursor == null) {
            lastRaw = raw
            cursor = raw
            return raw
        }

        val dt = deltaTimeMillis.coerceIn(
            config.minFrameMillis,
            config.maxFrameMillis,
        ).toFloat()

        val dx = raw.x - previousRaw.x
        val dy = raw.y - previousRaw.y
        val distance = hypot(dx, dy)

        val velocityPerSecond = distance * (1000f / dt)
        val gain = adaptiveGain(velocityPerSecond)

        val target = HaloVector(
            x = (previousCursor.x + dx * gain).coerceIn(0f, 1f),
            y = (previousCursor.y + dy * gain).coerceIn(0f, 1f),
        )

        // Convert the configured 60 Hz response into a frame-rate independent
        // alpha. This avoids a different Halo feel at different event rates.
        val framesAt60Hz = dt / 16.6667f
        val alpha = 1f - (1f - config.smoothingAt60Hz)
            .pow(framesAt60Hz)

        val output = HaloVector(
            x = lerp(previousCursor.x, target.x, alpha),
            y = lerp(previousCursor.y, target.y, alpha),
        )

        lastRaw = raw
        cursor = output
        return output
    }

    private fun adaptiveGain(velocityPerSecond: Float): Float {
        val t = (
            velocityPerSecond / config.fullAccelerationVelocity
        ).coerceIn(0f, 1f)

        // Smoothstep prevents a harsh acceleration threshold.
        val eased = t * t * (3f - 2f * t)

        return lerp(
            config.precisionGain,
            config.fastGain,
            eased,
        )
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float =
        start + (end - start) * amount
}

data class HaloMotionConfig(
    /** Near 1:1 movement for fine target acquisition. */
    val precisionGain: Float = 0.95f,

    /** Faster traversal when the finger moves decisively. */
    val fastGain: Float = 1.75f,

    /** Normalised units/second at which maximum acceleration is reached. */
    val fullAccelerationVelocity: Float = 1.4f,

    /**
     * High alpha = low latency. 0.82 intentionally keeps smoothing light;
     * Halo should feel attached to the finger rather than floating behind it.
     */
    val smoothingAt60Hz: Float = 0.82f,

    val minFrameMillis: Long = 4L,
    val maxFrameMillis: Long = 50L,
) {
    init {
        require(precisionGain > 0f)
        require(fastGain >= precisionGain)
        require(fullAccelerationVelocity > 0f)
        require(smoothingAt60Hz in 0f..1f)
        require(minFrameMillis > 0L)
        require(maxFrameMillis >= minFrameMillis)
    }
}

data class HaloVector(
    val x: Float,
    val y: Float,
)
