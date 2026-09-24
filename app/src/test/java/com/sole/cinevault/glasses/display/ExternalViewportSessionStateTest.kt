package com.sole.cinevault.glasses.display

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExternalViewportSessionStateTest {

    @Before
    fun setUp() {
        ExternalViewportSessionState.reset()
        ExternalViewportSessionState.updateViewportSize(
            widthPx = 1920,
            heightPx = 1080,
        )
    }

    @After
    fun tearDown() {
        ExternalViewportSessionState.reset()
        ExternalViewportSessionState.updateViewportSize(
            widthPx = 0,
            heightPx = 0,
        )
    }

    @Test
    fun pinchCanReduceViewportToNinetyFivePercentAndKeepsItCentred() {
        ExternalViewportSessionState.applyGesture(
            zoom = 0.95f,
            panX = 300f,
            panY = -200f,
        )

        assertEquals(0.95f, ExternalViewportSessionState.transform.scale, 0.0001f)
        assertEquals(0f, ExternalViewportSessionState.transform.panX, 0.0001f)
        assertEquals(0f, ExternalViewportSessionState.transform.panY, 0.0001f)
    }

    @Test
    fun usefulReductionScalesSnapCleanly() {
        ExternalViewportSessionState.applyGesture(
            zoom = 0.903f,
            panX = 0f,
            panY = 0f,
        )
        assertEquals(0.90f, ExternalViewportSessionState.transform.scale, 0.0001f)

        ExternalViewportSessionState.reset()
        ExternalViewportSessionState.applyGesture(
            zoom = 0.954f,
            panX = 0f,
            panY = 0f,
        )
        assertEquals(0.95f, ExternalViewportSessionState.transform.scale, 0.0001f)

        ExternalViewportSessionState.reset()
        ExternalViewportSessionState.applyGesture(
            zoom = 0.996f,
            panX = 0f,
            panY = 0f,
        )
        assertEquals(1f, ExternalViewportSessionState.transform.scale, 0.0001f)
    }

    @Test
    fun enlargedViewportPanIsClampedToVisibleDisplayBounds() {
        ExternalViewportSessionState.applyGesture(
            zoom = 2f,
            panX = 5000f,
            panY = -5000f,
        )

        // At 2x on 1920x1080, the extra half extents are 960x540.
        assertEquals(2f, ExternalViewportSessionState.transform.scale, 0.0001f)
        assertEquals(960f, ExternalViewportSessionState.transform.panX, 0.0001f)
        assertEquals(-540f, ExternalViewportSessionState.transform.panY, 0.0001f)
    }

    @Test
    fun resizingExternalDisplayReclampsExistingPan() {
        ExternalViewportSessionState.applyGesture(
            zoom = 2f,
            panX = 900f,
            panY = 500f,
        )

        ExternalViewportSessionState.updateViewportSize(
            widthPx = 1000,
            heightPx = 600,
        )

        assertEquals(500f, ExternalViewportSessionState.transform.panX, 0.0001f)
        assertEquals(300f, ExternalViewportSessionState.transform.panY, 0.0001f)
    }

    @Test
    fun viewportScaleIsClampedToSupportedSessionRange() {
        ExternalViewportSessionState.applyGesture(
            zoom = 0.1f,
            panX = 0f,
            panY = 0f,
        )
        assertEquals(
            ExternalViewportSessionState.MIN_SCALE,
            ExternalViewportSessionState.transform.scale,
            0.0001f,
        )

        ExternalViewportSessionState.applyGesture(
            zoom = 100f,
            panX = 0f,
            panY = 0f,
        )
        assertEquals(
            ExternalViewportSessionState.MAX_SCALE,
            ExternalViewportSessionState.transform.scale,
            0.0001f,
        )
    }

    @Test
    fun resetRestoresDefaultViewport() {
        ExternalViewportSessionState.applyGesture(
            zoom = 1.5f,
            panX = 20f,
            panY = 10f,
        )
        ExternalViewportSessionState.reset()

        assertEquals(
            ExternalViewportTransform(),
            ExternalViewportSessionState.transform,
        )
    }
}
