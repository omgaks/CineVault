package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloInteractionCoordinatorTest {

    private fun sample(x: Float, y: Float, pressed: Boolean) = HaloPointerSample(
        xPx = x * 1000f,
        yPx = y * 500f,
        xFraction = x,
        yFraction = y,
        pressed = pressed,
    )

    @Test fun touchProducesActivityForAutoHideIntegration() {
        val coordinator = HaloInteractionCoordinator()
        val frame = coordinator.update(sample(0.5f, 0.5f, true), 1000L, 16L)
        assertNotNull(frame.activity)
        assertTrue(HaloActivityReason.TOUCH in frame.activity!!.reasons)
    }

    @Test fun clickProducesClickActivity() {
        val coordinator = HaloInteractionCoordinator()
        coordinator.update(sample(0.5f, 0.5f, true), 1000L, 16L)
        val frame = coordinator.update(sample(0.5f, 0.5f, false), 1100L, 16L)
        assertTrue(frame.clickEvents.any { it.type == HaloClickEventType.CLICK })
        assertTrue(HaloActivityReason.CLICK in frame.activity!!.reasons)
    }

    @Test fun dragWinsAndPreventsAccidentalClick() {
        val coordinator = HaloInteractionCoordinator()
        coordinator.update(sample(0.2f, 0.2f, true), 1000L, 16L)
        val dragFrame = coordinator.update(sample(0.4f, 0.2f, true), 1050L, 16L)
        val releaseFrame = coordinator.update(sample(0.4f, 0.2f, false), 1100L, 16L)

        assertTrue(dragFrame.dragEvents.isNotEmpty())
        assertTrue(HaloActivityReason.DRAG in dragFrame.activity!!.reasons)
        assertFalse(releaseFrame.clickEvents.any { it.type == HaloClickEventType.CLICK })
    }

    @Test fun settledPointerExposesPrecisionStabilityState() {
        val coordinator = HaloInteractionCoordinator()
        coordinator.update(sample(0.5f, 0.5f, false), 1000L, 16L)
        val frame = coordinator.update(sample(0.5f, 0.5f, false), 1150L, 16L)
        assertTrue(frame.stability.isStable)
    }

    @Test fun pressedPointerCannotRemainVisiblyStable() {
        val coordinator = HaloInteractionCoordinator()
        coordinator.update(sample(0.5f, 0.5f, false), 1000L, 16L)
        coordinator.update(sample(0.5f, 0.5f, false), 1150L, 16L)
        val frame = coordinator.update(sample(0.5f, 0.5f, true), 1160L, 16L)
        assertFalse(frame.stability.isStable)
    }

    @Test fun stableBeforePressUsesPrecisionClickEnvelope() {
        val coordinator = HaloInteractionCoordinator()
        coordinator.update(sample(0.50f, 0.5f, false), 1000L, 16L)
        coordinator.update(sample(0.50f, 0.5f, false), 1150L, 16L)

        coordinator.update(sample(0.50f, 0.5f, true), 1160L, 16L)
        val release = coordinator.update(sample(0.52f, 0.5f, false), 1200L, 16L)

        assertFalse(release.clickEvents.any { it.type == HaloClickEventType.CLICK })
    }

    @Test fun nonStablePressKeepsStandardClickEnvelope() {
        val coordinator = HaloInteractionCoordinator()
        coordinator.update(sample(0.50f, 0.5f, true), 1000L, 16L)

        // Keep the raw travel inside the standard click envelope after the
        // motion engine applies acceleration/smoothing. This test is about
        // standard-vs-precision click ownership, not motion gain.
        val release = coordinator.update(sample(0.51f, 0.5f, false), 1100L, 16L)

        assertTrue(release.clickEvents.any { it.type == HaloClickEventType.CLICK })
    }

    @Test fun activeDragCancelsPrecisionStability() {
        val coordinator = HaloInteractionCoordinator()
        coordinator.update(sample(0.2f, 0.2f, false), 1000L, 16L)
        coordinator.update(sample(0.2f, 0.2f, false), 1150L, 16L)
        coordinator.update(sample(0.2f, 0.2f, true), 1160L, 16L)
        val frame = coordinator.update(sample(0.45f, 0.2f, true), 1200L, 16L)

        assertTrue(frame.dragEvents.isNotEmpty())
        assertFalse(frame.stability.isStable)
    }

    @Test fun resetClearsPendingGestureOwnershipAndStability() {
        val coordinator = HaloInteractionCoordinator()
        coordinator.update(sample(0.5f, 0.5f, true), 1000L, 16L)
        coordinator.reset(HaloVector(0.5f, 0.5f))
        val frame = coordinator.update(sample(0.5f, 0.5f, false), 1100L, 16L)

        assertFalse(frame.clickEvents.any { it.type == HaloClickEventType.CLICK })
        assertFalse(frame.stability.isStable)
    }
}
