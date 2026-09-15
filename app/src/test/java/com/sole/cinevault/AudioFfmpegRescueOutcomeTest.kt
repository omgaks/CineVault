package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFfmpegRescueOutcomeTest {

    @Test
    fun notAttemptedWinsBeforeAnyRescue() {
        assertEquals(
            AudioFfmpegRescueOutcome.NOT_ATTEMPTED,
            audioFfmpegRescueOutcome(
                attempted = false,
                activeDecoder = ActiveAudioDecoderStatus(
                    kind = ActiveAudioDecoderKind.PLATFORM,
                    decoderName = "c2.android.aac.decoder",
                ),
                terminalAudioFailureAfterAttempt = false,
            ),
        )
    }

    @Test
    fun attemptedWithoutFfmpegDecoderIsPending() {
        assertEquals(
            AudioFfmpegRescueOutcome.PENDING,
            audioFfmpegRescueOutcome(
                attempted = true,
                activeDecoder = ActiveAudioDecoderStatus(),
                terminalAudioFailureAfterAttempt = false,
            ),
        )
    }

    @Test
    fun actualFfmpegDecoderConfirmsRescue() {
        assertEquals(
            AudioFfmpegRescueOutcome.CONFIRMED,
            audioFfmpegRescueOutcome(
                attempted = true,
                activeDecoder = ActiveAudioDecoderStatus(
                    kind = ActiveAudioDecoderKind.FFMPEG,
                    decoderName = "ffmpeg",
                ),
                terminalAudioFailureAfterAttempt = false,
            ),
        )
    }

    @Test
    fun terminalAudioFailureAfterAttemptMarksRescueFailed() {
        assertEquals(
            AudioFfmpegRescueOutcome.FAILED,
            audioFfmpegRescueOutcome(
                attempted = true,
                activeDecoder = ActiveAudioDecoderStatus(
                    kind = ActiveAudioDecoderKind.FFMPEG,
                    decoderName = "ffmpeg",
                ),
                terminalAudioFailureAfterAttempt = true,
            ),
        )
    }
}
