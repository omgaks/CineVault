package com.sole.cinevault

import android.content.Context
import java.io.File
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.subtitles.*

/**
 * Slice 60: host for player overlay surfaces that are independent of the main
 * controls chrome. No mutable state is owned here.
 */
@Composable
internal fun BoxScope.PlayerOverlaySurfacesHost(
    context: Context,
    player: ExoPlayer,
    haptics: HapticFeedback,
    controlsLocked: Boolean,
    lockButtonVisibleWhileLocked: Boolean,
    showControls: Boolean,
    externalDisplayActive: Boolean,
    isLandscape: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    bottomDockPadding: Dp,
    playButton: Dp,
    subtitleIconCenterX: Float,
    autoSyncStatus: AutoSyncStatus,
    autoSyncCoordinator: AutoSyncCoordinator,
    pendingDeleteFile: File?,
    snackbarHostState: SnackbarHostState,
    pendingDeletePaths: List<String>,
    currentVideoPath: String,
    canDownloadExternalSubtitles: Boolean,
    generatedSubtitleFiles: List<GeneratedSubtitleFile>,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    searchUi: SubtitleAcquisitionUiState,
    studioUi: SubtitleStudioUiState,
    dualUi: DualSubtitleState,
    appearanceUi: SubtitleAppearanceUiState,
    driftUi: DriftCorrectionState,
    showSubtitleDock: Boolean,
    showSubtitleBloom: Boolean,
    studioCategory: StudioCategory?,
    showSubtitleBehaviourWindow: Boolean,
    showDualSubsWindow: Boolean,
    autoSyncSpeechTimeline: FloatArray?,
    dualSecondaryColorHex: String,
    pendingDualAiLanguage: String?,
    subtitleStudioNavigation: SubtitleStudioNavigationCoordinator,
    subtitleResetCoordinator: SubtitleResetCoordinator,
    subtitleSyncTools: SubtitleSyncToolsCoordinator,
    speechJobLabel: String?,
    speechJobProgress: Int?,
    translationJobLabel: String?,
    translationJobProgress: Int?,
    showSpeechSubtitlePanel: Boolean,
    showSubtitleTranslationPanel: Boolean,
    speechSubtitleStatus: SpeechSubtitleStatus,
    subtitleTranslationStatus: SubtitleTranslationStatus,
    speechSubtitleCoordinator: SpeechSubtitleCoordinator,
    subtitleTranslationCoordinator: SubtitleTranslationCoordinator,
    generatedSubtitleOrchestrator: GeneratedSubtitleOrchestrator,
    onControlsLockedChanged: (Boolean) -> Unit,
    onLockButtonVisibleWhileLockedChanged: (Boolean) -> Unit,
    onAutoSyncStatusChanged: (AutoSyncStatus) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (File) -> Unit,
    onStudioCategoryChanged: (StudioCategory?) -> Unit,
    onTrackSelectorManageModeChanged: (Boolean) -> Unit,
    onShowSubtitleBloomChanged: (Boolean) -> Unit,
    onShowSubtitleBehaviourWindowChanged: (Boolean) -> Unit,
    onShowDualSubsWindowChanged: (Boolean) -> Unit,
    onShowSpeechSubtitlePanelChanged: (Boolean) -> Unit,
    onShowSubtitleTranslationPanelChanged: (Boolean) -> Unit,
    onPendingDualAiLanguageChanged: (String?) -> Unit,
    onDualSecondaryColorHexChanged: (String) -> Unit,
) {
    PlayerImmediateOverlaySurfaces(
        controlsLocked = controlsLocked,
        lockButtonVisibleWhileLocked = lockButtonVisibleWhileLocked,
        showControls = showControls,
        externalDisplayActive = externalDisplayActive,
        isLandscape = isLandscape,
        haptics = haptics,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        autoSyncStatus = autoSyncStatus,
        autoSyncCoordinator = autoSyncCoordinator,
        pendingDeleteFile = pendingDeleteFile,
        snackbarHostState = snackbarHostState,
        snackbarBottomPadding = bottomDockPadding + playButton + 26.dp,
        onControlsLockedChanged = onControlsLockedChanged,
        onLockButtonVisibleWhileLockedChanged = onLockButtonVisibleWhileLockedChanged,
        onAutoSyncStatusChanged = onAutoSyncStatusChanged,
        onDismissDelete = onDismissDelete,
        onConfirmDelete = onConfirmDelete,
    )

    val embeddedTrackChoices = remember(player.currentTracks) {
        buildEmbeddedSubtitleChoices(player.currentTracks)
    }
    val downloadedTrackChoice = rememberDownloadedSubtitleChoice(
        context = context,
        videoPath = currentVideoPath,
        preferredLanguages = coreUi.behaviorPrefs.preferredLanguages,
        selectorVisible = trackUi.showSelector,
        canDownloadExternalSubtitles = canDownloadExternalSubtitles,
    )
    val localFileChoices = rememberAvailableLocalSubtitleFiles(
        videoPath = currentVideoPath,
        selectorVisible = trackUi.showSelector,
        pendingDeletePaths = pendingDeletePaths,
    )

    val quickHudFileName = remember(
        trackUi.selectedKey,
        embeddedTrackChoices,
        downloadedTrackChoice,
        localFileChoices,
        generatedSubtitleFiles,
    ) {
        val key = trackUi.selectedKey
        when {
            key == null || key == SubtitleTrackChoice.Off.key -> null
            downloadedTrackChoice?.key == key ->
                SubtitleLanguageRegistry.displayName(downloadedTrackChoice.language)
            else ->
                localFileChoices
                    .firstOrNull { SubtitleTrackChoice.Local(it).key == key }
                    ?.name
                    ?: generatedSubtitleFiles
                        .firstOrNull {
                            SubtitleTrackChoice.Generated(
                                it,
                                it.fileName.contains("-translated-"),
                            ).key == key
                        }
                        ?.label
                    ?: embeddedTrackChoices
                        .firstOrNull { it.key == key }
                        ?.let {
                            SubtitleLanguageRegistry.displayName(it.language)
                        }
        }
    }

    PlayerSubtitleStudioSurfaces(
        context = context,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        externalDisplayActive = externalDisplayActive,
        bottomDockPadding = bottomDockPadding,
        playButton = playButton,
        subtitleIconCenterX = subtitleIconCenterX,
        quickHudFileName = quickHudFileName,
        showSubtitleDock = showSubtitleDock,
        showSubtitleBloom = showSubtitleBloom,
        studioCategory = studioCategory,
        showSubtitleBehaviourWindow = showSubtitleBehaviourWindow,
        showDualSubsWindow = showDualSubsWindow,
        coreUi = coreUi,
        trackUi = trackUi,
        searchUi = searchUi,
        studioUi = studioUi,
        dualUi = dualUi,
        appearanceUi = appearanceUi,
        driftUi = driftUi,
        autoSyncSpeechTimeline = autoSyncSpeechTimeline,
        dualSecondaryColorHex = dualSecondaryColorHex,
        pendingDualAiLanguage = pendingDualAiLanguage,
        subtitleStudioNavigation = subtitleStudioNavigation,
        subtitleResetCoordinator = subtitleResetCoordinator,
        subtitleSyncTools = subtitleSyncTools,
        autoSyncCoordinator = autoSyncCoordinator,
        onStudioCategoryChanged = onStudioCategoryChanged,
        onTrackSelectorManageModeChanged = onTrackSelectorManageModeChanged,
        onShowSubtitleBloomChanged = onShowSubtitleBloomChanged,
        onShowSubtitleBehaviourWindowChanged = onShowSubtitleBehaviourWindowChanged,
        onShowDualSubsWindowChanged = onShowDualSubsWindowChanged,
        onShowSpeechSubtitlePanelChanged = onShowSpeechSubtitlePanelChanged,
        onShowSubtitleTranslationPanelChanged = onShowSubtitleTranslationPanelChanged,
        onPendingDualAiLanguageChanged = onPendingDualAiLanguageChanged,
        onDualSecondaryColorHexChanged = onDualSecondaryColorHexChanged,
    )

    PlayerSubtitleAiPanels(
        context = context,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        isInPipMode = CineVaultPlayerHolder.isInPipMode,
        externalDisplayActive = externalDisplayActive,
        speechJobLabel = speechJobLabel,
        speechJobProgress = speechJobProgress,
        translationJobLabel = translationJobLabel,
        translationJobProgress = translationJobProgress,
        showSpeechPanel = showSpeechSubtitlePanel,
        showTranslationPanel = showSubtitleTranslationPanel,
        speechStatus = speechSubtitleStatus,
        translationStatus = subtitleTranslationStatus,
        generatedFiles = generatedSubtitleFiles,
        activeSubtitleUri = trackUi.primaryUri ?: trackUi.originalUri,
        speechCoordinator = speechSubtitleCoordinator,
        translationCoordinator = subtitleTranslationCoordinator,
        generatedSubtitleOrchestrator = generatedSubtitleOrchestrator,
        onShowSpeechPanel = {
            onShowSubtitleTranslationPanelChanged(false)
            onShowSpeechSubtitlePanelChanged(true)
        },
        onHideSpeechPanel = {
            onShowSpeechSubtitlePanelChanged(false)
        },
        onShowTranslationPanel = {
            onShowSpeechSubtitlePanelChanged(false)
            onShowSubtitleTranslationPanelChanged(true)
        },
        onHideTranslationPanel = {
            onShowSubtitleTranslationPanelChanged(false)
        },
    )
}
