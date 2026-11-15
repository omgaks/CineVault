package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRemoteKeysTest {
    @Test fun mediaKeysAlwaysWork() {
        assertEquals(RemoteKeyAction.TOGGLE_PLAY, remoteKeyAction(85, controlsVisible = true, menuOpen = true))
        assertEquals(RemoteKeyAction.SEEK_FORWARD, remoteKeyAction(90, controlsVisible = true, menuOpen = false))
    }

    @Test fun arrowsSeekOnlyWhenControlsHidden() {
        assertEquals(RemoteKeyAction.SEEK_BACK, remoteKeyAction(21, controlsVisible = false, menuOpen = false))
        assertEquals(RemoteKeyAction.NONE, remoteKeyAction(21, controlsVisible = true, menuOpen = false))
        assertEquals(RemoteKeyAction.NONE, remoteKeyAction(22, controlsVisible = false, menuOpen = true))
    }

    @Test fun okTogglesAndUpDownRevealControls() {
        assertEquals(RemoteKeyAction.TOGGLE_PLAY, remoteKeyAction(23, controlsVisible = false, menuOpen = false))
        assertEquals(RemoteKeyAction.SHOW_CONTROLS, remoteKeyAction(19, controlsVisible = false, menuOpen = false))
    }

    @Test fun dedicatedKeysSkipFurther() {
        assertEquals(30_000L, remoteSeekStepMs(89))
        assertEquals(10_000L, remoteSeekStepMs(21))
    }
}
