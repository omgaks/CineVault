package com.sole.cinevault

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.*
import java.io.File

/*
 * PlayerSubtitleOverlays.kt
 *
 * Third slice of the overlay-wiring extraction out of VideoPlayerScreen.kt
 * — the big one: the subtitle-sheet cluster (Settings Menu, Track Selector,
 * Search/Fallback/Embedded-Browser/Candidate import flow, Dialogue Sync,
 * Drift Correction, Appearance Studio, Subtitle Studio).
 *
 * Split into four functions instead of one, matching the real dependency
 * boundaries rather than forcing everything through one huge parameter
 * list:
 *   - SubtitleQuickMenuAndTrackSelector: Settings Menu + Track Selector
 *     (share the same icon-anchored position)
 *   - SubtitleAcquisitionFlow: Search -> Fallback -> Embedded Browser ->
 *     Candidate picker (the "getting a subtitle onto the device" chain)
 *   - SubtitleSyncAndAppearancePopups: Dialogue Sync bar, Drift Correction,
 *     Appearance Studio
 *
 * Deliberately NOT decomposing the original inline callbacks into new
 * named parameters — every onXxx lambda here is forwarded straight
 * through from VideoPlayerScreen.kt exactly as it was written (same
 * business logic, same state mutations, same function calls). Each
 * function's signature mirrors the underlying sheet's own parameters, so
 * this is a pure relocation of where the sheets get wired up, not a
 * rewrite of what they do.
 *
 * embeddedTrackChoices/downloadedTrackChoice/localFileChoices are computed
 * once in VideoPlayerScreen.kt and passed to the dedicated subtitle surfaces.
 */

