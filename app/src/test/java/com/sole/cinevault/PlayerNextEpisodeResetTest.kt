package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNextEpisodeResetTest {

    @Test
    fun overlayVisibleAndSeekedBeforeCredits_resets() {
        assertTrue(
            shouldResetNextEpisodeOverlay(
                showNextEpisodeOverlay = true,
                creditsStartMs = 90_000L,
                position = 89_999L,
            )
        )
    }

    @Test
    fun exactlyAtCredits_doesNotReset() {
        assertFalse(
            shouldResetNextEpisodeOverlay(
                showNextEpisodeOverlay = true,
                creditsStartMs = 90_000L,
                position = 90_000L,
            )
        )
    }

    @Test
    fun afterCredits_doesNotReset() {
        assertFalse(
            shouldResetNextEpisodeOverlay(
                showNextEpisodeOverlay = true,
                creditsStartMs = 90_000L,
                position = 95_000L,
            )
        )
    }

    @Test
    fun overlayNotVisible_doesNotReset() {
        assertFalse(
            shouldResetNextEpisodeOverlay(
                showNextEpisodeOverlay = false,
                creditsStartMs = 90_000L,
                position = 10_000L,
            )
        )
    }

    @Test
    fun missingCreditsSegment_doesNotReset() {
        assertFalse(
            shouldResetNextEpisodeOverlay(
                showNextEpisodeOverlay = true,
                creditsStartMs = null,
                position = 10_000L,
            )
        )
    }
}
