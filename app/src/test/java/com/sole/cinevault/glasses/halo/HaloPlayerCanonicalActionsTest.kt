package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloPlayerCanonicalActionsTest {

    @Test
    fun forwardsBrightnessToCanonicalCallback() {
        var received = 0f
        val actions = actions(brightness = { received = it })

        actions.onBrightnessDrag(-42f)

        assertEquals(-42f, received, 0.001f)
    }

    @Test
    fun forwardsVolumeToCanonicalCallback() {
        var received = 0f
        val actions = actions(volume = { received = it })

        actions.onVolumeDrag(18f)

        assertEquals(18f, received, 0.001f)
    }

    @Test
    fun forwardsSeekAndClampsNegativePosition() {
        var received = -1L
        val actions = actions(seek = { received = it })

        actions.onSeekTo(-500L)

        assertEquals(0L, received)
    }

    @Test
    fun forwardsUserActivityWithoutInventingVisibilityState() {
        var calls = 0
        val actions = actions(activity = { calls++ })

        actions.onUserActivity()
        actions.onUserActivity()

        assertEquals(2, calls)
    }

    private fun actions(
        brightness: (Float) -> Unit = {},
        volume: (Float) -> Unit = {},
        seek: (Long) -> Unit = {},
        activity: () -> Unit = {},
    ) = HaloPlayerCanonicalActions(
        brightnessDrag = brightness,
        volumeDrag = volume,
        seekTo = seek,
        userActivity = activity,
    )
}
