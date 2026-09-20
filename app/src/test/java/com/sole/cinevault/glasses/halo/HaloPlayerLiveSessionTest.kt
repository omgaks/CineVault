package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerLiveSessionTest {

    @Test
    fun liveBrightnessPathReachesCanonicalCallback() {
        var delta = 0f
        val session = session(brightness = { delta = it })

        session.onDragStart(100f, 700f, 1000, 1000, 20_000L, 100_000L)
        session.onDrag(100f, 500f, 1000, 1000)

        assertEquals(-200f, delta, 0.01f)
    }

    @Test
    fun liveVolumePathReachesCanonicalCallback() {
        var delta = 0f
        val session = session(volume = { delta = it })

        session.onDragStart(900f, 700f, 1000, 1000, 20_000L, 100_000L)
        session.onDrag(900f, 500f, 1000, 1000)

        assertEquals(-200f, delta, 0.01f)
    }

    @Test
    fun liveSeekPathReachesCanonicalCallback() {
        var target = -1L
        val session = session(seek = { target = it })

        session.onDragStart(300f, 150f, 1000, 1000, 20_000L, 100_000L)
        session.onDrag(500f, 150f, 1000, 1000)

        assertEquals(40_000L, target)
    }

    @Test
    fun activityIsBumpedWhileHaloGestureIsUsed() {
        var bumps = 0
        val session = session(activity = { bumps++ })

        session.onDragStart(900f, 700f, 1000, 1000, 0L, 100_000L)
        session.onDrag(900f, 600f, 1000, 1000)
        session.onDragEnd()

        assertEquals(3, bumps)
    }

    @Test
    fun resetKillsLiveGestureOwnership() {
        var volumeCalls = 0
        val session = session(volume = { volumeCalls++ })

        session.onDragStart(900f, 700f, 1000, 1000, 0L, 100_000L)
        assertTrue(session.isActive())

        session.reset()
        assertFalse(session.isActive())

        session.onDrag(900f, 400f, 1000, 1000)
        assertEquals(0, volumeCalls)
    }

    private fun session(
        brightness: (Float) -> Unit = {},
        volume: (Float) -> Unit = {},
        seek: (Long) -> Unit = {},
        activity: () -> Unit = {},
    ) = HaloPlayerLiveSession(
        brightnessDrag = brightness,
        volumeDrag = volume,
        seekTo = seek,
        userActivity = activity,
    )
}
