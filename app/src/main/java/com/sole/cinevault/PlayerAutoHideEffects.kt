package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
internal fun PlayerAutoHideEffects(
    showControls: Boolean,
    showTopBar: Boolean,
    controlsLocked: Boolean,
    lockButtonVisibleWhileLocked: Boolean,
    isDraggingSeekbar: Boolean,
    showAudioSelector: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    showSrtBrowser: Boolean,
    menuTouchKey: Int,
    brightnessGestureKey: Int,
    volumeGestureKey: Int,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    searchUi: SubtitleAcquisitionUiState,
    driftUi: DriftCorrectionState,
    studioUi: SubtitleStudioUiState,
    onHideControls: () -> Unit,
    onHideTopBar: () -> Unit,
    onHideLockedButton: () -> Unit,
    onHideAudioSelector: () -> Unit,
    onHideSpeedMenu: () -> Unit,
    onHideSleepMenu: () -> Unit,
    onHideSrtBrowser: () -> Unit,
    onHideBrightnessHud: () -> Unit,
    onHideVolumeHud: () -> Unit,
) {
    LaunchedEffect(
        showControls,
        showAudioSelector,
        coreUi.showSettings,
        trackUi.showSelector,
        searchUi.showSearch,
        showSpeedMenu,
        showSleepMenu,
        showSrtBrowser,
        isDraggingSeekbar,
    ) {
        val anyMenuOpen = showAudioSelector || coreUi.showSettings || trackUi.showSelector ||
            searchUi.showSearch || driftUi.showDialog || coreUi.showAppearanceStudio ||
            studioUi.showStudio || coreUi.dialogueSyncArmed || showSpeedMenu ||
            showSleepMenu || showSrtBrowser
        if (showControls && !anyMenuOpen && !isDraggingSeekbar) {
            delay(4500)
            if (!isDraggingSeekbar && !anyMenuOpen) onHideControls()
        }
    }

    LaunchedEffect(controlsLocked, lockButtonVisibleWhileLocked) {
        if (controlsLocked && lockButtonVisibleWhileLocked) {
            delay(4500)
            onHideLockedButton()
        }
    }

    LaunchedEffect(
        showTopBar,
        showAudioSelector,
        coreUi.showSettings,
        trackUi.showSelector,
        searchUi.showSearch,
        showSpeedMenu,
        showSleepMenu,
        showSrtBrowser,
        isDraggingSeekbar,
    ) {
        val anyMenuOpen = showAudioSelector || coreUi.showSettings || trackUi.showSelector ||
            searchUi.showSearch || driftUi.showDialog || coreUi.showAppearanceStudio ||
            studioUi.showStudio || coreUi.dialogueSyncArmed || showSpeedMenu ||
            showSleepMenu || showSrtBrowser
        if (showTopBar && !anyMenuOpen && !isDraggingSeekbar) {
            delay(2800)
            if (!isDraggingSeekbar && !anyMenuOpen) onHideTopBar()
        }
    }

    LaunchedEffect(showAudioSelector, menuTouchKey) {
        if (showAudioSelector) {
            delay(9000)
            onHideAudioSelector()
        }
    }
    LaunchedEffect(coreUi.showSettings, studioUi.menuTouchKey) {
        if (coreUi.showSettings) {
            delay(9000)
            coreUi.showSettings = false
        }
    }
    LaunchedEffect(trackUi.showSelector, studioUi.menuTouchKey) {
        if (trackUi.showSelector) {
            delay(12000)
            trackUi.showSelector = false
        }
    }
    LaunchedEffect(searchUi.showSearch, studioUi.menuTouchKey) {
        if (searchUi.showSearch) {
            delay(18000)
            searchUi.showSearch = false
        }
    }
    LaunchedEffect(coreUi.showAppearanceStudio, studioUi.menuTouchKey) {
        if (coreUi.showAppearanceStudio) {
            delay(15000)
            coreUi.showAppearanceStudio = false
        }
    }
    LaunchedEffect(studioUi.showStudio, studioUi.menuTouchKey) {
        if (studioUi.showStudio) {
            delay(30000)
            studioUi.showStudio = false
        }
    }
    LaunchedEffect(showSrtBrowser) {
        if (showSrtBrowser) {
            delay(20000)
            onHideSrtBrowser()
        }
    }
    LaunchedEffect(showSpeedMenu) {
        if (showSpeedMenu) {
            delay(8000)
            onHideSpeedMenu()
        }
    }
    LaunchedEffect(showSleepMenu) {
        if (showSleepMenu) {
            delay(8000)
            onHideSleepMenu()
        }
    }
    LaunchedEffect(brightnessGestureKey) {
        if (brightnessGestureKey > 0) {
            delay(1400)
            onHideBrightnessHud()
        }
    }
    LaunchedEffect(volumeGestureKey) {
        if (volumeGestureKey > 0) {
            delay(1400)
            onHideVolumeHud()
        }
    }
}
