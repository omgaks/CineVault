package com.sole.cinevault

import org.junit.Assert.assertNull
import org.junit.Test

class AudioRescueSubtitleRestoreTest {

    @Test
    fun handoverWithoutSubtitleSnapshotNeedsNoExplicitRestore() {
        val handover = AudioRuntimeRescueHandover(
            mediaItem = androidx.media3.common.MediaItem.EMPTY,
            resumePositionMs = 0L,
            playWhenReady = false,
            subtitleSnapshot = null,
        )

        assertNull(handover.subtitleRestore())
    }

    @Test
    fun embeddedOnlyIdentityDoesNotInventExternalSubtitleUri() {
        val handover = AudioRuntimeRescueHandover(
            mediaItem = androidx.media3.common.MediaItem.EMPTY,
            resumePositionMs = 0L,
            playWhenReady = false,
            subtitleSnapshot = AudioRescueSubtitleSnapshot(
                primaryUri = null,
                originalUri = null,
                selectedKey = "embedded:eng:0",
                selectedLabel = "English",
                selectedSource = "Embedded",
                primaryLanguage = "eng",
            ),
        )

        assertNull(handover.subtitleRestore())
    }
}
