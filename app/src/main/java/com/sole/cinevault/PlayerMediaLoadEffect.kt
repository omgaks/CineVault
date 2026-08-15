package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sole.cinevault.library.VideoFile
import com.sole.cinevault.library.loadPlaybackPosition
import com.sole.cinevault.library.updateRestrictedFolderLastPlayed
import com.sole.cinevault.subtitles.OpenSubtitlesClient
import com.sole.cinevault.subtitles.SubtitleDownloadResult
import com.sole.cinevault.subtitles.SubtitleLanguageRegistry
import com.sole.cinevault.subtitles.buildCleanedSubtitleFile
import com.sole.cinevault.subtitles.findBestMatchingLocalSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * PlayerMediaLoadEffect.kt
 *
 * Owns the one-time work performed whenever the active video changes:
 * restoring its position, resetting the previous session, recording history,
 * selecting the best existing subtitle, and optionally downloading one when
 * no local/cached match exists. The rendering surface and external-display
 * path remain outside this file.
 */

@Composable
internal fun PlayerMediaLoadEffect(
    context: Context,
    scope: CoroutineScope,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    currentVideo: VideoFile,
    isStreamMedia: Boolean,
    isRestrictedFolderMedia: Boolean,
    canDownloadExternalSubtitles: Boolean,
    coreUi: SubtitleCoreUiState,
    trackUi: SubtitleTrackSelectionState,
    searchUi: SubtitleAcquisitionUiState,
    driftUi: DriftCorrectionState,
    dualUi: DualSubtitleState,
    appearanceUi: SubtitleAppearanceUiState,
    studioUi: SubtitleStudioUiState,
    autoSubtitleFetch: AutoSubtitleFetchState,
    onResetPlayerState: (savedPosition: Long) -> Unit,
    playCurrentVideoWithSubtitle: (subtitleUri: Uri?, resumePosition: Long, isOriginalSubtitle: Boolean) -> Unit,
) {
    LaunchedEffect(currentVideo.path) {
        val savedPosition = if (isStreamMedia) {
            0L
        } else {
            loadPlaybackPosition(context, currentVideo.path)
        }

        onResetPlayerState(savedPosition)

        coreUi.showSettings = false
        coreUi.syncOffset = 0.0f
        coreUi.dialogueSyncArmed = false
        coreUi.dialogueSyncReferenceMs = null

        trackUi.showSelector = false
        trackUi.originalUri = null
        trackUi.appliedOffsetMs = 0L
        trackUi.primaryUri = null
        trackUi.primaryLanguage = null
        trackUi.selectedKey = null
        trackUi.selectedLabel = ""
        trackUi.selectedSource = ""

        searchUi.showSearch = false
        searchUi.showFallback = false
        searchUi.showEmbeddedBrowser = false
        searchUi.pendingImportCandidates = null
        searchUi.searchResults = emptyList()
        searchUi.searchStatus = ""
        searchUi.searchLoading = false

        driftUi.scale = 1.0f
        driftUi.appliedScale = 1.0f
        driftUi.pointA = null
        driftUi.pointB = null
        driftUi.showDialog = false

        dualUi.enabled = false
        dualUi.statusText = ""
        appearanceUi.preserveOriginalStyling = false
        studioUi.gestureFeedback = ""

        if (!isStreamMedia) {
            recordWatchHistory(
                context,
                currentVideo.path,
                cleanVideoTitle(currentVideo.path),
            )
        }
        if (isRestrictedFolderMedia) {
            updateRestrictedFolderLastPlayed(
                context,
                currentVideo.path,
                currentVideo.folderPath,
            )
        }

        val localMatch = if (
            !isStreamMedia && coreUi.behaviorPrefs.autoLoadMatchingLocalFile
        ) {
            withContext(Dispatchers.IO) {
                findBestMatchingLocalSubtitle(
                    currentVideo.path,
                    coreUi.behaviorPrefs.preferredLanguages,
                )
            }
        } else {
            null
        }

        val cachedSubtitle = if (
            localMatch == null && !isStreamMedia && canDownloadExternalSubtitles
        ) {
            withContext(Dispatchers.IO) {
                OpenSubtitlesClient.findCachedSubtitle(
                    context,
                    currentVideo.path,
                    coreUi.behaviorPrefs.preferredLanguages,
                )
            }
        } else {
            null
        }

        when {
            localMatch != null -> {
                coreUi.subtitlesEnabled = true
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                val localUri = Uri.fromFile(localMatch.file)
                val cleanedLocalUri = withContext(Dispatchers.IO) {
                    buildCleanedSubtitleFile(context, localUri, coreUi.cleaningOptions)
                } ?: localUri
                trackUi.primaryUri = cleanedLocalUri
                trackUi.primaryLanguage = localMatch.languageCode
                playCurrentVideoWithSubtitle(cleanedLocalUri, savedPosition, true)
                autoSubtitleFetch.attemptedForPath = currentVideo.path
                trackUi.selectedKey = "local:${localMatch.file.absolutePath}"
                trackUi.selectedLabel = localMatch.file.name
                trackUi.selectedSource = "Local file"
            }

            cachedSubtitle != null -> {
                coreUi.subtitlesEnabled = true
                trackSelector.parameters = trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                val cleanedCachedUri = withContext(Dispatchers.IO) {
                    buildCleanedSubtitleFile(
                        context,
                        cachedSubtitle.uri,
                        coreUi.cleaningOptions,
                    )
                } ?: cachedSubtitle.uri
                trackUi.primaryUri = cleanedCachedUri
                trackUi.primaryLanguage = cachedSubtitle.language
                playCurrentVideoWithSubtitle(cleanedCachedUri, savedPosition, true)
                autoSubtitleFetch.attemptedForPath = currentVideo.path
                trackUi.selectedKey = "downloaded"
                trackUi.selectedLabel = friendlyLanguageName(cachedSubtitle.language)
                trackUi.selectedSource = "OpenSubtitles"
            }

            else -> playCurrentVideoWithSubtitle(null, savedPosition, true)
        }

        if (
            !isStreamMedia &&
            canDownloadExternalSubtitles &&
            !isRestrictedFolderMedia &&
            coreUi.behaviorPrefs.autoDownloadWhenMissing &&
            cachedSubtitle == null &&
            localMatch == null &&
            autoSubtitleFetch.attemptedForPath != currentVideo.path
        ) {
            autoSubtitleFetch.attemptedForPath = currentVideo.path
            scope.launch {
                delay(1200)
                if (autoSubtitleFetch.downloadInProgress) return@launch

                autoSubtitleFetch.downloadInProgress = true
                autoSubtitleFetch.status = "Searching subtitles..."
                try {
                    val result = OpenSubtitlesClient.downloadBestSubtitleDetailed(
                        context,
                        currentVideo.path,
                        coreUi.behaviorPrefs.preferredLanguages,
                    )
                    if (result is SubtitleDownloadResult.Success) {
                        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                        coreUi.subtitlesEnabled = true
                        trackSelector.parameters = trackSelector.buildUponParameters()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .build()
                        autoSubtitleFetch.status = "Subtitle loaded"
                        val cleanedResultUri = withContext(Dispatchers.IO) {
                            buildCleanedSubtitleFile(
                                context,
                                result.uri,
                                coreUi.cleaningOptions,
                            )
                        } ?: result.uri
                        trackUi.primaryUri = cleanedResultUri
                        trackUi.primaryLanguage = SubtitleLanguageRegistry.normalize(result.language)
                        playCurrentVideoWithSubtitle(cleanedResultUri, resumeAt, true)
                        trackUi.selectedKey = "downloaded"
                        trackUi.selectedLabel = friendlyLanguageName(result.language)
                        trackUi.selectedSource = "OpenSubtitles"
                        delay(1400)
                        autoSubtitleFetch.status = ""
                    } else {
                        autoSubtitleFetch.status = result.summary()
                        delay(3500)
                        autoSubtitleFetch.status = ""
                    }
                } catch (error: Exception) {
                    autoSubtitleFetch.status =
                        "Subtitle failed: ${error.message ?: error.javaClass.simpleName}"
                    delay(3500)
                    autoSubtitleFetch.status = ""
                } finally {
                    autoSubtitleFetch.downloadInProgress = false
                }
            }
        }
    }
}
