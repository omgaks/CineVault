package com.sole.cinevault.glasses.halo

/**
 * D2-5 — Halo click interaction contract.
 *
 * Converts the one-touch Halo interaction into explicit press/click/release
 * events without inventing a glasses-only UI. The next wiring slice can route
 * CLICK to the canonical CineVault hit target under the Halo position.
 *
 * Haptic and visual feedback are requests, not hardware assumptions.
 */
class HaloClickController(
    private val config: HaloClickConfig = HaloClickConfig(),
) {
    private var downAtMillis: Long? = null
    private var downPosition: HaloVector? = null
    private var lastPressed = false

    fun update(
        position: HaloVector,
        pressed: Boolean,
        eventTimeMillis: Long,
    ): List<HaloClickEvent> {
        val events = mutableListOf<HaloClickEvent>()

        if (pressed && !lastPressed) {
            downAtMillis = eventTimeMillis
            downPosition = position
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

                if (
                    duration <= config.maxTapDurationMillis &&
                    movement <= config.maxTapTravelFraction
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

            downAtMillis = null
            downPosition = null
        }

        lastPressed = pressed
        return events
    }

    fun cancel() {
        downAtMillis = null
        downPosition = null
        lastPressed = false
    }

    private fun distance(a: HaloVector, b: HaloVector): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

data class HaloClickConfig(
    val maxTapDurationMillis: Long = 350L,

    /**
     * Maximum movement while still classifying the interaction as a click.
     * 0.025 = 2.5% of the normalised Halo surface.
     */
    val maxTapTravelFraction: Float = 0.025f,
) {
    init {
        require(maxTapDurationMillis > 0L)
        require(maxTapTravelFraction >= 0f)
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
