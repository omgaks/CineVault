package com.sole.cinevault.glasses.halo

import kotlin.math.hypot

/**
 * D4-5 — precision-aware click qualification.
 *
 * A settled Halo gets a slightly tighter tap-travel envelope. This improves
 * small-target selection without snapping, auto-clicking, long-press actions,
 * or device-specific assumptions.
 */
class HaloClickController(
    private val config: HaloClickConfig = HaloClickConfig(),
) {
    private var downAtMillis: Long? = null
    private var downPosition: HaloVector? = null
    private var precisionAtDown = false
    private var lastPressed = false

    fun update(
        position: HaloVector,
        pressed: Boolean,
        eventTimeMillis: Long,
        precisionStable: Boolean = false,
    ): List<HaloClickEvent> {
        val events = mutableListOf<HaloClickEvent>()

        if (pressed && !lastPressed) {
            downAtMillis = eventTimeMillis
            downPosition = position
            precisionAtDown = precisionStable
            events += HaloClickEvent(
                type = HaloClickEventType.PRESS,
                position = position,
            )
        } else if (!pressed && lastPressed) {
            val startedAt = downAtMillis
            val startedAtPosition = downPosition

            if (startedAt != null && startedAtPosition != null) {
                val duration = (eventTimeMillis - startedAt).coerceAtLeast(0L)
                val movement = distance(startedAtPosition, position)
                val travelLimit = if (precisionAtDown) {
                    config.precisionTapTravelFraction
                } else {
                    config.maxTapTravelFraction
                }

                if (
                    duration <= config.maxTapDurationMillis &&
                    movement <= travelLimit
                ) {
                    events += HaloClickEvent(
                        type = HaloClickEventType.CLICK,
                        position = position,
                        feedback = HaloClickFeedback(
                            visualPulse = true,
                            haptic = HaloHapticRequest.CLICK,
                        ),
                    )
                }
            }

            events += HaloClickEvent(
                type = HaloClickEventType.RELEASE,
                position = position,
            )

            clearPendingTap()
        }

        lastPressed = pressed
        return events
    }

    fun cancel() {
        clearPendingTap()
        lastPressed = false
    }

    private fun clearPendingTap() {
        downAtMillis = null
        downPosition = null
        precisionAtDown = false
    }

    private fun distance(a: HaloVector, b: HaloVector): Float =
        hypot(b.x - a.x, b.y - a.y)
}

data class HaloClickConfig(
    val maxTapDurationMillis: Long = 350L,

    /** Standard Halo click travel: 2.5% of the normalized interaction surface. */
    val maxTapTravelFraction: Float = 0.025f,

    /**
     * Precision Halo click travel: 1.5% of the normalized interaction surface.
     * Used only when the pointer was already stable before press-down.
     */
    val precisionTapTravelFraction: Float = 0.015f,
) {
    init {
        require(maxTapDurationMillis > 0L)
        require(maxTapTravelFraction >= 0f)
        require(precisionTapTravelFraction >= 0f)
        require(precisionTapTravelFraction <= maxTapTravelFraction)
    }
}

data class HaloClickEvent(
    val type: HaloClickEventType,
    val position: HaloVector,
    val feedback: HaloClickFeedback = HaloClickFeedback(),
)

enum class HaloClickEventType {
    PRESS,
    CLICK,
    RELEASE,
}

data class HaloClickFeedback(
    val visualPulse: Boolean = false,
    val haptic: HaloHapticRequest = HaloHapticRequest.NONE,
)

enum class HaloHapticRequest {
    NONE,
    CLICK,
}
