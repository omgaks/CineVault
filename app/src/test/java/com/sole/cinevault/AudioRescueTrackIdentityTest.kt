package com.sole.cinevault

import androidx.media3.common.Format
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRescueTrackIdentityTest {

    private val identity = AudioRescueTrackIdentity(
        language = "eng",
        sampleMimeType = "audio/vnd.dts.hd",
        codecs = "dtsc",
        channelCount = 6,
        sampleRate = 48000,
    )

    @Test
    fun sameTrackCharacteristicsMatch() {
        assertTrue(identity.matches(format()))
    }

    @Test
    fun differentLanguageDoesNotMatch() {
        assertFalse(identity.matches(format(language = "jpn")))
    }

    @Test
    fun differentCodecDoesNotMatch() {
        assertFalse(identity.matches(format(codecs = "ac-3")))
    }

    @Test
    fun differentChannelLayoutDoesNotMatch() {
        assertFalse(identity.matches(format(channelCount = 2)))
    }

    private fun format(
        language: String = "eng",
        codecs: String = "dtsc",
        channelCount: Int = 6,
    ) = Format.Builder()
        .setLanguage(language)
        .setSampleMimeType("audio/vnd.dts.hd")
        .setCodecs(codecs)
        .setChannelCount(channelCount)
        .setSampleRate(48000)
        .build()
}
