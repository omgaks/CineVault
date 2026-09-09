package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRestoredDualSubtitleEligibilityTest {

    @Test
    fun allRequirementsReady_reappliesDualSubtitles() {
        assertTrue(
            shouldApplyRestoredDualSubtitles(
                movieSubtitleMemoryReady = true,
                restoredDualNeedsApply = true,
                dualSubtitlesEnabled = true,
                hasPrimarySubtitle = true,
            )
        )
    }

    @Test
    fun memoryNotReady_doesNotReapply() {
        assertFalse(
            shouldApplyRestoredDualSubtitles(
                movieSubtitleMemoryReady = false,
                restoredDualNeedsApply = true,
                dualSubtitlesEnabled = true,
                hasPrimarySubtitle = true,
            )
        )
    }

    @Test
    fun restoreNotPending_doesNotReapply() {
        assertFalse(
            shouldApplyRestoredDualSubtitles(
                movieSubtitleMemoryReady = true,
                restoredDualNeedsApply = false,
                dualSubtitlesEnabled = true,
                hasPrimarySubtitle = true,
            )
        )
    }

    @Test
    fun dualSubsDisabled_doesNotReapply() {
        assertFalse(
            shouldApplyRestoredDualSubtitles(
                movieSubtitleMemoryReady = true,
                restoredDualNeedsApply = true,
                dualSubtitlesEnabled = false,
                hasPrimarySubtitle = true,
            )
        )
    }

    @Test
    fun primarySubtitleMissing_doesNotReapply() {
        assertFalse(
            shouldApplyRestoredDualSubtitles(
                movieSubtitleMemoryReady = true,
                restoredDualNeedsApply = true,
                dualSubtitlesEnabled = true,
                hasPrimarySubtitle = false,
            )
        )
    }
}
