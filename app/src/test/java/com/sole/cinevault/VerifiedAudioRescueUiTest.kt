package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedAudioRescueUiTest {
    @Test
    fun pendingIsVisibleButDoesNotClaimSuccess() {
        val ui = verifiedAudioRescueUi(AudioFfmpegRescueOutcome.PENDING)
        assertEquals("FFmpeg audio rescue pending", ui.detailLabel)
        assertEquals("FFMPEG SWITCH", ui.pillLabel)
        assertTrue(ui.emphasize)
    }

    @Test
    fun confirmedUsesFfmpegAudioLabel() {
        val ui = verifiedAudioRescueUi(AudioFfmpegRescueOutcome.CONFIRMED)
        assertEquals("FFmpeg audio rescued", ui.detailLabel)
        assertEquals("FFMPEG AUDIO", ui.pillLabel)
        assertTrue(ui.emphasize)
    }

    @Test
    fun untouchedPlaybackAddsNoRescueUi() {
        val ui = verifiedAudioRescueUi(AudioFfmpegRescueOutcome.NOT_ATTEMPTED)
        assertEquals(null, ui.detailLabel)
        assertEquals(null, ui.pillLabel)
        assertFalse(ui.emphasize)
    }
}
