package com.sole.cinevault

/**
 * Central close contract for every transient player surface.
 *
 * No Android/Compose types live here so the behaviour stays unit-testable.
 * Every new transient player surface must be added here rather than creating
 * another independent empty-space/back-dismiss path.
 */
class PlayerMenuCloseCoordinator(
    private val closeAudioSelector: () -> Unit,
    private val closeAudioFxDashboard: () -> Unit,
    private val closeSettings: () -> Unit,
    private val closeDriftDialog: () -> Unit,
    private val closeSpeedMenu: () -> Unit,
    private val closeSleepMenu: () -> Unit,
    private val closeSrtBrowser: () -> Unit,
    private val closeSubtitleDock: () -> Unit,
    private val closeSubtitleBloom: () -> Unit,
    private val closeDualSubsWindow: () -> Unit,
    private val closeSubtitleBehaviourWindow: () -> Unit,
    private val closeSpeechSubtitlePanel: () -> Unit,
    private val closeSubtitleTranslationPanel: () -> Unit,
    private val cancelDialogueSync: () -> Unit,
    private val closeSubtitleSurfaces: () -> Unit,
) {
    fun closeAll() {
        closeAudioSelector()
        closeAudioFxDashboard()
        closeSettings()
        closeDriftDialog()
        closeSpeedMenu()
        closeSleepMenu()
        closeSrtBrowser()
        closeSubtitleDock()
        closeSubtitleBloom()
        closeDualSubsWindow()
        closeSubtitleBehaviourWindow()
        closeSpeechSubtitlePanel()
        closeSubtitleTranslationPanel()
        cancelDialogueSync()
        closeSubtitleSurfaces()
    }
}
