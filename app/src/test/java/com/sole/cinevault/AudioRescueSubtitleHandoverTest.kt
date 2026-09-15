package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioRescueSubtitleHandoverTest {

    @Test
    fun handoverCanCarrySubtitleSnapshot() {
        val snapshot = AudioRescueSubtitleSnapshot(
            primaryUri = null,
            originalUri = null,
            selectedKey = "embedded:eng:0",
            selectedLabel = "English",
            selectedSource = "Embedded",
            primaryLanguage = "eng",
        )
        val handover = AudioRuntimeRescueHandover(
            mediaItem = androidx.media3.common.MediaItem.EMPTY,
            resumePositionMs = 1000L,
            playWhenReady = true,
            playbackSpeed = 1.0f,
            subtitleSnapshot = snapshot,
        )
        assertEquals("embedded:eng:0", handover.subtitleSnapshot?.selectedKey)
    }

    @Test
    fun subtitleSnapshotRemainsOptional() {
        val handover = AudioRuntimeRescueHandover(
            mediaItem = androidx.media3.common.MediaItem.EMPTY,
            resumePositionMs = 0L,
            playWhenReady = false,
            playbackSpeed = 1.0f,
        )
        assertNull(handover.subtitleSnapshot)
    }
}
