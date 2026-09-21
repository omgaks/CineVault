package com.sole.cinevault.glasses.halo

/**
 * Central Halo interaction stream.
 *
 * D4-3 integrates the D4 target-stability engine without changing click/drag
 * ownership. Stability is descriptive UI state only: it never auto-clicks,
 * snaps, or steals an active gesture.
 */
class HaloInteractionCoordinator(
    private val motionEngine: HaloMotionEngine = HaloMotionEngine(),
    private val clickController: HaloClickController = HaloClickController(),
    private val dragController: HaloDragController = HaloDragController(),
    private val stabilityEngine: HaloTargetStabilityEngine = HaloTargetStabilityEngine(),
) {
    private var lastPressed = false

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
            )
        }

        val stability = if (dragOwnsGesture) {
            stabilityEngine.reset(position)
            HaloTargetStability(
                isStable = false,
                dwellMillis = 0L,
                distanceFromAnchor = 0f,
            )
        } else {
            stabilityEngine.update(
                position = position,
                pressed = sample.pressed,
                eventTimeMillis = eventTimeMillis,
            )
        }

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
        lastPressed = false
    }
}

data class HaloInteractionFrame(
    val position: HaloVector,
    val clickEvents: List<HaloClickEvent>,
    val dragEvents: List<HaloDragEvent>,
    val stability: HaloTargetStability,
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
