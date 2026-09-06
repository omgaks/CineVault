package com.sole.cinevault

internal fun shouldShowMainPlayerControls(
    externalDisplayActive: Boolean,
    showControls: Boolean,
    isDraggingSeekbar: Boolean,
    showAudioSelector: Boolean,
    showSubtitleSettings: Boolean,
    showTrackSelector: Boolean,
    showDriftDialog: Boolean,
    showAppearanceStudio: Boolean,
    dialogueSyncArmed: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    showSubtitleSearch: Boolean,
    isInPipMode: Boolean,
): Boolean {
    val largeSheetVisible = showSubtitleSearch

    val anyControlSurfaceVisible =
        showControls ||
            isDraggingSeekbar ||
            showAudioSelector ||
            showSubtitleSettings ||
            showTrackSelector ||
            showDriftDialog ||
            showAppearanceStudio ||
            dialogueSyncArmed ||
            showSpeedMenu ||
            showSleepMenu

    return !externalDisplayActive &&
        anyControlSurfaceVisible &&
        !largeSheetVisible &&
        !isInPipMode
}
