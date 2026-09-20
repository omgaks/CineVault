package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloPlayerLiveSessionFactoryTest {
    @Test
    fun factoryPreservesCanonicalCallbacks() {
        var brightness = 0f
        var volume = 0f
        var seek = -1L
        var activity = 0

        val session = HaloPlayerLiveSessionFactory.create(
            brightnessDrag = { brightness = it },
            volumeDrag = { volume = it },
            seekTo = { seek = it },
            userActivity = { activity++ },
        )

        session.onDragStart(100f, 700f, 1000, 1000, 20_000L, 100_000L)
        session.onDrag(100f, 500f, 1000, 1000)
        session.onDragEnd()
        assertEquals(-200f, brightness, 0.01f)

        session.onDragStart(900f, 700f, 1000, 1000, 20_000L, 100_000L)
        session.onDrag(900f, 500f, 1000, 1000)
        session.onDragEnd()
        assertEquals(-200f, volume, 0.01f)

        session.onDragStart(300f, 150f, 1000, 1000, 20_000L, 100_000L)
        session.onDrag(500f, 150f, 1000, 1000)
        session.onDragEnd()
        assertEquals(40_000L, seek)

        // begin + update + end for each of three sessions.
        assertEquals(9, activity)
    }
}
