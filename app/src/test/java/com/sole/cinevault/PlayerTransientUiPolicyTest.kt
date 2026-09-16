package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTransientUiPolicyTest {
    @Test fun emptySnapshotHasNoTransientUi() {
        assertFalse(PlayerTransientUiSnapshot().anyVisible)
    }

    @Test fun subtitleHudCountsAsTransientUi() {
        assertTrue(PlayerTransientUiSnapshot(subtitleDock = true).anyVisible)
    }

    @Test fun audioStudioCountsAsTransientUi() {
        assertTrue(PlayerTransientUiSnapshot(audioFxDashboard = true).anyVisible)
    }

    @Test fun aiPanelCountsAsTransientUi() {
        assertTrue(PlayerTransientUiSnapshot(subtitleTranslationPanel = true).anyVisible)
    }
}
