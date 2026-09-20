package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerSessionControllerTest {

    @Test
    fun pixelCoordinatesAreNormalizedBeforeRouting() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        val zone = session.beginDrag(
            startX = 100f,
            startY = 600f,
            surfaceWidthPx = 1000,
            surfaceHeightPx = 1000,
            playbackPositionMs = 20_000L,
            durationMs = 100_000L,
        )

        assertEquals(HaloPlayerGestureZone.BRIGHTNESS, zone)

        session.updateDrag(
            currentX = 100f,
            currentY = 500f,
            surfaceWidthPx = 1000,
            surfaceHeightPx = 1000,
        )

        assertEquals(-100f, actions.brightnessDelta, 0.01f)
    }

    @Test
    fun topThirtyPercentBecomesSeekSession() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        val zone = session.beginDrag(
            startX = 500f,
            startY = 200f,
            surfaceWidthPx = 1000,
            surfaceHeightPx = 1000,
            playbackPositionMs = 40_000L,
            durationMs = 100_000L,
        )

        assertEquals(HaloPlayerGestureZone.SEEK, zone)

        session.updateDrag(
            currentX = 700f,
            currentY = 200f,
            surfaceWidthPx = 1000,
            surfaceHeightPx = 1000,
        )

        assertEquals(60_000L, actions.seekPositionMs)
    }

    @Test
    fun invalidSurfaceDoesNotStartSession() {
        val session = HaloPlayerSessionController(RecordingActions())

        val zone = session.beginDrag(
            startX = 0f,
            startY = 0f,
            surfaceWidthPx = 0,
            surfaceHeightPx = 1000,
            playbackPositionMs = 0L,
            durationMs = 1L,
        )

        assertNull(zone)
        assertFalse(session.isActive())
    }

    @Test
    fun cancelKillsOwnershipImmediately() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        session.beginDrag(
            startX = 900f,
            startY = 600f,
            surfaceWidthPx = 1000,
            surfaceHeightPx = 1000,
            playbackPositionMs = 0L,
            durationMs = 100_000L,
        )

        assertTrue(session.isActive())
        session.cancel()
        assertFalse(session.isActive())
        assertNull(session.activeZone())

        val result = session.updateDrag(
            currentX = 900f,
            currentY = 400f,
            surfaceWidthPx = 1000,
            surfaceHeightPx = 1000,
        )

        assertNull(result)
        assertEquals(0, actions.volumeCalls)
    }

    @Test
    fun endClosesSession() {
        val session = HaloPlayerSessionController(RecordingActions())

        session.beginDrag(
            startX = 500f,
            startY = 700f,
            surfaceWidthPx = 1000,
            surfaceHeightPx = 1000,
            playbackPositionMs = 0L,
            durationMs = 100_000L,
        )
        session.endDrag()

        assertFalse(session.isActive())
        assertNull(session.activeZone())
    }

    private class RecordingActions : HaloCanonicalPlayerActions {
        var brightnessDelta = 0f
        var volumeCalls = 0
        var seekPositionMs = -1L

        override fun onBrightnessDrag(deltaYPx: Float) {
            brightnessDelta = deltaYPx
        }

        override fun onVolumeDrag(deltaYPx: Float) {
            volumeCalls++
        }

        override fun onSeekTo(positionMs: Long) {
            seekPositionMs = positionMs
        }

        override fun onUserActivity() = Unit
    }
}
