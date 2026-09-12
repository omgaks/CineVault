package com.sole.cinevault

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sole.cinevault.subtitles.*

/**
 * Slice 57: presentation host for subtitle selection and acquisition surfaces.
 *
 * This consolidates the local/audio popups, quick subtitle menu, track selector,
 * subtitle search/import surfaces, dialogue/drift sync popup and appearance
 * studio. State and behavior remain owned by the existing coordinators.
 */
@Composable
internal fun BoxScope.PlayerSubtitleSelectionAndAcquisitionSurfaces(
    context: Context,
    player: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    videoPath: String,
    canDownloadExternalSubtitles: Boolean,
    pendingDeletePaths: List<String>,
    generatedSubtitleFiles: List<GeneratedSubtitleFile>,
    showSrtBrowser: Boolean,
    showAudioSelector: Boolean,
    audioSyncMs: Int,
    popupBottomPadding: Dp,
    srtPopupWidth: Dp,
    srtPopupMaxHeight: Dp,
    audioPopupWidth: Dp,
    subtitlePopupWidth: Dp,
    trackStudioWidth: Dp,
    trackStudioMaxHeight: Dp,
    styleStudioWidth: Dp,
    styleStudioMaxHeight: Dp,
    studioFrameInset: Dp,
    visibleMovieWidth: Dp,
    containerWidth: Dp,
    containerHeight: Dp,
    isLandscape: Boolean,
    isCompactLandscape: Boolean,
    screenWidthPx: Float,
    density: Density,
    subtitleIconCenterX: Float,
    audioIconCenterX: Float,
    duration: Long,
    position: Long,
    isAssOrSsaFormat: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    searchUi: SubtitleAcquisitionUiState,
    driftUi: DriftCorrectionState,
    studioUi: SubtitleStudioUiState,
    appearanceUi: SubtitleAppearanceUiState,
    trackSelectorManageMode: Boolean,
    subtitleSearchCoordinator: SubtitleSearchCoordinator,
    subtitleDeletionCoordinator: SubtitleDeletionCoordinator,
    subtitleResetCoordinator: SubtitleResetCoordinator,
    subtitleSyncTools: SubtitleSyncToolsCoordinator,
    onPendingSrtUriChanged: (Uri?) -> Unit,
    onShowSrtBrowserChanged: (Boolean) -> Unit,
    onShowAudioSelectorChanged: (Boolean) -> Unit,
    onAudioSyncMsChanged: (Int) -> Unit,
    onShowControlsChanged: (Boolean) -> Unit,
    onShowTopBarChanged: (Boolean) -> Unit,
    onShowSubtitleBloomChanged: (Boolean) -> Unit,
    onStudioCategoryChanged: (StudioCategory?) -> Unit,
    onLaunchSrtPicker: (Array<String>) -> Unit,
) {
    val srtFiles = rememberAvailableLocalSubtitleFiles(
        videoPath = videoPath,
        selectorVisible = showSrtBrowser,
        pendingDeletePaths = pendingDeletePaths,
    )
    val audioTracksForPopup = buildAudioTrackRows(
        player = player,
        trackSelector = trackSelector,
        onTrackSelected = {
            onShowAudioSelectorChanged(false)
            onShowControlsChanged(true)
        },
    )

    SrtAndAudioTrackPopups(
        showSrtBrowser = showSrtBrowser,
        srtFiles = srtFiles,
        srtPopupWidth = srtPopupWidth,
        srtPopupMaxHeight = srtPopupMaxHeight,
        srtBottomPadding = playerPopupBottomPadding(popupBottomPadding),
        srtOffsetX = calculatePlayerPopupOffsetX(
            subtitleIconCenterX,
            srtPopupWidth,
            screenWidthPx,
            density,
        ),
        onPickSrt = { file ->
            onShowSrtBrowserChanged(false)
            onPendingSrtUriChanged(Uri.fromFile(file))
        },
        onDeleteSrt = { file ->
            subtitleDeletionCoordinator.requestDeleteSubtitle(file)
        },
        onSystemPicker = {
            onShowSrtBrowserChanged(false)
            onLaunchSrtPicker(
                arrayOf(
                    "application/x-subrip",
                    "text/plain",
                    "*/*",
                )
            )
        },
        onCloseSrtBrowser = {
            onShowSrtBrowserChanged(false)
            onShowControlsChanged(true)
        },
        showAudioSelector = showAudioSelector,
        audioTracks = audioTracksForPopup,
        audioPopupWidth = audioPopupWidth,
        audioBottomPadding = popupBottomPadding,
        audioOffsetX = calculatePlayerPopupOffsetX(
            audioIconCenterX,
            audioPopupWidth,
            screenWidthPx,
            density,
        ),
        audioSyncMs = audioSyncMs,
        onAudioSyncChange = {
            onAudioSyncMsChanged(it)
            studioUi.menuTouchKey++
        },
        onAudioMenuInteraction = { studioUi.menuTouchKey++ },
        onCloseAudioSelector = {
            onShowAudioSelectorChanged(false)
            onShowControlsChanged(true)
        },
    )

    val hasInternalSubtitles =
        hasInternalSubtitleTracks(player.currentTracks)

    val embeddedTrackChoices = remember(player.currentTracks) {
        buildEmbeddedSubtitleChoices(player.currentTracks)
    }
    val downloadedTrackChoice = rememberDownloadedSubtitleChoice(
        context = context,
        videoPath = videoPath,
        preferredLanguages = coreUi.behaviorPrefs.preferredLanguages,
        selectorVisible = trackUi.showSelector,
        canDownloadExternalSubtitles = canDownloadExternalSubtitles,
    )
    val localFileChoices = rememberAvailableLocalSubtitleFiles(
        videoPath = videoPath,
        selectorVisible = trackUi.showSelector,
        pendingDeletePaths = pendingDeletePaths,
    )

    val subtitleQuickMenuStatusText = buildSubtitleQuickMenuStatusText(
        subtitlesEnabled = coreUi.subtitlesEnabled,
        selectedLabel = trackUi.selectedLabel,
        selectedSource = trackUi.selectedSource,
        hasInternalSubtitles = hasInternalSubtitles,
    )

    SubtitleQuickMenuAndTrackSelector(
        showSubtitleSettings = coreUi.showSettings,
        showTrackSelector = trackUi.showSelector,
        subtitlesEnabled = coreUi.subtitlesEnabled,
        activeTrackStatusText = subtitleQuickMenuStatusText,
        quickMenuBottomPadding =
            playerPopupBottomPadding(popupBottomPadding),
        quickMenuOffsetX = calculatePlayerPopupOffsetX(
            subtitleIconCenterX,
            subtitlePopupWidth,
            screenWidthPx,
            density,
        ),
        subtitleTextSizeSp = appearanceUi.textSizeSp,
        subtitleBottomPadding = appearanceUi.bottomPadding,
        onFindClick = {
            studioUi.menuTouchKey++
            coreUi.showSettings = false
            searchUi.showSearch = true
            onShowControlsChanged(true)
            if (
                searchUi.searchResults.isEmpty() &&
                !searchUi.searchLoading
            ) {
                subtitleSearchCoordinator.performSubtitleSearch(
                    playerSubtitleSearchQuery(videoPath),
                    "",
                    "",
                    coreUi.behaviorPrefs.preferredLanguages
                        .firstOrNull() ?: "en",
                )
            }
        },
        onTracksClick = {
            coreUi.showSettings = false
            trackUi.showSelector = true
            onShowControlsChanged(true)
            studioUi.menuTouchKey++
        },
        onToggleSubtitles = {
            coreUi.subtitlesEnabled = !coreUi.subtitlesEnabled
            trackSelector.parameters =
                trackSelector
                    .buildUponParameters()
                    .setTrackTypeDisabled(
                        C.TRACK_TYPE_TEXT,
                        !coreUi.subtitlesEnabled,
                    )
                    .build()
            if (!coreUi.subtitlesEnabled) {
                trackUi.selectedKey = "off"
                trackUi.selectedLabel = ""
                trackUi.selectedSource = ""
            }
            onShowControlsChanged(true)
            studioUi.menuTouchKey++
        },
        onDismissSettings = {
            coreUi.showSettings = false
            onShowControlsChanged(true)
        },
        onFontSizeChange = {
            appearanceUi.textSizeSp = it
            onShowControlsChanged(true)
            studioUi.menuTouchKey++
        },
        onVerticalPositionChange = {
            appearanceUi.bottomPadding = it
            onShowControlsChanged(true)
            studioUi.menuTouchKey++
        },
        onSyncClick = {
            coreUi.showSettings = false
            onShowSubtitleBloomChanged(true)
            onStudioCategoryChanged(StudioCategory.POWER_TOOLS)
            onShowControlsChanged(false)
        },
        onStyleClick = {
            coreUi.showSettings = false
            coreUi.showAppearanceStudio = true
            onShowControlsChanged(false)
        },
        onResetSubtitleSettings = {
            subtitleResetCoordinator.reset()
        },
        onSettingsUserInteraction = {
            studioUi.menuTouchKey++
            onShowControlsChanged(true)
        },
        trackSelectorBottomPadding =
            playerPopupBottomPadding(popupBottomPadding),
        trackSelectorOffsetX = calculatePlayerPopupOffsetX(
            subtitleIconCenterX,
            trackStudioWidth,
            screenWidthPx,
            density,
        ),
        trackSelectorWidth = trackStudioWidth,
        trackSelectorMaxHeight = trackStudioMaxHeight,
        studioRightInset = studioFrameInset,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        embeddedTrackChoices = embeddedTrackChoices,
        downloadedTrackChoice = downloadedTrackChoice,
        localFileChoices = localFileChoices,
        generatedSubtitleFiles =
            generatedSubtitleFiles.filter { generated ->
                java.io.File(generated.uri.path ?: "").absolutePath !in
                    pendingDeletePaths
            },
        selectedTrackKey = trackUi.selectedKey,
        onSelectTrack = { choice ->
            subtitleSearchCoordinator.selectSubtitleTrack(choice)
            trackUi.showSelector = false
            onShowControlsChanged(true)
        },
        onDeleteLocalTrack = { file ->
            subtitleDeletionCoordinator.requestDeleteSubtitle(file)
        },
        onDeleteGeneratedTrack = { generated ->
            val path = generated.uri.path
            if (path != null) {
                subtitleDeletionCoordinator.requestDeleteSubtitle(
                    java.io.File(path)
                )
            }
        },
        onOpenFilePickerFromTrackSelector = {
            trackUi.showSelector = false
            onLaunchSrtPicker(
                arrayOf(
                    "application/x-subrip",
                    "text/plain",
                    "*/*",
                )
            )
        },
        onBackFromTrackSelector = {
            trackUi.showSelector = false
            onShowSubtitleBloomChanged(true)
            onStudioCategoryChanged(null)
            onShowControlsChanged(false)
            onShowTopBarChanged(false)
        },
        onDismissTrackSelector = {
            trackUi.showSelector = false
            onShowControlsChanged(true)
        },
        onTrackSelectorUserInteraction = {
            studioUi.menuTouchKey++
        },
        initialManageMode = trackSelectorManageMode,
    )

    val subtitleSearchLayout = calculateSubtitleSearchLayout(
        maxWidth = containerWidth,
        maxHeight = containerHeight,
        isLandscape = isLandscape,
        isCompactLandscape = isCompactLandscape,
    )
    val searchWidth =
        (subtitleSearchLayout.width * 1.12f)
            .coerceAtMost(
                (visibleMovieWidth - studioFrameInset * 2)
                    .coerceAtLeast(280.dp)
            )
    val searchMaxHeight =
        (subtitleSearchLayout.maxHeight * 1.10f)
            .coerceAtMost(
                (containerHeight - 24.dp).coerceAtLeast(280.dp)
            )
    val subtitleWebQuery = playerSubtitleSearchQuery(videoPath)

    SubtitleAcquisitionFlow(
        showSubtitleSearch = searchUi.showSearch,
        searchWidth = searchWidth,
        searchMaxHeight = searchMaxHeight,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        studioRightInset = studioFrameInset,
        initialSearchQuery = remember(videoPath) {
            playerSubtitleSearchQuery(videoPath)
        },
        searchResults = searchUi.searchResults,
        isSearching = searchUi.searchLoading,
        searchStatusText = searchUi.searchStatus,
        onSearchUserInteraction = {
            studioUi.menuTouchKey++
        },
        onSearch = { query, season, episode ->
            studioUi.menuTouchKey++
            subtitleSearchCoordinator.performSubtitleSearch(
                query,
                season,
                episode,
                coreUi.behaviorPrefs.preferredLanguages
                    .firstOrNull() ?: "en",
            )
        },
        onDownloadAndApply = { result ->
            studioUi.menuTouchKey++
            subtitleSearchCoordinator.applySearchResult(
                result,
                alsoPlay = true,
            )
        },
        onDownloadOnly = { result ->
            studioUi.menuTouchKey++
            subtitleSearchCoordinator.applySearchResult(
                result,
                alsoPlay = false,
            )
        },
        onWebsiteFallbackFromSearch = {
            searchUi.showSearch = false
            searchUi.showFallback = true
            onShowControlsChanged(false)
        },
        onBackFromSearch = {
            searchUi.showSearch = false
            onShowSubtitleBloomChanged(true)
            onStudioCategoryChanged(null)
            onShowControlsChanged(false)
            onShowTopBarChanged(false)
        },
        onDismissSearch = {
            searchUi.showSearch = false
            onShowControlsChanged(true)
        },
        showSubtitleFallback = searchUi.showFallback,
        fallbackSearchQuery = subtitleWebQuery,
        fallbackStatusText = searchUi.searchStatus,
        onSecureBrowser = {
            player.pause()
            launchSubtitleCustomTab(context, subtitleWebQuery)
            searchUi.showFallback = false
            Toast.makeText(
                context,
                "After downloading, return and choose Import downloaded subtitle",
                Toast.LENGTH_LONG,
            ).show()
        },
        onEmbeddedBrowser = {
            player.pause()
            searchUi.showFallback = false
            searchUi.showEmbeddedBrowser = true
        },
        onImportFile = {
            player.pause()
            onLaunchSrtPicker(
                arrayOf(
                    "application/x-subrip",
                    "text/vtt",
                    "text/plain",
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                )
            )
        },
        onBackFromFallback = {
            searchUi.showFallback = false
            onShowSubtitleBloomChanged(true)
            onStudioCategoryChanged(null)
            onShowControlsChanged(false)
            onShowTopBarChanged(false)
        },
        onDismissFallback = {
            searchUi.showFallback = false
        },
        showEmbeddedSubtitleBrowser =
            searchUi.showEmbeddedBrowser,
        embeddedBrowserQuery =
            playerSubtitleSearchQuery(videoPath),
        embeddedBrowserPreferredLanguage =
            coreUi.behaviorPrefs.preferredLanguages
                .firstOrNull() ?: "en",
        onImported = { result ->
            if (result.alternatives.isEmpty()) {
                subtitleSearchCoordinator
                    .applyImportedWebsiteSubtitle(result.selected)
            } else {
                searchUi.pendingImportCandidates = result
                searchUi.showEmbeddedBrowser = false
            }
        },
        onMessage = {
            Toast.makeText(
                context,
                it,
                Toast.LENGTH_LONG,
            ).show()
        },
        onDismissEmbeddedBrowser = {
            searchUi.showEmbeddedBrowser = false
            onShowControlsChanged(true)
        },
        pendingImportedCandidates =
            searchUi.pendingImportCandidates,
        onCandidateSelected = {
            subtitleSearchCoordinator.applyImportedWebsiteSubtitle(it)
        },
        onDismissCandidateSheet = {
            searchUi.pendingImportCandidates = null
        },
    )

    SubtitleSyncAndAppearancePopups(
        dialogueSyncArmed = coreUi.dialogueSyncArmed,
        isLandscape = isLandscape,
        onDialogueSyncTap = {
            subtitleSyncTools.confirmDialogueSyncTap()
        },
        onDialogueSyncCancel = {
            subtitleSyncTools.cancelDialogueSync()
        },
        showDriftDialog = driftUi.showDialog,
        driftPopupWidth =
            playerDriftPopupWidth(trackStudioWidth),
        videoDurationMs = duration,
        currentPositionMs = position,
        driftPointA = driftUi.pointA,
        driftPointB = driftUi.pointB,
        onMarkPointA = { correction ->
            subtitleSyncTools.markDriftPointA(correction)
        },
        onMarkPointB = { correction ->
            subtitleSyncTools.markDriftPointB(correction)
        },
        onApplyDrift = {
            subtitleSyncTools.applyDriftFix()
        },
        onDismissDrift = {
            driftUi.showDialog = false
            onShowControlsChanged(false)
            onShowTopBarChanged(false)
        },
        showAppearanceStudio = coreUi.showAppearanceStudio,
        appearanceBottomPadding =
            playerPopupBottomPadding(popupBottomPadding),
        appearanceOffsetX = calculatePlayerPopupOffsetX(
            subtitleIconCenterX,
            styleStudioWidth,
            screenWidthPx,
            density,
        ),
        appearancePopupWidth = styleStudioWidth,
        appearancePopupMaxHeight = styleStudioMaxHeight,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        studioRightInset = studioFrameInset,
        appearancePresetName = appearanceUi.preset,
        appearance = appearanceUi.appearance,
        appearanceFontSizeSp = appearanceUi.textSizeSp,
        onAppearanceFontSizeChange = {
            appearanceUi.textSizeSp = it
        },
        appearanceBottomPaddingFraction =
            appearanceUi.bottomPadding,
        onAppearanceBottomPaddingChange = {
            appearanceUi.bottomPadding = it
        },
        onApplyPreset = { name, preset ->
            appearanceUi.preset = name
            appearanceUi.appearance = preset
        },
        onForegroundChange = { color ->
            appearanceUi.preset = "Custom"
            appearanceUi.appearance =
                appearanceUi.appearance.copy(
                    foregroundColor = color
                )
        },
        onEdgeTypeChange = { type ->
            appearanceUi.preset = "Custom"
            appearanceUi.appearance =
                appearanceUi.appearance.copy(edgeType = type)
        },
        onEdgeColorChange = { color ->
            appearanceUi.preset = "Custom"
            appearanceUi.appearance =
                appearanceUi.appearance.copy(edgeColor = color)
        },
        onBackgroundChange = { color ->
            appearanceUi.preset = "Custom"
            appearanceUi.appearance =
                appearanceUi.appearance.copy(
                    backgroundColor = color
                )
        },
        isAssOrSsaFormat = isAssOrSsaFormat,
        preserveOriginalStyling =
            appearanceUi.preserveOriginalStyling,
        onPreserveOriginalStylingChange = {
            appearanceUi.preserveOriginalStyling = it
        },
        onBackFromAppearanceStudio = {
            coreUi.showAppearanceStudio = false
            onShowSubtitleBloomChanged(true)
            onStudioCategoryChanged(null)
            onShowControlsChanged(false)
            onShowTopBarChanged(false)
        },
        onDismissAppearanceStudio = {
            coreUi.showAppearanceStudio = false
            onShowControlsChanged(true)
        },
        onAppearanceUserInteraction = {
            studioUi.menuTouchKey++
        },
    )
}
