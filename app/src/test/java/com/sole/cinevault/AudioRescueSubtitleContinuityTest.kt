package com.sole.cinevault

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRescueSubtitleContinuityTest {

    @Test
    fun primarySubtitleUriIsRestorableAcrossAudioRuntimeRebuild() {
        assertTrue(
            snapshot(primaryUri = Uri.parse("file:///movie/sub.srt"))
                .hasRestorableSubtitleSelection()
        )
    }

    @Test
    fun originalSubtitleUriIsRestorableAcrossAudioRuntimeRebuild() {
        assertTrue(
            snapshot(originalUri = Uri.parse("file:///movie/original.srt"))
                .hasRestorableSubtitleSelection()
        )
    }

    @Test
    fun selectedTrackKeyIsRestorableAcrossAudioRuntimeRebuild() {
        assertTrue(
            snapshot(selectedKey = "embedded:eng:0")
                .hasRestorableSubtitleSelection()
        )
    }

    @Test
    fun emptySubtitleSelectionNeedsNoRestore() {
        assertFalse(snapshot().hasRestorableSubtitleSelection())
    }

    private fun snapshot(
        primaryUri: Uri? = null,
        originalUri: Uri? = null,
        selectedKey: String? = null,
    ) = AudioRescueSubtitleSnapshot(
        primaryUri = primaryUri,
        originalUri = originalUri,
        selectedKey = selectedKey,
        selectedLabel = "",
        selectedSource = "",
        primaryLanguage = null,
    )
}
