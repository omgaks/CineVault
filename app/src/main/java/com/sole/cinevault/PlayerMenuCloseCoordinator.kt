package com.sole.cinevault

/**
 * Slice 43: centralises closing the player's transient menus/sheets.
 *
 * This intentionally contains no Android or Compose types; it simply executes
 * the close operations in one place so VideoPlayerScreen does not carry the
 * full menu-dismiss sequence inline.
 */
class PlayerMenuCloseCoordinator(
    private val closeAudioSelector: () -> Unit,
    private val closeSettings: () -> Unit,
    private val closeDriftDialog: () -> Unit,
    private val closeSpeedMenu: () -> Unit,
    private val closeSleepMenu: () -> Unit,
    private val closeSrtBrowser: () -> Unit,
    private val closeSubtitleSurfaces: () -> Unit,
) {
    fun closeAll() {
        closeAudioSelector()
        closeSettings()
        closeDriftDialog()
        closeSpeedMenu()
        closeSleepMenu()
        closeSrtBrowser()
        closeSubtitleSurfaces()
    }
}
