package com.sole.cinevault

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPlaybackRecoveryPolicyTest {

    @Test
    fun dtsAudioRendererFailureRequestsFfmpegRescue() {
        val decision = decideAudioPlaybackRecovery(
            attribution = audioFailure(),
            selectedAudio = audio("audio/vnd.dts", "dts"),
            activeDecoder = ActiveAudioDecoderStatus(
                kind = ActiveAudioDecoderKind.PLATFORM,
                decoderName = "c2.android.dts.decoder",
            ),
            ffmpegRescueAlreadyAttempted = false,
        )

        assertEquals(
            AudioPlaybackRecoveryAction.SWITCH_TO_FFMPEG,
            decision.action,
        )
        assertEquals(AudioCodecFamily.DTS, decision.codecFamily)
    }

    @Test
    fun trueHdAudioRendererFailureRequestsFfmpegRescue() {
        val decision = decideAudioPlaybackRecovery(
            attribution = audioFailure(),
            selectedAudio = audio("audio/true-hd", "mlpa"),
            activeDecoder = ActiveAudioDecoderStatus(),
            ffmpegRescueAlreadyAttempted = false,
        )

        assertEquals(
            AudioPlaybackRecoveryAction.SWITCH_TO_FFMPEG,
            decision.action,
        )
        assertEquals(AudioCodecFamily.TRUEHD, decision.codecFamily)
    }

    @Test
    fun alreadyAttemptedFfmpegRescueCannotLoop() {
        val decision = decideAudioPlaybackRecovery(
            attribution = audioFailure(),
            selectedAudio = audio("audio/vnd.dts.hd", "dtsh"),
            activeDecoder = ActiveAudioDecoderStatus(),
            ffmpegRescueAlreadyAttempted = true,
        )

        assertEquals(AudioPlaybackRecoveryAction.FAIL, decision.action)
    }

    @Test
    fun activeFfmpegFailureCannotRequestFfmpegAgain() {
        val decision = decideAudioPlaybackRecovery(
            attribution = audioFailure(),
            selectedAudio = audio("audio/vnd.dts", "dts"),
            activeDecoder = ActiveAudioDecoderStatus(
                kind = ActiveAudioDecoderKind.FFMPEG,
                decoderName = "ffmpeg",
            ),
            ffmpegRescueAlreadyAttempted = false,
        )

        assertEquals(AudioPlaybackRecoveryAction.FAIL, decision.action)
    }

    @Test
    fun aacPlatformFailureDoesNotForceFfmpeg() {
        val decision = decideAudioPlaybackRecovery(
            attribution = audioFailure(),
            selectedAudio = audio("audio/mp4a-latm", "mp4a.40.2"),
            activeDecoder = ActiveAudioDecoderStatus(
                kind = ActiveAudioDecoderKind.PLATFORM,
                decoderName = "c2.android.aac.decoder",
            ),
            ffmpegRescueAlreadyAttempted = false,
        )

        assertEquals(AudioPlaybackRecoveryAction.FAIL, decision.action)
        assertEquals(AudioCodecFamily.AAC, decision.codecFamily)
    }

    @Test
    fun videoFailureDoesNotEnterAudioRecoveryLane() {
        val decision = decideAudioPlaybackRecovery(
            attribution = PlaybackFailureAttribution(
                streamKind = PlaybackFailureStreamKind.VIDEO,
                rendererFailure = true,
                rendererTrackType = C.TRACK_TYPE_VIDEO,
                errorCode = 4003,
            ),
            selectedAudio = audio("audio/vnd.dts", "dts"),
            activeDecoder = ActiveAudioDecoderStatus(),
            ffmpegRescueAlreadyAttempted = false,
        )

        assertEquals(AudioPlaybackRecoveryAction.NONE, decision.action)
    }

    @Test
    fun subtitleFailureDoesNotEnterAudioRecoveryLane() {
        val decision = decideAudioPlaybackRecovery(
            attribution = PlaybackFailureAttribution(
                streamKind = PlaybackFailureStreamKind.TEXT,
                rendererFailure = true,
                rendererTrackType = C.TRACK_TYPE_TEXT,
                errorCode = 4003,
            ),
            selectedAudio = audio("audio/true-hd", "mlpa"),
            activeDecoder = ActiveAudioDecoderStatus(),
            ffmpegRescueAlreadyAttempted = false,
        )

        assertEquals(AudioPlaybackRecoveryAction.NONE, decision.action)
    }

    private fun audioFailure() = PlaybackFailureAttribution(
        streamKind = PlaybackFailureStreamKind.AUDIO,
        rendererFailure = true,
        rendererTrackType = C.TRACK_TYPE_AUDIO,
        errorCode = 4003,
    )

    private fun audio(
        mimeType: String,
        codecString: String,
    ) = PlaybackStreamDescriptor(
        kind = PlaybackStreamKind.AUDIO,
        mimeType = mimeType,
        codecString = codecString,
        language = "eng",
        selected = true,
    )
}
