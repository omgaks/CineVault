package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackInfoVisibilityTest {

    @Test
    fun panelStaysHiddenUntilUserRequestsIt() {
        val visibility = playbackInfoVisibility(
            panelRequested = false,
            fallbackOccurred = false,
        )

        assertFalse(visibility.showPanel)
        assertFalse(visibility.showRecoverySection)
    }

    @Test
    fun fallbackDoesNotForceTechnicalPanelOntoPlayer() {
        val visibility = playbackInfoVisibility(
            panelRequested = false,
            fallbackOccurred = true,
        )

        assertFalse(visibility.showPanel)
        assertFalse(visibility.showRecoverySection)
    }

    @Test
    fun requestedPanelShowsRecoveryWhenFallbackOccurred() {
        val visibility = playbackInfoVisibility(
            panelRequested = true,
            fallbackOccurred = true,
        )

        assertTrue(visibility.showPanel)
        assertTrue(visibility.showRecoverySection)
    }

    @Test
    fun requestedPanelWithoutFallbackStillShowsDiagnostics() {
        val visibility = playbackInfoVisibility(
            panelRequested = true,
            fallbackOccurred = false,
        )

        assertTrue(visibility.showPanel)
        assertFalse(visibility.showRecoverySection)
    }
}
