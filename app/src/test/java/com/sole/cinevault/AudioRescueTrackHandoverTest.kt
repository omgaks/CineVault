package com.sole.cinevault

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioRescueTrackHandoverTest {

    @Test
    fun handoverCarriesSelectedAudioIdentity() {
        val identity = AudioRescueTrackIdentity(
            language = "jpn",
            sampleMimeType = "audio/vnd.dts.hd",
            codecs = "dtsc",
            channelCount = 6,
            sampleRate = 48000,
        )

        val handover = AudioRuntimeRescueHandover(
            mediaItem = MediaItem.EMPTY,
            resumePositionMs = 42_000L,
            playWhenReady = true,
            playbackSpeed = 1.0f,
            audioTrackIdentity = identity,
        )

        assertEquals(identity, handover.audioTrackIdentity)
    }

    @Test
    fun audioIdentityRemainsOptional() {
        val handover = AudioRuntimeRescueHandover(
            mediaItem = MediaItem.EMPTY,
            resumePositionMs = 0L,
            playWhenReady = false,
            playbackSpeed = 1.0f,
        )

        assertNull(handover.audioTrackIdentity)
    }
}
