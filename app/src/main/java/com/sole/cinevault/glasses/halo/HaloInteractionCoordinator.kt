package com.sole.cinevault.glasses.halo

/**
 * D2-7 — first Halo integration layer.
 *
 * Brings D2-4 (motion), D2-5 (click) and D2-6 (drag) into one interaction
 * stream and, critically, emits ACTIVITY for every meaningful Halo operation.
 *
 * Player/UI code can use ACTIVITY to restart its existing controls auto-hide
 * timer. Halo therefore counts as real user interaction instead of allowing
 * controls to disappear while the user is operating them.
 *
 * This coordinator does not render a new player and does not own CineVault
 * controls. It only describes canonical interaction intent.
 */
class HaloInteractionCoordinator(
    private val motionEngine: HaloMotionEngine = HaloMotionEngine(),
    private val clickController: HaloClickController = HaloClickController(),
    private val dragController: HaloDragController = HaloDragController(),
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
            // Once drag wins, a release must never become a click.
            clickController.cancel()
            emptyList()
        } else {
            clickController.update(
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
            activity = reasons.takeIf { it.isNotEmpty() }?.let {
                HaloActivityEvent(
                    eventTimeMillis = eventTimeMillis,
                    reasons = it,
                )
            },
        )
    }

    /**
     * Re-anchor after display/orientation/session changes so Halo does not jump.
     */
    fun reset(position: HaloVector? = null) {
        motionEngine.reset(position)
        clickController.cancel()
        dragController.cancel()
        lastPressed = false
    }
}

data class HaloInteractionFrame(
    val position: HaloVector,
    val clickEvents: List<HaloClickEvent>,
    val dragEvents: List<HaloDragEvent>,
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
