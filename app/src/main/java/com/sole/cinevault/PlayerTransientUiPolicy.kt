package com.sole.cinevault

/** Pure visibility policy shared by the player and unit tests. */
data class PlayerTransientUiSnapshot(
    val audioSelector: Boolean = false,
    val audioFxDashboard: Boolean = false,
    val subtitleSettings: Boolean = false,
    val trackSelector: Boolean = false,
    val subtitleSearch: Boolean = false,
    val driftDialog: Boolean = false,
    val appearanceStudio: Boolean = false,
    val dialogueSyncArmed: Boolean = false,
    val subtitleDock: Boolean = false,
    val subtitleBloom: Boolean = false,
    val dualSubsWindow: Boolean = false,
    val subtitleBehaviourWindow: Boolean = false,
    val speechSubtitlePanel: Boolean = false,
    val subtitleTranslationPanel: Boolean = false,
    val speedMenu: Boolean = false,
    val sleepMenu: Boolean = false,
    val srtBrowser: Boolean = false,
) {
    val anyVisible: Boolean
        get() = audioSelector || audioFxDashboard || subtitleSettings ||
            trackSelector || subtitleSearch || driftDialog || appearanceStudio ||
            dialogueSyncArmed || subtitleDock || subtitleBloom || dualSubsWindow ||
            subtitleBehaviourWindow || speechSubtitlePanel ||
            subtitleTranslationPanel || speedMenu || sleepMenu || srtBrowser
}
