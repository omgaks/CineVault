package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSyncRenderCoordinatorTest {

    @Test
    fun disabledSubtitles_doNotRebuild() {
        assertFalse(
            shouldRebuildShiftedSubtitle(
                subtitlesEnabled = false,
                hasBaseSubtitle = true,
                requestedOffsetMs = 500L,
                appliedOffsetMs = 0L,
                requestedScale = 1f,
                appliedScale = 1f,
            )
        )
    }

    @Test
    fun missingBaseSubtitle_doesNotRebuild() {
        assertFalse(
            shouldRebuildShiftedSubtitle(
                subtitlesEnabled = true,
                hasBaseSubtitle = false,
                requestedOffsetMs = 500L,
                appliedOffsetMs = 0L,
                requestedScale = 1f,
                appliedScale = 1f,
            )
        )
    }

    @Test
    fun unchangedOffsetAndScale_doNotRebuild() {
        assertFalse(
            shouldRebuildShiftedSubtitle(
                subtitlesEnabled = true,
                hasBaseSubtitle = true,
                requestedOffsetMs = 750L,
                appliedOffsetMs = 750L,
                requestedScale = 1.002f,
                appliedScale = 1.002f,
            )
        )
    }

    @Test
    fun changedOffset_rebuilds() {
        assertTrue(
            shouldRebuildShiftedSubtitle(
                subtitlesEnabled = true,
                hasBaseSubtitle = true,
                requestedOffsetMs = 1000L,
                appliedOffsetMs = 500L,
                requestedScale = 1f,
                appliedScale = 1f,
            )
        )
    }

    @Test
    fun changedDriftScale_rebuilds() {
        assertTrue(
            shouldRebuildShiftedSubtitle(
                subtitlesEnabled = true,
                hasBaseSubtitle = true,
                requestedOffsetMs = 0L,
                appliedOffsetMs = 0L,
                requestedScale = 1.0427f,
                appliedScale = 1f,
            )
        )
    }
}
