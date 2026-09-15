package com.sole.cinevault

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class PostFfmpegAudioFailurePolicyTest {

    @Test
    fun secondAudioRendererFailureAfterRescueFailsRescue() {
        assertEquals(
            PostFfmpegAudioFailureAction.FAIL_RESCUE,
            decidePostFfmpegAudioFailureAction(
                attribution = audioRendererFailure(),
                ffmpegRescueAttempted = true,
            ),
        )
    }

    @Test
    fun firstAudioFailureStillUsesNormalRecoveryLane() {
        assertEquals(
            PostFfmpegAudioFailureAction.USE_NORMAL_RECOVERY,
            decidePostFfmpegAudioFailureAction(
                attribution = audioRendererFailure(),
                ffmpegRescueAttempted = false,
            ),
        )
    }

    @Test
    fun videoFailureIsNeverCapturedByAudioRescuePolicy() {
        assertEquals(
            PostFfmpegAudioFailureAction.USE_NORMAL_RECOVERY,
            decidePostFfmpegAudioFailureAction(
                attribution = PlaybackFailureAttribution(
                    streamKind = PlaybackFailureStreamKind.VIDEO,
                    rendererFailure = true,
                    rendererTrackType = C.TRACK_TYPE_VIDEO,
                    errorCode = 4003,
                ),
                ffmpegRescueAttempted = true,
            ),
        )
    }

    @Test
    fun nonRendererAudioFailureStaysOnNormalRecoveryLane() {
        assertEquals(
            PostFfmpegAudioFailureAction.USE_NORMAL_RECOVERY,
            decidePostFfmpegAudioFailureAction(
                attribution = PlaybackFailureAttribution(
                    streamKind = PlaybackFailureStreamKind.AUDIO,
                    rendererFailure = false,
                    rendererTrackType = C.TRACK_TYPE_AUDIO,
                    errorCode = 4003,
                ),
                ffmpegRescueAttempted = true,
            ),
        )
    }

    private fun audioRendererFailure() = PlaybackFailureAttribution(
        streamKind = PlaybackFailureStreamKind.AUDIO,
        rendererFailure = true,
        rendererTrackType = C.TRACK_TYPE_AUDIO,
        errorCode = 4003,
    )
}
