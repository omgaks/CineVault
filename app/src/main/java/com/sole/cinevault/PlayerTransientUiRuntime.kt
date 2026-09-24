package com.sole.cinevault

/**
 * Central assembly point for the player's transient UI visibility snapshot.
 *
 * The visibility policy itself remains in [PlayerTransientUiSnapshot]. Keeping
 * assembly here prevents VideoPlayerScreen from owning another policy-shaped
 * construction block while preserving the existing behavior exactly.
 */
internal fun playerTransientUiSnapshot(
    audioSelector: Boolean,
    audioFxDashboard: Boolean,
    subtitleSettings: Boolean,
    trackSelector: Boolean,
    subtitleSearch: Boolean,
    driftDialog: Boolean,
    appearanceStudio: Boolean,
    dialogueSyncArmed: Boolean,
    subtitleDock: Boolean,
    subtitleBloom: Boolean,
    dualSubsWindow: Boolean,
    subtitleBehaviourWindow: Boolean,
    speechSubtitlePanel: Boolean,
    subtitleTranslationPanel: Boolean,
    speedMenu: Boolean,
    sleepMenu: Boolean,
    srtBrowser: Boolean,
): PlayerTransientUiSnapshot = PlayerTransientUiSnapshot(
    audioSelector = audioSelector,
    audioFxDashboard = audioFxDashboard,
    subtitleSettings = subtitleSettings,
    trackSelector = trackSelector,
    subtitleSearch = subtitleSearch,
    driftDialog = driftDialog,
    appearanceStudio = appearanceStudio,
    dialogueSyncArmed = dialogueSyncArmed,
    subtitleDock = subtitleDock,
    subtitleBloom = subtitleBloom,
    dualSubsWindow = dualSubsWindow,
    subtitleBehaviourWindow = subtitleBehaviourWindow,
    speechSubtitlePanel = speechSubtitlePanel,
    subtitleTranslationPanel = subtitleTranslationPanel,
    speedMenu = speedMenu,
    sleepMenu = sleepMenu,
    srtBrowser = srtBrowser,
)
