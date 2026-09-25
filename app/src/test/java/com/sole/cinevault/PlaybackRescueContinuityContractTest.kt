package com.sole.cinevault

import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRescueContinuityContractTest {
    @Test fun everyRescueLaneRequiresFullPlaybackContinuity() {
        PlaybackRescueLane.entries.forEach { lane ->
            val contract = PlaybackRescueContinuityPolicy.forLane(lane)
            assertTrue("$lane must preserve position", contract.preservesPosition)
            assertTrue("$lane must preserve play state", contract.preservesPlayState)
            assertTrue("$lane must preserve speed", contract.preservesPlaybackSpeed)
            assertTrue("$lane must preserve volume", contract.preservesVolume)
            assertTrue("$lane must preserve audio track", contract.preservesAudioTrack)
            assertTrue("$lane must preserve subtitle", contract.preservesSubtitle)
        }
    }

    @Test fun mixedRescueHasNoReducedContinuityContract() {
        val mixed = PlaybackRescueContinuityPolicy.forLane(PlaybackRescueLane.MIXED)
        assertTrue(mixed.preservesPosition)
        assertTrue(mixed.preservesPlayState)
        assertTrue(mixed.preservesPlaybackSpeed)
        assertTrue(mixed.preservesVolume)
        assertTrue(mixed.preservesAudioTrack)
        assertTrue(mixed.preservesSubtitle)
    }
}
