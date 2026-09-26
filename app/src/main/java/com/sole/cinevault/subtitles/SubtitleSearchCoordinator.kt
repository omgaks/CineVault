package com.sole.cinevault.subtitles

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
    private val playSubtitle: (subtitleUri: Uri?, resumePosition: Long, isOriginalSubtitle: Boolean) -> Unit
) {
    fun applyImportedWebsiteSubtitle(imported: ImportedSubtitle) {
        scope.launch {
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            val cleanedUri = withContext(Dispatchers.IO) {
                if (supportsCustomTextPipeline(imported.format)) {
                    buildCleanedSubtitleFile(context, imported.uri, coreUi.cleaningOptions)
                } else null
            } ?: imported.uri
            coreUi.subtitlesEnabled = true
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
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
            Toast.makeText(context, "Subtitle loaded", Toast.LENGTH_SHORT).show()
        }
    }

    fun performSubtitleSearch(query: String, seasonText: String, episodeText: String, language: String = coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en") {
        searchUi.searchLoading = true
        searchUi.searchStatus = ""
        scope.launch {
            val openSubsDeferred = scope.async {
                OpenSubtitlesClient.searchSubtitlesDetailed(
                    query = query, season = seasonText.toIntOrNull(),
                    episode = episodeText.toIntOrNull(), language = language,
                    preferForced = coreUi.behaviorPrefs.preferForced,
                    preferSdh = coreUi.behaviorPrefs.preferSdh
                )
            }
            val subDlDeferred = scope.async {
                SubDlClient.search(query, seasonText.toIntOrNull(), episodeText.toIntOrNull(), language)
            }
            val openSubsResult = openSubsDeferred.await()
            val subDlResult = subDlDeferred.await()
            searchUi.searchLoading = false
            val openSubsList = (openSubsResult as? SubtitleSearchListResult.Success)?.results.orEmpty()
            val subDlList = (subDlResult as? SubtitleSearchListResult.Success)?.results.orEmpty()
            val merged = subDlList + openSubsList
            when {
                merged.isNotEmpty() -> { searchUi.searchResults = merged; searchUi.searchStatus = "" }
                openSubsResult is SubtitleSearchListResult.HttpError -> { searchUi.searchResults = emptyList(); searchUi.searchStatus = "Search error: ${openSubsResult.detail}" }
                else -> { searchUi.searchResults = emptyList(); searchUi.searchStatus = "No subtitles found for this search" }
            }
        }
    }

    fun applySearchResult(result: SubtitleSearchResult, alsoPlay: Boolean) {
        scope.launch {
            val downloadResult = if (result.provider == "SubDL" && result.subDlDownloadPath != null) {
                SubDlClient.downloadSubtitle(context, getCurrentVideoPath(), result.subDlDownloadPath, result.language)
            } else {
                OpenSubtitlesClient.downloadSubtitleByFileId(context, getCurrentVideoPath(), result.fileId, result.language, result.provider)
            }
            when (downloadResult) {
                is SubtitleDownloadResult.Success -> {
                    if (alsoPlay) {
                        coreUi.subtitlesEnabled = true
                        trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                        trackUi.selectedKey = "downloaded"
                        trackUi.selectedLabel = SubtitleLanguageRegistry.displayName(result.language)
                        trackUi.selectedSource = result.provider
                        if (coreUi.behaviorPrefs.rememberLastSelectedLanguage && result.language.isNotBlank()) {
                            coreUi.behaviorPrefs = promoteLanguageToFront(coreUi.behaviorPrefs, result.language.take(2).lowercase())
                            saveSubtitleBehaviorPrefs(context, coreUi.behaviorPrefs)
                        }
                        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val cleanedApplyUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, downloadResult.uri, coreUi.cleaningOptions) } ?: downloadResult.uri
                        trackUi.primaryUri = cleanedApplyUri
                        trackUi.primaryLanguage = SubtitleLanguageRegistry.normalize(result.language)
                        playSubtitle(cleanedApplyUri, resumeAt, true)
                        searchUi.showSearch = false
                        setShowControls(true)
                        Toast.makeText(context, "Subtitle applied", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Subtitle saved — apply it from Tracks", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> Toast.makeText(context, downloadResult.summary(), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun selectSubtitleTrack(choice: SubtitleTrackChoice) {
        studioUi.menuTouchKey++
        when (choice) {
            is SubtitleTrackChoice.Off -> {
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
                    Toast.makeText(context, "Subtitle track is no longer available", Toast.LENGTH_SHORT).show()
                    return
                }

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
                        choice.language.take(2).lowercase()
                    )
                    saveSubtitleBehaviorPrefs(context, coreUi.behaviorPrefs)
                }
            }
            is SubtitleTrackChoice.Downloaded -> {
                coreUi.subtitlesEnabled = true
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
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
                trackUi.selectedSource = "OpenSubtitles"
            }
            is SubtitleTrackChoice.Local -> setPendingSrtUri(Uri.fromFile(choice.file))
            is SubtitleTrackChoice.Generated -> setPendingSrtUri(choice.file.uri)
        }
    }
}
