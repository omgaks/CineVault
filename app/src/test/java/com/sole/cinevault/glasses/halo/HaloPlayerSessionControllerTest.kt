package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerSessionControllerTest {

    @Test
    fun landscapeLeftTwentyPercentRoutesBrightness() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        val zone =
            session.beginDrag(
                startX = 240f,
                startY = 700f,
                surfaceWidthPx = 1600,
                surfaceHeightPx = 900,
                playbackPositionMs = 20_000L,
                durationMs = 100_000L,
            )

        assertEquals(HaloPlayerGestureZone.BRIGHTNESS, zone)

        session.updateDrag(
            currentX = 240f,
            currentY = 550f,
            surfaceWidthPx = 1600,
            surfaceHeightPx = 900,
        )

        assertTrue(actions.brightnessDelta < 0f)
    }

    @Test
    fun portraitLeftTwentyPercentRoutesBrightness() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        val zone =
            session.beginDrag(
                startX = 120f,
                startY = 900f,
                surfaceWidthPx = 800,
                surfaceHeightPx = 1200,
                playbackPositionMs = 20_000L,
                durationMs = 100_000L,
            )

        assertEquals(HaloPlayerGestureZone.BRIGHTNESS, zone)

        session.updateDrag(
            currentX = 120f,
            currentY = 700f,
            surfaceWidthPx = 800,
            surfaceHeightPx = 1200,
        )

        assertTrue(actions.brightnessDelta < 0f)
    }

    @Test
    fun landscapeRightTwentyPercentRoutesVolume() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        val zone =
            session.beginDrag(
                startX = 1440f,
                startY = 700f,
                surfaceWidthPx = 1600,
                surfaceHeightPx = 900,
                playbackPositionMs = 0L,
                durationMs = 100_000L,
            )

        assertEquals(HaloPlayerGestureZone.VOLUME, zone)

        session.updateDrag(
            currentX = 1440f,
            currentY = 550f,
            surfaceWidthPx = 1600,
            surfaceHeightPx = 900,
        )

        assertEquals(1, actions.volumeCalls)
    }

    @Test
    fun portraitRightTwentyPercentRoutesVolume() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        val zone =
            session.beginDrag(
                startX = 720f,
                startY = 900f,
                surfaceWidthPx = 800,
                surfaceHeightPx = 1200,
                playbackPositionMs = 0L,
                durationMs = 100_000L,
            )

        assertEquals(HaloPlayerGestureZone.VOLUME, zone)

        session.updateDrag(
            currentX = 720f,
            currentY = 700f,
            surfaceWidthPx = 800,
            surfaceHeightPx = 1200,
        )

        assertEquals(1, actions.volumeCalls)
    }

    @Test
    fun landscapeTopHalfHorizontalRoutesSeek() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        val zone =
            session.beginDrag(
                startX = 800f,
                startY = 300f,
                surfaceWidthPx = 1600,
                surfaceHeightPx = 900,
                playbackPositionMs = 40_000L,
                durationMs = 100_000L,
            )

        assertEquals(HaloPlayerGestureZone.SEEK, zone)

        session.updateDrag(
            currentX = 1120f,
            currentY = 300f,
            surfaceWidthPx = 1600,
            surfaceHeightPx = 900,
        )

        assertEquals(60_000L, actions.seekPositionMs)
    }

    @Test
    fun portraitTopHalfHorizontalRoutesSeek() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        val zone =
            session.beginDrag(
                startX = 400f,
                startY = 300f,
                surfaceWidthPx = 800,
                surfaceHeightPx = 1200,
                playbackPositionMs = 40_000L,
                durationMs = 100_000L,
            )

        assertEquals(HaloPlayerGestureZone.SEEK, zone)

        session.updateDrag(
            currentX = 560f,
            currentY = 300f,
            surfaceWidthPx = 800,
            surfaceHeightPx = 1200,
        )

        assertEquals(60_000L, actions.seekPositionMs)
    }

    @Test
    fun topLeftVerticalStillRoutesBrightnessInPortrait() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        session.beginDrag(
            startX = 80f,
            startY = 240f,
            surfaceWidthPx = 800,
            surfaceHeightPx = 1200,
            playbackPositionMs = 0L,
            durationMs = 100_000L,
        )

        val intent =
            session.updateDrag(
                currentX = 80f,
                currentY = 120f,
                surfaceWidthPx = 800,
                surfaceHeightPx = 1200,
            )

        assertTrue(intent is HaloPlayerGestureIntent.Brightness)
    }

    @Test
    fun topLeftHorizontalStillRoutesSeekInPortrait() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        session.beginDrag(
            startX = 80f,
            startY = 240f,
            surfaceWidthPx = 800,
            surfaceHeightPx = 1200,
            playbackPositionMs = 20_000L,
            durationMs = 100_000L,
        )

        val intent =
            session.updateDrag(
                currentX = 240f,
                currentY = 240f,
                surfaceWidthPx = 800,
                surfaceHeightPx = 1200,
            )

        assertTrue(intent is HaloPlayerGestureIntent.Seek)
    }


    @Test
    fun activeDragCancelsWhenSurfaceGeometryChanges() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        session.beginDrag(
            startX = 400f,
            startY = 300f,
            surfaceWidthPx = 800,
            surfaceHeightPx = 1200,
            playbackPositionMs = 40_000L,
            durationMs = 100_000L,
        )

        assertTrue(session.isActive())

        val result =
            session.updateDrag(
                currentX = 900f,
                currentY = 300f,
                surfaceWidthPx = 1600,
                surfaceHeightPx = 900,
            )

        assertNull(result)
        assertFalse(session.isActive())
        assertNull(session.activeZone())
        assertEquals(-1L, actions.seekPositionMs)
    }

    @Test
    fun resizedSurfaceCanStartFreshGestureAfterInvalidation() {
        val actions = RecordingActions()
        val session = HaloPlayerSessionController(actions)

        session.beginDrag(
            startX = 400f,
            startY = 300f,
            surfaceWidthPx = 800,
            surfaceHeightPx = 1200,
            playbackPositionMs = 40_000L,
            durationMs = 100_000L,
        )

        session.updateDrag(
            currentX = 900f,
            currentY = 300f,
            surfaceWidthPx = 1600,
            surfaceHeightPx = 900,
        )

        assertFalse(session.isActive())

        val zone =
            session.beginDrag(
                startX = 800f,
                startY = 300f,
                surfaceWidthPx = 1600,
                surfaceHeightPx = 900,
                playbackPositionMs = 40_000L,
                durationMs = 100_000L,
            )

        assertEquals(HaloPlayerGestureZone.SEEK, zone)

        val intent =
            session.updateDrag(
                currentX = 1120f,
                currentY = 300f,
                surfaceWidthPx = 1600,
                surfaceHeightPx = 900,
            )

        assertTrue(intent is HaloPlayerGestureIntent.Seek)
        assertEquals(60_000L, actions.seekPositionMs)
    }

    @Test
    fun invalidSurfaceDoesNotStartSession() {
        val session = HaloPlayerSessionController(RecordingActions())

        val zone =
            session.beginDrag(
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

        val result =
            session.updateDrag(
                currentX = 900f,
                currentY = 400f,
                surfaceWidthPx = 1000,
                surfaceHeightPx = 1000,
            )

        assertNull(result)
        assertEquals(0, actions.volumeCalls)
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
