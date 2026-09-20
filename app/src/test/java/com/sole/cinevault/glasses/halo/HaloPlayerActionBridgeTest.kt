package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloPlayerActionBridgeTest {

    @Test
    fun leftZoneUsesExistingBrightnessDragDirection() {
        val actions = RecordingActions()
        val bridge = HaloPlayerActionBridge(actions)

        bridge.begin(
            start = HaloVector(0.10f, 0.70f),
            viewportHeightPx = 1000,
            playbackPositionMs = 0L,
            durationMs = 100_000L,
        )
        bridge.update(HaloVector(0.10f, 0.50f))

        assertEquals(-200f, actions.brightnessDelta, 0.01f)
        assertEquals(0, actions.volumeCalls)
    }

    @Test
    fun rightZoneUsesExistingVolumeDragDirection() {
        val actions = RecordingActions()
        val bridge = HaloPlayerActionBridge(actions)

        bridge.begin(
            start = HaloVector(0.90f, 0.70f),
            viewportHeightPx = 800,
            playbackPositionMs = 0L,
            durationMs = 100_000L,
        )
        bridge.update(HaloVector(0.90f, 0.45f))

        assertEquals(-200f, actions.volumeDelta, 0.01f)
        assertEquals(0, actions.brightnessCalls)
    }

    @Test
    fun topZoneSeeksRelativeToPositionAtGestureStart() {
        val actions = RecordingActions()
        val bridge = HaloPlayerActionBridge(actions)

        bridge.begin(
            start = HaloVector(0.30f, 0.15f),
            viewportHeightPx = 1000,
            playbackPositionMs = 30_000L,
            durationMs = 100_000L,
        )
        bridge.update(HaloVector(0.50f, 0.15f))

        assertEquals(50_000L, actions.seekPositionMs)
    }

    @Test
    fun seekClampsAtMediaBounds() {
        assertEquals(
            100_000L,
            HaloSeekProjection.targetPositionMs(
                startPositionMs = 90_000L,
                durationMs = 100_000L,
                horizontalDeltaFraction = 0.50f,
            ),
        )
        assertEquals(
            0L,
            HaloSeekProjection.targetPositionMs(
                startPositionMs = 10_000L,
                durationMs = 100_000L,
                horizontalDeltaFraction = -0.50f,
            ),
        )
    }

    @Test
    fun canonicalCentreDoesNotInvokePlayerAdjustmentActions() {
        val actions = RecordingActions()
        val bridge = HaloPlayerActionBridge(actions)

        val zone = bridge.begin(
            start = HaloVector(0.50f, 0.60f),
            viewportHeightPx = 1000,
            playbackPositionMs = 40_000L,
            durationMs = 100_000L,
        )
        val intent = bridge.update(HaloVector(0.60f, 0.70f))

        assertEquals(HaloPlayerGestureZone.CANONICAL_UI, zone)
        assertTrue(intent is HaloPlayerGestureIntent.CanonicalUi)
        assertEquals(0, actions.brightnessCalls)
        assertEquals(0, actions.volumeCalls)
        assertEquals(0, actions.seekCalls)
    }

    @Test
    fun everyActivePlayerGestureBumpsExistingActivityPath() {
        val actions = RecordingActions()
        val bridge = HaloPlayerActionBridge(actions)

        bridge.begin(
            start = HaloVector(0.90f, 0.60f),
            viewportHeightPx = 1000,
            playbackPositionMs = 0L,
            durationMs = 100_000L,
        )
        bridge.update(HaloVector(0.90f, 0.50f))
        bridge.end()

        assertEquals(3, actions.activityCalls)
    }

    private class RecordingActions : HaloCanonicalPlayerActions {
        var brightnessCalls = 0
        var volumeCalls = 0
        var seekCalls = 0
        var activityCalls = 0
        var brightnessDelta = 0f
        var volumeDelta = 0f
        var seekPositionMs = -1L

        override fun onBrightnessDrag(deltaYPx: Float) {
            brightnessCalls++
            brightnessDelta = deltaYPx
        }

        override fun onVolumeDrag(deltaYPx: Float) {
            volumeCalls++
            volumeDelta = deltaYPx
        }

        override fun onSeekTo(positionMs: Long) {
            seekCalls++
            seekPositionMs = positionMs
        }

        override fun onUserActivity() {
            activityCalls++
        }
    }
}
