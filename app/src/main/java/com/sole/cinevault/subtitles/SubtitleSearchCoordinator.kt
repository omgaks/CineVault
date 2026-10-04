package com.sole.cinevault.subtitles

import com.sole.cinevault.CineVaultToast

import android.annotation.SuppressLint

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sole.cinevault.SubtitleAcquisitionUiState
import com.sole.cinevault.SubtitleCoreUiState
import com.sole.cinevault.SubtitleStudioUiState
import com.sole.cinevault.SubtitleTrackSelectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubtitleSearchCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val exoPlayer: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    private val coreUi: SubtitleCoreUiState,
    private val trackUi: SubtitleTrackSelectionState,
    private val searchUi: SubtitleAcquisitionUiState,
    private val studioUi: SubtitleStudioUiState,
    private val getCurrentVideoPath: () -> String,
    private val setShowControls: (Boolean) -> Unit,
    private val setPendingSrtUri: (Uri) -> Unit,
    private val playSubtitle: (subtitleUri: Uri?, resumePosition: Long, isOriginalSubtitle: Boolean) -> Unit,
    private val externalOverlay: com.sole.cinevault.ExternalSubtitleOverlay? = null,
    // Called when an embedded track replaces the external subtitle, so anything built on that
    // external file (Dual subtitles) can be switched off.
    private val onExternalSubtitleReplaced: () -> Unit = {},
) {
    private var searchGeneration = 0L

    /**
     * An embedded track is now the subtitle. The previous external file must stop counting as
     * "the primary subtitle": otherwise Dual subtitles would still merge the OLD file (and
     * reject a secondary language that merely matches it), and a decoder fallback could put the
     * old external subtitle back over the embedded choice.
     */
    private fun adoptEmbeddedAsPrimary(language: String?) {
        val hadExternal = trackUi.primaryUri != null || trackUi.originalUri != null
        trackUi.primaryUri = null
        trackUi.originalUri = null
        trackUi.renderBaseUri = null
        trackUi.primaryLanguage = SubtitleLanguageRegistry.normalize(language ?: "")
        if (hadExternal) onExternalSubtitleReplaced()
    }

    // What the Tracks list showed as active before the Subtitles pill was switched off,
    // so switching it back on restores the same external subtitle.
    private var stashedKey: String? = null
    private var stashedLabel: String = ""
    private var stashedSource: String = ""

    private fun isCurrentVideo(expectedPath: String): Boolean =
        getCurrentVideoPath() == expectedPath

    private fun prepareExplicitExternalSelection(language: String?) {
        val normalized = SubtitleLanguageRegistry.normalize(language ?: "")
        val builder = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setIgnoredTextSelectionFlags(0)
            .setSelectUndeterminedTextLanguage(true)
        if (!normalized.isNullOrBlank()) builder.setPreferredTextLanguage(normalized)
        trackSelector.parameters = builder.build()
    }

    fun applyImportedWebsiteSubtitle(imported: ImportedSubtitle) {
        scope.launch {
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            val cleanedUri = withContext(Dispatchers.IO) {
                if (supportsCustomTextPipeline(imported.format)) {
                    buildCleanedSubtitleFile(context, imported.uri, coreUi.cleaningOptions)
                } else null
            } ?: imported.uri
            coreUi.subtitlesEnabled = true
            prepareExplicitExternalSelection(imported.language)
            trackUi.primaryUri = cleanedUri
            trackUi.primaryLanguage = imported.language
            trackUi.selectedKey = "downloaded"
            trackUi.selectedLabel = SubtitleLanguageRegistry.displayName(imported.language ?: "en")
            trackUi.selectedSource = "Website import"
            playSubtitle(cleanedUri, resumeAt, true)
            searchUi.showFallback = false
            searchUi.showEmbeddedBrowser = false
            searchUi.pendingImportCandidates = null
            setShowControls(true)
            CineVaultToast.show(context, "Subtitle loaded")
        }
    }

    fun performSubtitleSearch(query: String, seasonText: String, episodeText: String, language: String = coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en") {
        val expectedPath = getCurrentVideoPath()
        val generation = ++searchGeneration
        searchUi.searchLoading = true
        searchUi.searchStatus = ""
        scope.launch {
            val openSubsDeferred = async {
                OpenSubtitlesClient.searchSubtitlesDetailed(
                    query = query, season = seasonText.toIntOrNull(),
                    episode = episodeText.toIntOrNull(), language = language,
                    preferForced = coreUi.behaviorPrefs.preferForced,
                    preferSdh = coreUi.behaviorPrefs.preferSdh
                )
            }
            val subDlDeferred = async {
                SubDlClient.search(query, seasonText.toIntOrNull(), episodeText.toIntOrNull(), language)
            }
            val openSubsResult = openSubsDeferred.await()
            val subDlResult = subDlDeferred.await()
            if (generation != searchGeneration || !isCurrentVideo(expectedPath)) return@launch
            searchUi.searchLoading = false
            val openSubsList = (openSubsResult as? SubtitleSearchListResult.Success)?.results.orEmpty()
            val subDlList = (subDlResult as? SubtitleSearchListResult.Success)?.results.orEmpty()
            val merged = subDlList + openSubsList
            when {
                merged.isNotEmpty() -> { searchUi.searchResults = merged; searchUi.searchStatus = "" }
                openSubsResult is SubtitleSearchListResult.HttpError && subDlResult is SubtitleSearchListResult.HttpError -> {
                    searchUi.searchResults = emptyList()
                    searchUi.searchStatus = "Subtitle providers unavailable: OpenSubtitles ${openSubsResult.detail}; SubDL ${subDlResult.detail}"
                }
                openSubsResult is SubtitleSearchListResult.HttpError -> { searchUi.searchResults = emptyList(); searchUi.searchStatus = "OpenSubtitles failed (${openSubsResult.detail}); SubDL found no results" }
                subDlResult is SubtitleSearchListResult.HttpError -> { searchUi.searchResults = emptyList(); searchUi.searchStatus = "SubDL failed (${subDlResult.detail}); OpenSubtitles found no results" }
                else -> { searchUi.searchResults = emptyList(); searchUi.searchStatus = "No subtitles found for this search" }
            }
        }
    }

    fun applySearchResult(result: SubtitleSearchResult, alsoPlay: Boolean) {
        val expectedPath = getCurrentVideoPath()
        scope.launch {
            val downloadResult = if (result.provider == "SubDL" && result.subDlDownloadPath != null) {
                SubDlClient.downloadSubtitle(context, expectedPath, result.subDlDownloadPath, result.language)
            } else {
                OpenSubtitlesClient.downloadSubtitleByFileId(context, expectedPath, result.fileId, result.language, result.provider)
            }
            if (!isCurrentVideo(expectedPath)) {
                CineVaultToast.show(context, "Subtitle download ignored: video changed")
                return@launch
            }
            when (downloadResult) {
                is SubtitleDownloadResult.Success -> {
                    if (alsoPlay) {
                        coreUi.subtitlesEnabled = true
                        prepareExplicitExternalSelection(result.language)
                        trackUi.selectedKey = "downloaded"
                        trackUi.selectedLabel = SubtitleLanguageRegistry.displayName(result.language)
                        trackUi.selectedSource = result.provider
                        if (coreUi.behaviorPrefs.rememberLastSelectedLanguage && result.language.isNotBlank()) {
                            coreUi.behaviorPrefs = promoteLanguageToFront(coreUi.behaviorPrefs, SubtitleLanguageRegistry.normalize(result.language) ?: result.language.lowercase())
                            saveSubtitleBehaviorPrefs(context, coreUi.behaviorPrefs)
                        }
                        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val cleanedApplyUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, downloadResult.uri, coreUi.cleaningOptions) } ?: downloadResult.uri
                        trackUi.primaryUri = cleanedApplyUri
                        trackUi.primaryLanguage = SubtitleLanguageRegistry.normalize(result.language)
                        playSubtitle(cleanedApplyUri, resumeAt, true)
                        searchUi.showSearch = false
                        setShowControls(true)
                        CineVaultToast.show(context, "Subtitle applied")
                    } else {
                        CineVaultToast.show(context, "Subtitle saved — apply it from Tracks")
                    }
                }
                else -> CineVaultToast.show(context, downloadResult.summary(), long = true)
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun selectSubtitleTrack(choice: SubtitleTrackChoice) {
        studioUi.menuTouchKey++
        when (choice) {
            is SubtitleTrackChoice.On -> {
                val overlay = externalOverlay
                if (overlay != null && overlay.hasContent) {
                    // An external subtitle is loaded: just show it again.
                    coreUi.subtitlesEnabled = true
                    overlay.setVisible(true)
                    stashedKey?.let { trackUi.selectedKey = it }
                    trackUi.selectedLabel = stashedLabel
                    trackUi.selectedSource = stashedSource
                    return
                }
                val textGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                val firstAvailable = textGroups.firstOrNull { it.length > 0 }

                coreUi.subtitlesEnabled = true
                val builder = trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)

                if (firstAvailable != null) {
                    builder.setOverrideForType(
                        TrackSelectionOverride(firstAvailable.mediaTrackGroup, listOf(0))
                    )
                    val format = firstAvailable.getTrackFormat(0)
                    externalOverlay?.clear()
                    adoptEmbeddedAsPrimary(format.language)
                    trackUi.selectedKey = "embedded:0:0"
                    trackUi.selectedLabel = SubtitleLanguageRegistry.displayName(format.language)
                    trackUi.selectedSource = "Embedded"
                } else {
                    // No embedded text track is currently exposed. Leave the
                    // renderer enabled so a downloaded/local/generated track
                    // can be selected immediately.
                    trackUi.selectedKey = choice.key
                    trackUi.selectedLabel = ""
                    trackUi.selectedSource = ""
                }

                trackSelector.parameters = builder.build()
            }
            is SubtitleTrackChoice.Off -> {
                if (externalOverlay?.hasContent == true) {
                    stashedKey = trackUi.selectedKey
                    stashedLabel = trackUi.selectedLabel
                    stashedSource = trackUi.selectedSource
                    externalOverlay?.setVisible(false)
                }
                coreUi.subtitlesEnabled = false
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                trackUi.selectedKey = choice.key
                trackUi.selectedLabel = ""
                trackUi.selectedSource = ""
            }
            is SubtitleTrackChoice.Embedded -> {
                val textGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                val trackCounts = textGroups.map { it.length }
                if (!isValidEmbeddedSubtitleSelection(choice.groupIndex, choice.trackIndexInGroup, trackCounts)) {
                    coreUi.subtitlesEnabled = false
                    trackUi.selectedKey = "off"
                    trackUi.selectedLabel = ""
                    trackUi.selectedSource = ""
                    CineVaultToast.show(context, "Subtitle track is no longer available")
                    return
                }

                // An embedded track replaces any external subtitle shown by the overlay.
                externalOverlay?.clear()
                adoptEmbeddedAsPrimary(choice.language)
                val group = textGroups[choice.groupIndex]
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(
                        TrackSelectionOverride(
                            group.mediaTrackGroup,
                            listOf(choice.trackIndexInGroup)
                        )
                    )
                    .build()

                coreUi.subtitlesEnabled = true
                trackUi.selectedKey = choice.key
                trackUi.selectedLabel = SubtitleLanguageRegistry.displayName(choice.language)
                trackUi.selectedSource = "Embedded"
                if (coreUi.behaviorPrefs.rememberLastSelectedLanguage &&
                    choice.language.isNotBlank() && choice.language != "und") {
                    coreUi.behaviorPrefs = promoteLanguageToFront(
                        coreUi.behaviorPrefs,
                        SubtitleLanguageRegistry.normalize(choice.language) ?: choice.language.lowercase()
                    )
                    saveSubtitleBehaviorPrefs(context, coreUi.behaviorPrefs)
                }
            }
            is SubtitleTrackChoice.Downloaded -> {
                coreUi.subtitlesEnabled = true
                prepareExplicitExternalSelection(choice.language)
                val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                scope.launch {
                    val cleaned = withContext(Dispatchers.IO) {
                        buildCleanedSubtitleFile(context, Uri.fromFile(choice.file), coreUi.cleaningOptions)
                    } ?: Uri.fromFile(choice.file)
                    trackUi.primaryUri = cleaned
                    trackUi.primaryLanguage = SubtitleLanguageRegistry.normalize(choice.language)
                    playSubtitle(cleaned, resumeAt, true)
                }
                trackUi.selectedKey = choice.key
                trackUi.selectedLabel = SubtitleLanguageRegistry.displayName(choice.language)
                trackUi.selectedSource = OpenSubtitlesClient.providerForCachedFile(choice.file)
            }
            is SubtitleTrackChoice.Local -> setPendingSrtUri(Uri.fromFile(choice.file))
            is SubtitleTrackChoice.Generated -> setPendingSrtUri(choice.file.uri)
        }
    }
}
