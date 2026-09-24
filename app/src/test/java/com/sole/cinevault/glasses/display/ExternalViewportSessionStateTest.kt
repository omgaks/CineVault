package com.sole.cinevault.glasses.display

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExternalViewportSessionStateTest {

    @Before
    fun setUp() {
        ExternalViewportSessionState.unbindProfile(null)
        ExternalViewportSessionState.reset()
        ExternalViewportSessionState.updateViewportSize(1920, 1080)
    }

    @After
    fun tearDown() {
        ExternalViewportSessionState.unbindProfile(null)
        ExternalViewportSessionState.reset()
        ExternalViewportSessionState.updateViewportSize(0, 0)
    }

    @Test
    fun bindingProfileRestoresSavedViewportThroughSafetyPolicy() {
        ExternalViewportSessionState.bindProfile(
            profileName = "Example Glasses",
            restoredTransform = ExternalViewportTransform(5f, 9999f, -9999f),
            onTransformChanged = null,
        )

        assertEquals(3f, ExternalViewportSessionState.transform.scale, 0.0001f)
        assertEquals(1920f, ExternalViewportSessionState.transform.panX, 0.0001f)
        assertEquals(-1080f, ExternalViewportSessionState.transform.panY, 0.0001f)
    }

    @Test
    fun gestureChangesArePublishedToBoundProfile() {
        var persisted: ExternalViewportTransform? = null
        ExternalViewportSessionState.bindProfile(
            profileName = "Example Glasses",
            restoredTransform = ExternalViewportTransform(scale = 0.95f),
            onTransformChanged = { persisted = it },
        )

        ExternalViewportSessionState.applyGesture(1.2f, 100f, 50f)

        assertEquals(ExternalViewportSessionState.transform, persisted)
    }

    @Test
    fun switchingProfilesRestoresEachDisplaysOwnTransform() {
        ExternalViewportSessionState.bindProfile(
            "Display A",
            ExternalViewportTransform(scale = 0.90f),
            null,
        )
        assertEquals(0.90f, ExternalViewportSessionState.transform.scale, 0.0001f)

        ExternalViewportSessionState.bindProfile(
            "Display B",
            ExternalViewportTransform(scale = 0.95f),
            null,
        )
        assertEquals(0.95f, ExternalViewportSessionState.transform.scale, 0.0001f)
    }

    @Test
    fun reducedViewportStaysCentred() {
        ExternalViewportSessionState.applyGesture(0.95f, 300f, -200f)
        assertEquals(0.95f, ExternalViewportSessionState.transform.scale, 0.0001f)
        assertEquals(0f, ExternalViewportSessionState.transform.panX, 0.0001f)
        assertEquals(0f, ExternalViewportSessionState.transform.panY, 0.0001f)
    }

    @Test
    fun enlargedViewportPanIsClampedToVisibleDisplayBounds() {
        ExternalViewportSessionState.applyGesture(2f, 5000f, -5000f)
        assertEquals(2f, ExternalViewportSessionState.transform.scale, 0.0001f)
        assertEquals(960f, ExternalViewportSessionState.transform.panX, 0.0001f)
        assertEquals(-540f, ExternalViewportSessionState.transform.panY, 0.0001f)
    }
}
