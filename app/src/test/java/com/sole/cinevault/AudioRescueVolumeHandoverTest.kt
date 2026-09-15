package com.sole.cinevault

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRescueVolumeHandoverTest {

    @Test
    fun mutedVolumeIsPreserved() {
        val handover = AudioRuntimeRescueHandover(
            mediaItem = MediaItem.EMPTY,
            resumePositionMs = 0L,
            playWhenReady = true,
            playbackSpeed = 1.0f,
            volume = 0.0f,
        )

        assertEquals(0.0f, handover.volume, 0.0f)
    }

    @Test
    fun normalVolumeIsPreserved() {
        val handover = AudioRuntimeRescueHandover(
            mediaItem = MediaItem.EMPTY,
            resumePositionMs = 0L,
            playWhenReady = true,
            playbackSpeed = 1.0f,
            volume = 0.42f,
        )

        assertEquals(0.42f, handover.volume, 0.0f)
    }
}
