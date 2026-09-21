package com.sole.cinevault.glasses.halo

import kotlin.math.hypot

/**
 * D4-9 — Halo focus-confidence foundation.
 *
 * Converts target stability into a small, deterministic confidence state that
 * can later drive app-wide hover/focus treatment. It is descriptive only:
 * it never clicks, snaps, captures focus, or changes gesture ownership.
 */
class HaloFocusConfidenceController(
    private val config: HaloFocusConfidenceConfig = HaloFocusConfidenceConfig(),
) {
    fun evaluate(
        stability: HaloTargetStability,
        pressed: Boolean,
        dragging: Boolean,
    ): HaloFocusConfidence {
        if (pressed || dragging) return HaloFocusConfidence.NONE
        if (!stability.isStable) return HaloFocusConfidence.NONE

        val dwellProgress =
            (stability.dwellMillis.toFloat() / config.fullConfidenceDwellMillis)
                .coerceIn(0f, 1f)

        val distanceProgress =
            1f - (stability.distanceFromAnchor / config.maxStableDistanceFraction)
                .coerceIn(0f, 1f)

        val score = (dwellProgress * config.dwellWeight +
            distanceProgress * config.distanceWeight).coerceIn(0f, 1f)

        return when {
            score >= config.strongThreshold -> HaloFocusConfidence.STRONG
            score >= config.readyThreshold -> HaloFocusConfidence.READY
            else -> HaloFocusConfidence.SETTLING
        }
    }
}

enum class HaloFocusConfidence {
    NONE,
    SETTLING,
    READY,
    STRONG,
}

data class HaloFocusConfidenceConfig(
    val fullConfidenceDwellMillis: Long = 320L,
    val maxStableDistanceFraction: Float = 0.0125f,
    val dwellWeight: Float = 0.70f,
    val distanceWeight: Float = 0.30f,
    val readyThreshold: Float = 0.55f,
    val strongThreshold: Float = 0.82f,
) {
    init {
        require(fullConfidenceDwellMillis > 0L)
        require(maxStableDistanceFraction > 0f)
        require(dwellWeight >= 0f)
        require(distanceWeight >= 0f)
        require(dwellWeight + distanceWeight > 0f)
        require(readyThreshold in 0f..1f)
        require(strongThreshold in 0f..1f)
        require(strongThreshold >= readyThreshold)
    }
}
