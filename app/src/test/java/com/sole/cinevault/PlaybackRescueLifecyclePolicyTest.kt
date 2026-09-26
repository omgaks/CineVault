package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRescueLifecyclePolicyTest {
    private fun snapshot(
        softwareRequested: Boolean = false,
        softwareActive: Boolean = false,
        softwareOccurred: Boolean = false,
        audioAttempted: Boolean = false,
        audioActive: Boolean = false,
    ) = PlaybackRescueLifecycleSnapshot(
        softwareVideoRequested = softwareRequested,
        softwareVideoActive = softwareActive,
        softwareVideoOccurred = softwareOccurred,
        ffmpegAudioAttempted = audioAttempted,
        ffmpegAudioActive = audioActive,
    )

    @Test fun cleanSessionAllowsBothRescueLanes() {
        val state = snapshot()
        assertTrue(PlaybackRescueLifecyclePolicy.allowsSoftwareVideoRequest(state))
        assertTrue(PlaybackRescueLifecyclePolicy.allowsFfmpegAudioRequest(state))
        assertTrue(PlaybackRescueLifecyclePolicy.isCleanNewMediaScope(state))
    }

    @Test fun pendingVideoRequestCannotReenter() {
        assertFalse(
            PlaybackRescueLifecyclePolicy.allowsSoftwareVideoRequest(
                snapshot(softwareRequested = true)
            )
        )
    }

    @Test fun activeOrCompletedSoftwareVideoCannotReenter() {
        assertFalse(
            PlaybackRescueLifecyclePolicy.allowsSoftwareVideoRequest(
                snapshot(softwareActive = true)
            )
        )
        assertFalse(
            PlaybackRescueLifecyclePolicy.allowsSoftwareVideoRequest(
                snapshot(softwareOccurred = true)
            )
        )
    }

    @Test fun attemptedOrActiveFfmpegAudioCannotReenter() {
        assertFalse(
            PlaybackRescueLifecyclePolicy.allowsFfmpegAudioRequest(
                snapshot(audioAttempted = true)
            )
        )
        assertFalse(
            PlaybackRescueLifecyclePolicy.allowsFfmpegAudioRequest(
                snapshot(audioActive = true)
            )
        )
    }

    @Test fun mixedRescueIsNotCleanNewMediaScope() {
        assertFalse(
            PlaybackRescueLifecyclePolicy.isCleanNewMediaScope(
                snapshot(
                    softwareActive = true,
                    softwareOccurred = true,
                    audioAttempted = true,
                    audioActive = true,
                )
            )
        )
    }
}
