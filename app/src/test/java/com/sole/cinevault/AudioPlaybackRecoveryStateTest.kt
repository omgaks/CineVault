package com.sole.cinevault

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaybackRecoveryStateTest {

    @Test
    fun eligibleAudioFailureCanRequestFfmpegBeforeAttempt() {
        val state = PlayerPlaybackRecoveryState()
        state.updateStreamInventory(
            PlaybackStreamInventory(
                streams = listOf(
                    PlaybackStreamDescriptor(
                        kind = PlaybackStreamKind.AUDIO,
                        mimeType = "audio/vnd.dts",
                        codecString = "dts",
                        language = "eng",
                        selected = true,
                    )
                )
            )
        )

        val decision = state.decideAudioRecovery(audioFailure())

        assertEquals(
            AudioPlaybackRecoveryAction.SWITCH_TO_FFMPEG,
            decision.action,
        )
        assertFalse(state.audioFfmpegRescueAttempted)
    }

    @Test
    fun markingAttemptPreventsSecondFfmpegRequest() {
        val state = PlayerPlaybackRecoveryState()
        state.updateStreamInventory(
            PlaybackStreamInventory(
                streams = listOf(
                    PlaybackStreamDescriptor(
                        kind = PlaybackStreamKind.AUDIO,
                        mimeType = "audio/true-hd",
                        codecString = "mlpa",
                        language = "eng",
                        selected = true,
                    )
                )
            )
        )

        assertEquals(
            AudioPlaybackRecoveryAction.SWITCH_TO_FFMPEG,
            state.decideAudioRecovery(audioFailure()).action,
        )

        state.markAudioFfmpegRescueAttempted()

        assertTrue(state.audioFfmpegRescueAttempted)
        assertEquals(
            AudioPlaybackRecoveryAction.FAIL,
            state.decideAudioRecovery(audioFailure()).action,
        )
    }

    @Test
    fun resetForNewVideoClearsAudioFfmpegAttemptGuard() {
        val state = PlayerPlaybackRecoveryState()
        state.markAudioFfmpegRescueAttempted()

        state.resetForNewVideo()

        assertFalse(state.audioFfmpegRescueAttempted)
    }

    @Test
    fun videoFailureDoesNotEnterAudioRecoveryFromState() {
        val state = PlayerPlaybackRecoveryState()

        val decision = state.decideAudioRecovery(
            PlaybackFailureAttribution(
                streamKind = PlaybackFailureStreamKind.VIDEO,
                rendererFailure = true,
                rendererTrackType = C.TRACK_TYPE_VIDEO,
                errorCode = 4003,
            )
        )

        assertEquals(AudioPlaybackRecoveryAction.NONE, decision.action)
    }

    private fun audioFailure() = PlaybackFailureAttribution(
        streamKind = PlaybackFailureStreamKind.AUDIO,
        rendererFailure = true,
        rendererTrackType = C.TRACK_TYPE_AUDIO,
        errorCode = 4003,
    )
}
