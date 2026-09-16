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

    @Test fun dialogueSyncDoesNotInstallFullScreenDismissLayer() {
        val snapshot = PlayerTransientUiSnapshot(dialogueSyncArmed = true)
        assertTrue(snapshot.anyVisible)
        assertFalse(snapshot.needsDismissLayer)
    }

    @Test fun ordinaryMenuInstallsFullScreenDismissLayer() {
        assertTrue(PlayerTransientUiSnapshot(audioFxDashboard = true).needsDismissLayer)
    }

    @Test fun aiPanelCountsAsTransientUi() {
        assertTrue(PlayerTransientUiSnapshot(subtitleTranslationPanel = true).anyVisible)
    }
}
