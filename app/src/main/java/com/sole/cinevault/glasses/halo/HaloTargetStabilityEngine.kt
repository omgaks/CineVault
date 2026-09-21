package com.sole.cinevault.glasses.halo

import kotlin.math.hypot

/**
 * D4-2 — Halo target-acquisition stabiliser.
 *
 * Adds lightweight dwell/stability detection on top of the D4-1 motion engine.
 * It does not click, snap to UI elements, or know anything about device models.
 * Consumers can use [isStable] to render a calmer/precision Halo state while
 * the pointer is intentionally settling over a target.
 */
class HaloTargetStabilityEngine(
    private val config: HaloTargetStabilityConfig = HaloTargetStabilityConfig(),
) {
    private var anchor: HaloVector? = null
    private var stableSinceMillis: Long? = null
    private var lastEventTimeMillis: Long? = null

    fun update(
        position: HaloVector,
        pressed: Boolean,
        eventTimeMillis: Long,
    ): HaloTargetStability {
        val previousTime = lastEventTimeMillis
        if (previousTime != null && eventTimeMillis < previousTime) {
            reset(position)
        }
        lastEventTimeMillis = eventTimeMillis

        if (pressed) {
            anchor = position
            stableSinceMillis = null
            return HaloTargetStability(
                isStable = false,
                dwellMillis = 0L,
                distanceFromAnchor = 0f,
            )
        }

        val currentAnchor = anchor
        if (currentAnchor == null) {
            anchor = position
            stableSinceMillis = eventTimeMillis
            return HaloTargetStability(false, 0L, 0f)
        }

        val distance = hypot(
            position.x - currentAnchor.x,
            position.y - currentAnchor.y,
        )

        if (distance > config.stabilityRadiusFraction) {
            anchor = position
            stableSinceMillis = eventTimeMillis
            return HaloTargetStability(false, 0L, distance)
        }

        val since = stableSinceMillis ?: eventTimeMillis.also {
            stableSinceMillis = it
        }
        val dwell = (eventTimeMillis - since).coerceAtLeast(0L)

        return HaloTargetStability(
            isStable = dwell >= config.dwellThresholdMillis,
            dwellMillis = dwell,
            distanceFromAnchor = distance,
        )
    }

    fun reset(position: HaloVector? = null) {
        anchor = position
        stableSinceMillis = null
        lastEventTimeMillis = null
    }
}

data class HaloTargetStabilityConfig(
    /** About 1.25% of the current render axis; no fixed pixel/device assumption. */
    val stabilityRadiusFraction: Float = 0.0125f,
    /** Short enough to feel immediate, long enough to ignore transit motion. */
    val dwellThresholdMillis: Long = 140L,
) {
    init {
        require(stabilityRadiusFraction in 0f..0.10f)
        require(dwellThresholdMillis >= 0L)
    }
}

data class HaloTargetStability(
    val isStable: Boolean,
    val dwellMillis: Long,
    val distanceFromAnchor: Float,
)
