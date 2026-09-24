package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sole.cinevault.subtitles.SubtitleStudioNavigationCoordinator

/**
 * Compose wiring for the player's single transient-menu close contract.
 *
 * The behavioural ordering remains owned by [PlayerMenuCloseCoordinator].
 * This runtime only connects that coordinator to the state holders and
 * screen-owned visibility setters used by VideoPlayerScreen.
 */
@Composable
fun rememberPlayerMenuCloseRuntime(
    chromeUi: PlayerChromeUiState,
    coreUi: SubtitleCoreUiState,
    driftUi: DriftCorrectionState,
    searchUi: SubtitleAcquisitionUiState,
    subtitleStudioNavigation: SubtitleStudioNavigationCoordinator,
    onShowAudioFxDashboardChanged: (Boolean) -> Unit,
    onShowSubtitleDockChanged: (Boolean) -> Unit,
    onShowSubtitleBloomChanged: (Boolean) -> Unit,
    onStudioCategoryCleared: () -> Unit,
    onShowDualSubsWindowChanged: (Boolean) -> Unit,
    onShowSubtitleBehaviourWindowChanged: (Boolean) -> Unit,
): PlayerMenuCloseCoordinator = remember {
    PlayerMenuCloseCoordinator(
        closeAudioSelector = { chromeUi.showAudioSelector = false },
        closeAudioFxDashboard = { onShowAudioFxDashboardChanged(false) },
        closeSettings = { coreUi.showSettings = false },
        closeDriftDialog = { driftUi.showDialog = false },
        closeDialogueSync = {
            coreUi.dialogueSyncArmed = false
            coreUi.dialogueSyncReferenceMs = null
        },
        closeSpeedMenu = { chromeUi.showSpeedMenu = false },
        closeSleepMenu = { chromeUi.showSleepMenu = false },
        closeSrtBrowser = { chromeUi.showSrtBrowser = false },
        closeSubtitleDock = { onShowSubtitleDockChanged(false) },
        closeSubtitleBloom = {
            onShowSubtitleBloomChanged(false)
            onStudioCategoryCleared()
        },
        closeDualSubsWindow = { onShowDualSubsWindowChanged(false) },
        closeSubtitleBehaviourWindow = { onShowSubtitleBehaviourWindowChanged(false) },
        closeSubtitleSurfaces = {
            subtitleStudioNavigation.closeSubtitleSurfaces(
                clearPendingImportCandidates = { searchUi.pendingImportCandidates = null },
                setShowFallback = { searchUi.showFallback = it },
                setShowEmbeddedBrowser = { searchUi.showEmbeddedBrowser = it },
                setShowDualSubsWindow = onShowDualSubsWindowChanged,
            )
        },
    )
}
