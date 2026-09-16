package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRuntimeRescuePlaybackSnapshotTest {

    @Test
    fun validPlaybackStateIsPreserved() {
        val snapshot = AudioRuntimeRescuePlaybackSnapshot(
            resumePositionMs = 90_000L,
            playWhenReady = true,
            playbackSpeed = 1.25f,
            volume = 0.55f,
        ).normalized()

        assertEquals(90_000L, snapshot.resumePositionMs)
        assertEquals(true, snapshot.playWhenReady)
        assertEquals(1.25f, snapshot.playbackSpeed, 0.0f)
        assertEquals(0.55f, snapshot.volume, 0.0f)
    }

    @Test
    fun invalidPlaybackStateIsNormalizedAtHandoverBoundary() {
        val snapshot = AudioRuntimeRescuePlaybackSnapshot(
            resumePositionMs = -5_000L,
            playWhenReady = false,
            playbackSpeed = 99.0f,
            volume = -2.0f,
        ).normalized()

        assertEquals(0L, snapshot.resumePositionMs)
        assertEquals(false, snapshot.playWhenReady)
        assertEquals(4.0f, snapshot.playbackSpeed, 0.0f)
        assertEquals(0.0f, snapshot.volume, 0.0f)
    }

    @Test
    fun lowerPlaybackSpeedAndUpperVolumeAreClamped() {
        val snapshot = AudioRuntimeRescuePlaybackSnapshot(
            resumePositionMs = 1L,
            playWhenReady = true,
            playbackSpeed = 0.01f,
            volume = 5.0f,
        ).normalized()

        assertEquals(0.25f, snapshot.playbackSpeed, 0.0f)
        assertEquals(1.0f, snapshot.volume, 0.0f)
    }
}
