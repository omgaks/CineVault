package com.sole.cinevault.subtitles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedSubtitleSelectionPolicyTest {
    @Test fun validSelectionIsAccepted() =
        assertTrue(isValidEmbeddedSubtitleSelection(0, 1, listOf(2)))

    @Test fun staleGroupIsRejected() =
        assertFalse(isValidEmbeddedSubtitleSelection(1, 0, listOf(2)))

    @Test fun staleTrackIsRejected() =
        assertFalse(isValidEmbeddedSubtitleSelection(0, 2, listOf(2)))

    @Test fun emptyInventoryIsRejected() =
        assertFalse(isValidEmbeddedSubtitleSelection(0, 0, emptyList()))
}