@androidx.compose.runtime.Composable
fun BoxScope.SubtitleQuickMenuAndTrackSelector(
    showSubtitleSettings: Boolean,
    showTrackSelector: Boolean,
    subtitlesEnabled: Boolean,
    activeTrackStatusText: String,
    quickMenuBottomPadding: Dp,
    quickMenuOffsetX: Int,
    subtitleTextSizeSp: Float,
    subtitleBottomPadding: Float,
    onFindClick: () -> Unit,
    onTracksClick: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onDismissSettings: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onVerticalPositionChange: (Float) -> Unit,
    onResetSubtitleSettings: () -> Unit,
    onSyncClick: () -> Unit,
    onStyleClick: () -> Unit,
    onSettingsUserInteraction: () -> Unit,

    trackSelectorBottomPadding: Dp,
    trackSelectorOffsetX: Int,
    trackSelectorWidth: Dp,
    trackSelectorMaxHeight: Dp,
    studioRightInset: Dp,
    containerWidth: Dp,
    containerHeight: Dp,
    embeddedTrackChoices: List<SubtitleTrackChoice.Embedded>,
    downloadedTrackChoice: SubtitleTrackChoice.Downloaded?,
    localFileChoices: List<File>,
    generatedSubtitleFiles: List<GeneratedSubtitleFile> = emptyList(),
    selectedTrackKey: String?,
    onSelectTrack: (SubtitleTrackChoice) -> Unit,
    onDeleteLocalTrack: (File) -> Unit,
    onDeleteGeneratedTrack: (GeneratedSubtitleFile) -> Unit = {},
    onOpenFilePickerFromTrackSelector: () -> Unit,
    onBackFromTrackSelector: () -> Unit,
    onDismissTrackSelector: () -> Unit,
    onTrackSelectorUserInteraction: () -> Unit,
    initialManageMode: Boolean = false,
) {
    AnimatedVisibility(
        visible = showSubtitleSettings,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = quickMenuBottomPadding).offset { IntOffset(quickMenuOffsetX, 0) }
    ) {
        SubtitleSettingsMenu(
            isVisible = true,
            subtitlesEnabled = subtitlesEnabled,
            activeTrackStatusText = activeTrackStatusText,
            onFindClick = onFindClick,
            onTracksClick = onTracksClick,
            onToggleSubtitles = onToggleSubtitles,
            onDismiss = onDismissSettings,
            currentFontSize = subtitleTextSizeSp, onFontSizeChange = onFontSizeChange,
            currentVerticalPosition = subtitleBottomPadding, onVerticalPositionChange = onVerticalPositionChange,
            onReset = onResetSubtitleSettings,
            onSyncClick = onSyncClick,
            onStyleClick = onStyleClick,
            onUserInteraction = onSettingsUserInteraction
        )
    }

    AnimatedVisibility(
        visible = showTrackSelector,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = studioRightInset)
    ) {
        DraggableFloatingPopup(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            popupWidth = trackSelectorWidth,
            popupMaxHeight = trackSelectorMaxHeight,
            onUserInteraction = onTrackSelectorUserInteraction
        ) {
            SubtitleTrackSelectorSheet(
                embeddedTracks = embeddedTrackChoices,
                downloadedTrack = downloadedTrackChoice,
                localFiles = localFileChoices,
                generatedFiles = generatedSubtitleFiles,
                selectedKey = selectedTrackKey,
                popupWidth = trackSelectorWidth,
                popupMaxHeight = trackSelectorMaxHeight,
                onSelect = onSelectTrack,
                onDeleteLocal = onDeleteLocalTrack,
                onDeleteGenerated = onDeleteGeneratedTrack,
                onOpenFilePicker = onOpenFilePickerFromTrackSelector,
                onBack = onBackFromTrackSelector,
                onDismiss = onDismissTrackSelector,
                initialManageMode = initialManageMode
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun BoxScope.SubtitleAcquisitionFlow(
    showSubtitleSearch: Boolean,
    searchWidth: Dp,
    searchMaxHeight: Dp,
    containerWidth: Dp,
    containerHeight: Dp,
    studioRightInset: Dp,
    initialSearchQuery: String,
    searchResults: List<SubtitleSearchResult>,
    isSearching: Boolean,
    searchStatusText: String,
    onSearchUserInteraction: () -> Unit,
    onSearch: (String, String, String) -> Unit,
    onDownloadAndApply: (SubtitleSearchResult) -> Unit,
    onDownloadOnly: (SubtitleSearchResult) -> Unit,
    onWebsiteFallbackFromSearch: () -> Unit,
    onBackFromSearch: () -> Unit,
    onDismissSearch: () -> Unit,

    showSubtitleFallback: Boolean,
    fallbackSearchQuery: String,
    fallbackStatusText: String,
    onSecureBrowser: () -> Unit,
    onEmbeddedBrowser: () -> Unit,
    onImportFile: () -> Unit,
    onBackFromFallback: () -> Unit,
    onDismissFallback: () -> Unit,

    showEmbeddedSubtitleBrowser: Boolean,
    embeddedBrowserQuery: String,
    embeddedBrowserPreferredLanguage: String,
    onImported: (SubtitleImportResult.Success) -> Unit,
    onMessage: (String) -> Unit,
    onDismissEmbeddedBrowser: () -> Unit,

    pendingImportedCandidates: SubtitleImportResult.Success?,
    onCandidateSelected: (ImportedSubtitle) -> Unit,
    onDismissCandidateSheet: () -> Unit,
) {
    AnimatedVisibility(
        visible = showSubtitleSearch,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = studioRightInset)
    ) {
        SubtitleSearchSheet(
            initialQuery = initialSearchQuery,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            onUserInteraction = onSearchUserInteraction,
            initialSeason = "",
            initialEpisode = "",
            results = searchResults,
            isSearching = isSearching,
            statusText = searchStatusText,
            popupWidth = searchWidth,
            popupMaxHeight = searchMaxHeight,
            onSearch = onSearch,
            onDownloadAndApply = onDownloadAndApply,
            onDownloadOnly = onDownloadOnly,
            onWebsiteFallback = onWebsiteFallbackFromSearch,
            onBack = onBackFromSearch,
            onDismiss = onDismissSearch
        )
    }

    if (showSubtitleFallback) {
        SubtitleFallbackSheet(
            searchQuery = fallbackSearchQuery,
            statusText = fallbackStatusText,
            onSecureBrowser = onSecureBrowser,
            onEmbeddedBrowser = onEmbeddedBrowser,
            onImportFile = onImportFile,
            onBack = onBackFromFallback,
            rightInset = studioRightInset,
            onDismiss = onDismissFallback
        )
    }

    if (showEmbeddedSubtitleBrowser) {
        EmbeddedSubtitleBrowser(
            query = embeddedBrowserQuery,
            preferredLanguage = embeddedBrowserPreferredLanguage,
            onImported = onImported,
            onMessage = onMessage,
            onDismiss = onDismissEmbeddedBrowser
        )
    }

    pendingImportedCandidates?.let { result ->
        SubtitleCandidateSheet(
            primary = result.selected,
            alternatives = result.alternatives,
            onSelected = onCandidateSelected,
            onDismiss = onDismissCandidateSheet
        )
    }
}

@androidx.compose.runtime.Composable
fun BoxScope.SubtitleSyncAndAppearancePopups(
    dialogueSyncArmed: Boolean,
    isLandscape: Boolean,
    onDialogueSyncTap: () -> Unit,
    onDialogueSyncCancel: () -> Unit,

    showDriftDialog: Boolean,
    driftPopupWidth: Dp,
    videoDurationMs: Long,
    currentPositionMs: Long,
    driftPointA: DriftPoint?,
    driftPointB: DriftPoint?,
    onMarkPointA: (Float) -> Unit,
    onMarkPointB: (Float) -> Unit,
    onApplyDrift: () -> Unit,
    onDismissDrift: () -> Unit,

    showAppearanceStudio: Boolean,
    appearanceBottomPadding: Dp,
    appearanceOffsetX: Int,
    appearancePopupWidth: Dp,
    appearancePopupMaxHeight: Dp,
    containerWidth: Dp,
    containerHeight: Dp,
    studioRightInset: Dp,
    appearancePresetName: String,
    appearance: SubtitleAppearance,
    appearanceFontSizeSp: Float,
    onAppearanceFontSizeChange: (Float) -> Unit,
    appearanceBottomPaddingFraction: Float,
    onAppearanceBottomPaddingChange: (Float) -> Unit,
    onApplyPreset: (String, SubtitleAppearance) -> Unit,
    onForegroundChange: (Int) -> Unit,
    onEdgeTypeChange: (Int) -> Unit,
    onEdgeColorChange: (Int) -> Unit,
    onBackgroundChange: (Int) -> Unit,
    isAssOrSsaFormat: Boolean,
    preserveOriginalStyling: Boolean,
    onPreserveOriginalStylingChange: (Boolean) -> Unit,
    onBackFromAppearanceStudio: () -> Unit,
    onDismissAppearanceStudio: () -> Unit,
    onAppearanceUserInteraction: () -> Unit,
) {
    AnimatedVisibility(
        visible = dialogueSyncArmed,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isLandscape) 54.dp else 90.dp)
    ) {
        DialogueTapSyncBar(isLandscape = isLandscape, onTap = onDialogueSyncTap, onCancel = onDialogueSyncCancel)
    }

    AnimatedVisibility(
        visible = showDriftDialog,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.Center)
    ) {
        DriftCorrectionSheet(
            videoDurationMs = videoDurationMs,
            currentPositionMs = currentPositionMs,
            pointA = driftPointA,
            pointB = driftPointB,
            popupWidth = driftPopupWidth,
            popupMaxHeight = (containerHeight * 0.82f).coerceAtLeast(250.dp),
            onMarkPointA = onMarkPointA,
            onMarkPointB = onMarkPointB,
            onApply = onApplyDrift,
            onDismiss = onDismissDrift
        )
    }

    AnimatedVisibility(
        visible = showAppearanceStudio,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = studioRightInset)
    ) {
        DraggableFloatingPopup(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            popupWidth = appearancePopupWidth,
            popupMaxHeight = appearancePopupMaxHeight,
            onUserInteraction = onAppearanceUserInteraction
        ) {
            SubtitleAppearanceStudioSheet(
                presetName = appearancePresetName,
                appearance = appearance,
                fontSizeSp = appearanceFontSizeSp,
                popupWidth = appearancePopupWidth,
                popupMaxHeight = appearancePopupMaxHeight,
                onApplyPreset = onApplyPreset,
                onForegroundChange = onForegroundChange,
                onEdgeTypeChange = onEdgeTypeChange,
                onEdgeColorChange = onEdgeColorChange,
                onBackgroundChange = onBackgroundChange,
                isAssOrSsaFormat = isAssOrSsaFormat,
                preserveOriginalStyling = preserveOriginalStyling,
                onPreserveOriginalStylingChange = onPreserveOriginalStylingChange,
                onFontSizeChange = onAppearanceFontSizeChange,
                bottomPadding = appearanceBottomPaddingFraction,
                onBottomPaddingChange = onAppearanceBottomPaddingChange,
                onBack = onBackFromAppearanceStudio,
                onDismiss = onDismissAppearanceStudio
            )
        }
    }
}
