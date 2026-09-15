package com.sole.cinevault

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
        assertTrue(
            identity.matches(
                AudioRescueTrackIdentity(
                    language = "eng",
                    sampleMimeType = "audio/vnd.dts.hd",
                    codecs = "dtsc",
                    channelCount = 6,
                    sampleRate = 48000,
                )
            )
        )
    }

    @Test
    fun differentLanguageDoesNotMatch() {
        assertFalse(
            identity.matches(
                AudioRescueTrackIdentity(
                    language = "jpn",
                    sampleMimeType = "audio/vnd.dts.hd",
                    codecs = "dtsc",
                    channelCount = 6,
                    sampleRate = 48000,
                )
            )
        )
    }

    @Test
    fun differentCodecDoesNotMatch() {
        assertFalse(
            identity.matches(
                AudioRescueTrackIdentity(
                    language = "eng",
                    sampleMimeType = "audio/vnd.dts.hd",
                    codecs = "ac-3",
                    channelCount = 6,
                    sampleRate = 48000,
                )
            )
        )
    }

    @Test
    fun differentChannelLayoutDoesNotMatch() {
        assertFalse(
            identity.matches(
                AudioRescueTrackIdentity(
                    language = "eng",
                    sampleMimeType = "audio/vnd.dts.hd",
                    codecs = "dtsc",
                    channelCount = 2,
                    sampleRate = 48000,
                )
            )
        )
    }
}
