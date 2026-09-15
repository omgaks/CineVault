package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRescueSubtitleContinuityTest {

    @Test
    fun selectedLocalTrackKeyIsRestorableAcrossAudioRuntimeRebuild() {
        assertTrue(
            snapshot(selectedKey = "local:/movie/sub.srt")
                .hasRestorableSubtitleSelection()
        )
    }

    @Test
    fun selectedEmbeddedTrackKeyIsRestorableAcrossAudioRuntimeRebuild() {
        assertTrue(
            snapshot(selectedKey = "embedded:eng:0")
                .hasRestorableSubtitleSelection()
        )
    }

    @Test
    fun blankSelectedTrackKeyNeedsNoRestore() {
        assertFalse(
            snapshot(selectedKey = "   ")
                .hasRestorableSubtitleSelection()
        )
    }

    @Test
    fun emptySubtitleSelectionNeedsNoRestore() {
        assertFalse(snapshot().hasRestorableSubtitleSelection())
    }

    private fun snapshot(
        selectedKey: String? = null,
    ) = AudioRescueSubtitleSnapshot(
        primaryUri = null,
        originalUri = null,
        selectedKey = selectedKey,
        selectedLabel = "",
        selectedSource = "",
        primaryLanguage = null,
    )
}
