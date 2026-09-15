package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFfmpegRescuePresentationTest {

    @Test
    fun notAttemptedStaysSilent() {
        assertNull(audioFfmpegRescueOutcomeLabel(AudioFfmpegRescueOutcome.NOT_ATTEMPTED))
        assertNull(audioFfmpegRescuePillLabel(AudioFfmpegRescueOutcome.NOT_ATTEMPTED))
    }

    @Test
    fun pendingNeverClaimsFfmpegIsActive() {
        assertEquals(
            "FFmpeg audio rescue pending",
            audioFfmpegRescueOutcomeLabel(AudioFfmpegRescueOutcome.PENDING),
        )
        assertEquals(
            "FFMPEG SWITCH",
            audioFfmpegRescuePillLabel(AudioFfmpegRescueOutcome.PENDING),
        )
    }

    @Test
    fun confirmedIsTheOnlyOutcomeThatClaimsFfmpegAudio() {
        assertEquals(
            "FFmpeg audio rescued",
            audioFfmpegRescueOutcomeLabel(AudioFfmpegRescueOutcome.CONFIRMED),
        )
        assertEquals(
            "FFMPEG AUDIO",
            audioFfmpegRescuePillLabel(AudioFfmpegRescueOutcome.CONFIRMED),
        )
    }

    @Test
    fun failedRescueIsExplicit() {
        assertEquals(
            "FFmpeg audio rescue failed",
            audioFfmpegRescueOutcomeLabel(AudioFfmpegRescueOutcome.FAILED),
        )
        assertEquals(
            "AUDIO FAILED",
            audioFfmpegRescuePillLabel(AudioFfmpegRescueOutcome.FAILED),
        )
    }
}
