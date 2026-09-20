package com.sole.cinevault.glasses.halo

import kotlin.math.hypot

/**
 * D2-6 — Halo drag / scroll ownership.
 *
 * This controller decides when a one-touch Halo interaction stops being a
 * possible click and becomes a drag. It does NOT implement a second scrolling
 * system. The emitted drag deltas are intended to be forwarded to the existing
 * CineVault scrollable/drag target under Halo.
 *
 * Ownership rule:
 *   DOWN -> POSSIBLE_CLICK
 *   travel below threshold -> still possible click
 *   travel beyond threshold -> DRAG_START, then DRAG
 *   UP after drag -> DRAG_END
 *
 * This keeps click and drag mutually exclusive.
 */
class HaloDragController(
    private val config: HaloDragConfig = HaloDragConfig(),
) {
    private var start: HaloVector? = null
    private var previous: HaloVector? = null
    private var dragging = false

    fun update(
        position: HaloVector,
        pressed: Boolean,
    ): List<HaloDragEvent> {
        val events = mutableListOf<HaloDragEvent>()

        if (pressed) {
            val startPosition = start
            val previousPosition = previous

            if (startPosition == null || previousPosition == null) {
                start = position
                previous = position
                dragging = false
                return emptyList()
            }

            val totalTravel = distance(startPosition, position)

            if (!dragging && totalTravel >= config.dragStartTravelFraction) {
                dragging = true
                events += HaloDragEvent(
                    type = HaloDragEventType.DRAG_START,
                    position = position,
                    delta = HaloVector(
                        x = position.x - startPosition.x,
                        y = position.y - startPosition.y,
                    ),
                )
            } else if (dragging) {
                events += HaloDragEvent(
                    type = HaloDragEventType.DRAG,
                    position = position,
                    delta = HaloVector(
                        x = position.x - previousPosition.x,
                        y = position.y - previousPosition.y,
                    ),
                )
            }

            previous = position
            return events
        }

        if (dragging) {
            events += HaloDragEvent(
                type = HaloDragEventType.DRAG_END,
                position = position,
                delta = HaloVector(0f, 0f),
            )
        }

        reset()
        return events
    }

    fun cancel(): HaloDragEvent? {
        val event = if (dragging) {
            HaloDragEvent(
                type = HaloDragEventType.DRAG_CANCEL,
                position = previous ?: HaloVector(0f, 0f),
                delta = HaloVector(0f, 0f),
            )
        } else {
            null
        }

        reset()
        return event
    }

    fun isDragging(): Boolean = dragging

    private fun reset() {
        start = null
        previous = null
        dragging = false
    }

    private fun distance(a: HaloVector, b: HaloVector): Float =
        hypot(b.x - a.x, b.y - a.y)
}

data class HaloDragConfig(
    /**
     * 1.5% of the normalised surface before drag owns the gesture.
     * This is deliberately lower than D2-5's click travel ceiling so a
     * deliberate scroll is claimed early instead of feeling sticky.
     */
    val dragStartTravelFraction: Float = 0.015f,
) {
    init {
        require(dragStartTravelFraction > 0f)
    }
}

data class HaloDragEvent(
    val type: HaloDragEventType,
    val position: HaloVector,
    val delta: HaloVector,
)

enum class HaloDragEventType {
    DRAG_START,
    DRAG,
    DRAG_END,
    DRAG_CANCEL,
}
