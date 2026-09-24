package com.sole.cinevault.glasses.display

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExternalViewportSessionStateTest {

    @Before
    fun setUp() {
        ExternalViewportSessionState.reset()
    }

    @After
    fun tearDown() {
        ExternalViewportSessionState.reset()
    }

    @Test
    fun pinchCanReduceViewportBelowOneHundredPercent() {
        ExternalViewportSessionState.applyGesture(
            zoom = 0.95f,
            panX = 0f,
            panY = 0f,
        )

        assertEquals(0.95f, ExternalViewportSessionState.transform.scale, 0.0001f)
    }

    @Test
    fun viewportScaleIsClampedToSupportedSessionRange() {
        ExternalViewportSessionState.applyGesture(zoom = 0.1f, panX = 0f, panY = 0f)
        assertEquals(0.75f, ExternalViewportSessionState.transform.scale, 0.0001f)

        ExternalViewportSessionState.applyGesture(zoom = 100f, panX = 0f, panY = 0f)
        assertEquals(3f, ExternalViewportSessionState.transform.scale, 0.0001f)
    }

    @Test
    fun panAccumulatesAcrossGestureUpdates() {
        ExternalViewportSessionState.applyGesture(zoom = 1f, panX = 12f, panY = -8f)
        ExternalViewportSessionState.applyGesture(zoom = 1f, panX = -2f, panY = 3f)

        assertEquals(10f, ExternalViewportSessionState.transform.panX, 0.0001f)
        assertEquals(-5f, ExternalViewportSessionState.transform.panY, 0.0001f)
    }

    @Test
    fun resetRestoresDefaultViewport() {
        ExternalViewportSessionState.applyGesture(zoom = 1.5f, panX = 20f, panY = 10f)
        ExternalViewportSessionState.reset()

        assertEquals(ExternalViewportTransform(), ExternalViewportSessionState.transform)
    }
}
