package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRescueAdmissionPolicyTest {
    @Test fun noRequestDoesNothing() {
        assertEquals(
            PlaybackRescueLane.NONE,
            PlaybackRescueAdmissionPolicy.decide(
                PlaybackRescueAdmissionInput(false, true, false, false, false)
            )
        )
    }

    @Test fun softwareVideoNeedsRealAvailability() {
        assertEquals(
            PlaybackRescueLane.NONE,
            PlaybackRescueAdmissionPolicy.decide(
                PlaybackRescueAdmissionInput(true, false, false, false, false)
            )
        )
    }

    @Test fun softwareVideoRequestSelectsVideoLane() {
        assertEquals(
            PlaybackRescueLane.SOFTWARE_VIDEO,
            PlaybackRescueAdmissionPolicy.decide(
                PlaybackRescueAdmissionInput(true, true, false, false, false)
            )
        )
    }

    @Test fun ffmpegAudioRequestSelectsAudioLane() {
        assertEquals(
            PlaybackRescueLane.FFMPEG_AUDIO,
            PlaybackRescueAdmissionPolicy.decide(
                PlaybackRescueAdmissionInput(false, false, false, true, false)
            )
        )
    }

    @Test fun simultaneousIndependentRequestsBecomeMixedRescue() {
        assertEquals(
            PlaybackRescueLane.MIXED,
            PlaybackRescueAdmissionPolicy.decide(
                PlaybackRescueAdmissionInput(true, true, false, true, false)
            )
        )
    }

    @Test fun alreadyActiveLanesCannotLoop() {
        assertEquals(
            PlaybackRescueLane.NONE,
            PlaybackRescueAdmissionPolicy.decide(
                PlaybackRescueAdmissionInput(true, true, true, true, true)
            )
        )
    }
}
