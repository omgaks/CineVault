package com.sole.cinevault.glasses.halo

/**
 * Central Halo interaction stream.
 *
 * D4-10 promotes focus confidence into the canonical interaction frame.
 * Confidence remains descriptive only: it never clicks, snaps, captures
 * Compose focus, or changes drag/click ownership.
 */
class HaloInteractionCoordinator(
    private val motionEngine: HaloMotionEngine = HaloMotionEngine(),
    private val clickController: HaloClickController = HaloClickController(),
    private val dragController: HaloDragController = HaloDragController(),
    private val stabilityEngine: HaloTargetStabilityEngine = HaloTargetStabilityEngine(),
    private val focusConfidenceController: HaloFocusConfidenceController =
        HaloFocusConfidenceController(),
) {
    private var lastPressed = false
    private var lastStability = inactiveStability()

    fun update(
        sample: HaloPointerSample,
        eventTimeMillis: Long,
        deltaTimeMillis: Long,
    ): HaloInteractionFrame {
        val position = motionEngine.update(
            rawXFraction = sample.xFraction,
            rawYFraction = sample.yFraction,
            deltaTimeMillis = deltaTimeMillis,
        )

        val pressStarted = sample.pressed && !lastPressed
        val precisionAtPressDown = pressStarted && lastStability.isStable

        val dragEvents = dragController.update(
            position = position,
            pressed = sample.pressed,
        )

        val dragOwnsGesture = dragController.isDragging() ||
            dragEvents.any {
                it.type == HaloDragEventType.DRAG_END ||
                    it.type == HaloDragEventType.DRAG_CANCEL
            }

        val clickEvents = if (dragOwnsGesture) {
            clickController.cancel()
            emptyList()
        } else {
            clickController.update(
                position = position,
                pressed = sample.pressed,
                eventTimeMillis = eventTimeMillis,
                precisionStable = precisionAtPressDown,
            )
        }

        val stability = if (dragOwnsGesture) {
            stabilityEngine.reset(position)
            inactiveStability()
        } else {
            stabilityEngine.update(
                position = position,
                pressed = sample.pressed,
                eventTimeMillis = eventTimeMillis,
            )
        }
        lastStability = stability

        val focusConfidence = focusConfidenceController.evaluate(
            stability = stability,
            pressed = sample.pressed,
            dragging = dragOwnsGesture,
        )

        val moved = position.x != sample.xFraction ||
            position.y != sample.yFraction ||
            sample.pressed

        val pressChanged = sample.pressed != lastPressed
        lastPressed = sample.pressed

        val reasons = buildSet {
            if (moved) add(HaloActivityReason.POINTER)
            if (pressChanged) add(HaloActivityReason.TOUCH)
            if (clickEvents.any { it.type == HaloClickEventType.CLICK }) {
                add(HaloActivityReason.CLICK)
            }
            if (dragEvents.isNotEmpty()) add(HaloActivityReason.DRAG)
        }

        return HaloInteractionFrame(
            position = position,
            clickEvents = clickEvents,
            dragEvents = dragEvents,
            stability = stability,
            focusConfidence = focusConfidence,
            activity = reasons.takeIf { it.isNotEmpty() }?.let {
                HaloActivityEvent(
                    eventTimeMillis = eventTimeMillis,
                    reasons = it,
                )
            },
        )
    }

    /** Re-anchor every stateful engine after display/orientation/session changes. */
    fun reset(position: HaloVector? = null) {
        motionEngine.reset(position)
        clickController.cancel()
        dragController.cancel()
        stabilityEngine.reset(position)
        lastStability = inactiveStability()
        lastPressed = false
    }

    private fun inactiveStability() = HaloTargetStability(
        isStable = false,
        dwellMillis = 0L,
        distanceFromAnchor = 0f,
    )
}

data class HaloInteractionFrame(
    val position: HaloVector,
    val clickEvents: List<HaloClickEvent>,
    val dragEvents: List<HaloDragEvent>,
    val stability: HaloTargetStability,
    val focusConfidence: HaloFocusConfidence,
    val activity: HaloActivityEvent?,
)

data class HaloActivityEvent(
    val eventTimeMillis: Long,
    val reasons: Set<HaloActivityReason>,
)

enum class HaloActivityReason {
    POINTER,
    TOUCH,
    CLICK,
    DRAG,
}
